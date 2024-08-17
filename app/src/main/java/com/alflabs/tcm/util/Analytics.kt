/*
 * Project: Conductor, modified for TCM.
 * Copyright (C) 2018 alf.labs gmail com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.alflabs.tcm.util

import android.util.Log
import com.alflabs.tcm.app.AppPrefsValues
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An implementation using GA4 Measurement Protocol (POST HTTPS) _without_ Firebase.
 *
 * https://developers.google.com/analytics/devguides/collection/protocol/ga4
 * https://developers.google.com/analytics/devguides/collection/protocol/ga4/sending-events?client_type=firebase
 *
 * Access results at https://analytics.google.com/analytics/web
 *
 * GA1 had a clear hierarchy of event category > name > label > value.
 * GA4 puts an emphasis on event name, with event category & label being sub-parameters, whereas
 *   value can only be sent as a currency.
 * In GA4, event categories and labels will NOT appear unless marked as "custom dimensions" in
 * property settings > data display > custom dimensions:
 * - add dimension "Event Category" with Event Scope for user param "event_category."
 * - add dimension "Event Label" with Event Scope for user param "event_label."
 *
 */
class Analytics : ThreadLoop() {

    companion object {
        private val TAG: String = Analytics::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        // Verbose debug + "Debug Event" collection.
        // Debug events are visible in GA4 > Prop Setting > Data Display > Debug View only.
        private const val VERBOSE_DEBUG = false

        private const val IDLE_SLEEP_MS = 1000L / 10L
        private const val MAX_ERROR_NUM = 3

        private val GA4_URL = ("https://www.google-analytics.com/"
                + (if (VERBOSE_DEBUG) "debug/" else "")
                + "mp/collect")

        private val MEDIA_TYPE = MediaType.parse("text/plain")
    }

    private val mOkHttpClient = OkHttpClient()      // Uses android.permission.INTERNET in Manifest
    private val mPayloads = ConcurrentLinkedDeque<Payload>()
    private val mStopLoopOnceEmpty = AtomicBoolean(false)
    private val mLatchEndLoop = CountDownLatch(1)
    private val mExecutor = Executors.newSingleThreadScheduledExecutor()

    private var analyticsId = ""
    private var mGA4ClientId = ""
    private var mGA4AppSecret = ""


    fun setAnalyticsId(prefs: AppPrefsValues) {
        // GA4 uses the format "GA4ID|ClientID|AppSecret".
        var idOrFile = prefs.systemGA4ID()
        idOrFile = idOrFile.replace("[^A-Za-z0-9|-]".toRegex(), "")
        val fields = idOrFile.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        analyticsId = if (fields.size > 0) fields[0] else ""
        mGA4ClientId = if (fields.size > 1) fields[1] else ""
        mGA4AppSecret = if (fields.size > 2) fields[2] else ""

        if (DEBUG) Log.d(TAG, "Tracking ID: $analyticsId")
        if (DEBUG) Log.d(TAG, "GA4 Client : $mGA4ClientId")
    }

    @Throws(Exception::class)
    override fun start() {
        if (DEBUG) Log.d(TAG, "Start")
        super.start("Analytics")
    }

    /**
     * Requests termination. Pending tasks will be executed, no new task is allowed.
     * Waiting time is 10 seconds max.
     *
     * Side effect: The executor is now a dagger singleton.
     */
    @Throws(Exception::class)
    override fun stop() {
        if (DEBUG) Log.d(TAG, "Stop")
        mStopLoopOnceEmpty.set(true)
        mLatchEndLoop.await(10, TimeUnit.SECONDS)
        super.stop()
        mExecutor.shutdown()
        mExecutor.awaitTermination(10, TimeUnit.SECONDS)
        if (DEBUG) Log.d(TAG, "Stopped")
    }

    @Throws(EndLoopException::class)
    override fun runInThreadLoop() {
        val isStopping = mStopLoopOnceEmpty.get()
        val isNotStopping = !isStopping

        if (mPayloads.isEmpty()) {
            if (isStopping) {
                throw EndLoopException()
            }
        } else {
            var errors = 0

            while (!mQuit) {
                val payload: Payload? = mPayloads.pollFirst()
                if (payload == null) break
                if (!payload.send(System.currentTimeMillis())) {
                    if (isNotStopping) {
                        // If it fails, append the payload at the *end* of the queue to retry later
                        // after all newer events.
                        // Except if we fail when stopping, in that case we just drop the events.
                        mPayloads.offerLast(payload)
                        errors++

                        // Don't hammer the server in case of failures.
                        if (errors >= MAX_ERROR_NUM) {
                            break
                        }
                    }
                }
            }
        }

        if (!mQuit) {
            try {
                Thread.sleep(IDLE_SLEEP_MS)
            } catch (e: Exception) {
                if (DEBUG) Log.d(TAG, "Stats idle loop interrupted: $e")
            }
        }
    }

    override fun afterThreadLoop() {
        if (DEBUG) Log.d(TAG, "End Loop")
        mLatchEndLoop.countDown()
    }

    fun sendEvent(
        category: String,
        action: String,
        label: String = "",
        value: String = "",
    ) {
        val analyticsId = analyticsId
        if (analyticsId.isEmpty()) {
            if (DEBUG) Log.d(TAG, "Event Ignored -- No Tracking ID")
            return
        }

        try {
            start()
        } catch (e: Exception) {
            if (DEBUG) Log.d(TAG, "Event Ignored -- Failed to start Analytics thread: $e")
            return
        }

        try {
            // Events keys:
            // https://developers.google.com/analytics/devguides/collection/protocol/v1/devguide#event
            // GA4:
            // https://developers.google.com/analytics/devguides/collection/protocol/ga4

            // TBD revisit later with a proper GA4 implementation.
            // Nothing ever goes wrong generating JSON using a String.format, right?
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val timeWithSeconds = LocalDateTime.now().format(formatter)
            val timeWithMinutes = timeWithSeconds.substring(0, timeWithSeconds.length - 2)

            var payload: String = String.format(
                "{" +
                        "'client_id':'%s'" +  // GA4 client id
                        ",'events':[{'name':'%s'" +  // event action
                        ",'params':{'items':[]" +
                        ",'event_category':'%s'" +  // event category
                        ",'event_label':'%s'" +  // event label
                        ",'date_sec':'%s'" +  // date with seconds
                        ",'date_min':'%s'",  // date with minutes
                mGA4ClientId,
                action,
                category,
                label,
                timeWithSeconds,
                timeWithMinutes
            )
            try {
                val value_ = value.toInt()
                payload += String.format(Locale.US, ",'value':%d,'currency':'USD'", value_)
            } catch (_: Exception) {
                // no-op
            }
            payload += "}}]}"

            mPayloads.offerFirst(
                Payload(
                    System.currentTimeMillis(),
                    payload,
                    String.format("Event [c:%s a:%s l:%s v:%s]", category, action, label, value)
                )
            )
        } catch (e: Exception) {
            if (DEBUG) Log.d(TAG, "Event Encoding ERROR: $e")
        }
    }


    private inner class Payload(
        private val mCreatedWallTimeMS: Long,
        private val mPayload: String,
        private val mDebugLog: String
    ) {
        /** Must be executed in background thread.  */
        fun send(wallTimeMS: Long): Boolean {
            val deltaTS = wallTimeMS - mCreatedWallTimeMS

            // Queue Time:
            // https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#qt
            // GA4 has timestamp_micros at the outer level:
            // https://developers.google.com/analytics/devguides/collection/protocol/ga4/reference?client_type=firebase#payload
            val payload = mPayload.replaceFirst(
                "\\{".toRegex(),
                String.format(Locale.US, "{'timestamp_micros':%d,", mCreatedWallTimeMS * 1000 /* ms to μs */)
            )

            try {
                val response = sendPayloadGA4(payload)

                val code = response.code()
                if (DEBUG) Log.d(
                    TAG, String.format(
                        Locale.US,
                        "%s delta: %d ms, code: %d",
                        mDebugLog, deltaTS, code
                    )
                )

                if (VERBOSE_DEBUG) {
                    Log.d(TAG, "Event body: " + response.body()!!.string())
                }

                response.close()
                return code < 400
            } catch (e: Exception) {
                if (DEBUG) Log.d(TAG, "Send ERROR: $e")
            }

            return false
        }

        /** Must be executed in background thread. Caller must call Response.close().  */
        @Throws(IOException::class)
        private fun sendPayloadGA4(payload: String): Response {
            if (VERBOSE_DEBUG) {
                Log.d(TAG, "GA4 Event Payload: $payload")
            }

            val url = String.format(
                "%s?api_secret=%s&measurement_id=%s",
                GA4_URL, mGA4AppSecret, analyticsId
            )

            val builder = Request.Builder().url(url)

            // GA4 always uses POST
            val body = RequestBody.create(MEDIA_TYPE, payload)
            builder.post(body)
            val request = builder.build()
            return mOkHttpClient.newCall(request).execute()
        }
    }

}
