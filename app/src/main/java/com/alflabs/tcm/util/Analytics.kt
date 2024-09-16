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

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.alflabs.tcm.app.AppPrefsValues
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class Analytics @Inject constructor() : ThreadLoop() {

    companion object {
        private val TAG: String = Analytics::class.java.simpleName
        private val DEBUG: Boolean = GlobalDebug.DEBUG

        // Verbose debug + "Debug Event" collection.
        // Debug events are visible in GA4 > Prop Setting > Data Display > Debug View only.
        private const val VERBOSE_DEBUG = false

        private const val IDLE_SLEEP_MS     = 1000L          // 1 second regular pool time
        private const val IDLE_SLEEP_MAX_MS = 1000L * 60 * 5 // 5 minutes max error retry
        private const val MAX_ERROR_NUM = 2

        // The GA4 Measurement Protocol is clearly documented and works properly:
        // https://developers.google.com/analytics/devguides/collection/protocol/ga4
        private val GA4_MP_URL = ("https://www.google-analytics.com/"
                + (if (VERBOSE_DEBUG) "debug/" else "")
                + "mp/collect")

        // This is an _attempt_ at using the GA4 gtag.js page view mechanism.
        // This does not seem to properly work yet: page view events are not showing in GA.
        private val GA4_V2_URL = ("https://www.google-analytics.com/"
                + (if (VERBOSE_DEBUG) "debug/" else "") // Note: there is no DEBUG, this will 404
                + "g/collect")

        private val MEDIA_TYPE = MediaType.parse("text/plain")
    }

    private val mOkHttpClient = OkHttpClient()      // Uses android.permission.INTERNET in Manifest
    private val mPayloads = LinkedBlockingDeque<Payload>()
    private val mExecutor = Executors.newSingleThreadScheduledExecutor()
    private var mErrorSleepMs = IDLE_SLEEP_MS

    private var v2ClientId = ""    // filled when first page_view is sent
    private var v2SessionId = ""    // filled when first page_view is sent
    private var v2SessionCount = 0
    private var v2SequenceNum = 0

    private var analyticsId = ""
    private var mGA4ClientId = ""
    private var mGA4AppSecret = ""


    /**
     * Sets or reset the Analytics ID.
     */
    @Synchronized
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

        // This is currently called each time the activity starts, which is a good
        // indicator that a "new session" is starting
        v2SessionId = ""
        v2SessionCount++
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
    override fun stopSync() {
        if (DEBUG) Log.d(TAG, "Stop")
        stopSync(10 * 1000)
    }

    override fun stopSync(joinTimeoutMillis: Long) {
        super.stopSync(joinTimeoutMillis)
        mExecutor.shutdown()
        mExecutor.awaitTermination(joinTimeoutMillis, TimeUnit.MILLISECONDS)
        if (DEBUG) Log.d(TAG, "Stopped")
    }

    @Throws(EndLoopException::class)
    override fun runInThreadLoop() {
        var errors = 0

        while (!mQuit) {
            try {
                val payload = mPayloads.takeFirst() // blocking or interrupted
                if (payload.send(System.currentTimeMillis())) {
                    mErrorSleepMs = IDLE_SLEEP_MS
                    errors = 0
                } else  if (!mQuit) {
                    // If it fails, append the payload at the *end* of the queue to retry later
                    // after all newer events.
                    // Except if we fail when stopping, in that case we just drop the events.
                    mPayloads.offerLast(payload)
                    errors++

                    // Don't hammer the server in case of continuous failures.
                    if (errors >= MAX_ERROR_NUM) {
                        break
                    }
                }
            } catch (_: InterruptedException) { }
        }


        if (!mQuit) {
            try {
                Thread.sleep(mErrorSleepMs)
                if (errors > 0 && mErrorSleepMs < IDLE_SLEEP_MAX_MS) {
                    mErrorSleepMs += mErrorSleepMs
                    if (DEBUG) Log.d(TAG, "Error timeout changed to $mErrorSleepMs ms")
                }
            } catch (e: Exception) {
                if (DEBUG) Log.d(TAG, "Stats idle loop interrupted: $e")
            }
        }
    }

    override fun afterThreadLoop() {
        if (DEBUG) Log.d(TAG, "End Loop")
    }

    @Synchronized
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
            // Events keys:
            // https://developers.google.com/analytics/devguides/collection/protocol/v1/devguide#event
            // GA4:
            // https://developers.google.com/analytics/devguides/collection/protocol/ga4

            // TBD revisit later with a proper GA4 implementation.
            // Nothing ever goes wrong generating JSON using a String.format, right?
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val timeWithSeconds = LocalDateTime.now().format(formatter)
            val timeWithMinutes = timeWithSeconds.substring(0, timeWithSeconds.length - 2)

            val url = String.format(
                "%s?api_secret=%s&measurement_id=%s",
                GA4_MP_URL, mGA4AppSecret, analyticsId
            )

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
                    url,
                    payload,
                    String.format("Event [c:%s a:%s l:%s v:%s]", category, action, label, value)
                )
            )
        } catch (e: Exception) {
            if (DEBUG) Log.d(TAG, "Event Encoding ERROR: $e")
        }
    }


    @Synchronized
    fun sendPageView(
        pageTitle: String,
        pageLocation: String
    ) {
        val analyticsId = analyticsId
        if (analyticsId.isEmpty()) {
            if (DEBUG) Log.d(TAG, "Event Ignored -- No Tracking ID")
            return
        }

        try {
            val _et = SystemClock.currentThreadTimeMillis().toString()

            val _p = System.currentTimeMillis().toString()

            if (v2SessionId.isEmpty()) {
                v2SessionId = _p
            }
            if (v2ClientId.isEmpty()) {
                v2ClientId = "$mGA4ClientId-$mGA4AppSecret".hashCode().toString()
                v2ClientId = "$v2ClientId.$v2ClientId"
            }

            v2SequenceNum++

            val url = GA4_V2_URL +
                    "?v=2" +
                    "&tid=$analyticsId" +
                    "&en=page_view" +
                    "&_p=$_p" +
                    "&cid=$v2ClientId" +
                    "&_s=$v2SequenceNum" +
                    "&sid=$v2SessionId" +
                    "&sct=$v2SessionCount" +
                    "&dt=${Uri.encode(pageTitle)}" +
                    "&dl=${Uri.encode(pageLocation)}" +
                    "&_et=${_et}"

            val payload = ""

            mPayloads.offerFirst(
                Payload(
                    System.currentTimeMillis(),
                    url,
                    payload,
                    "PageView [$pageTitle]"
                )
            )
        } catch (e: Exception) {
            if (DEBUG) Log.d(TAG, "PageView Encoding ERROR: $e")
        }
    }
    private inner class Payload(
        private val createdWallTimeMS: Long,
        private val url: String,
        private val jsonPayload: String,
        private val debugLog: String
    ) {
        /** Must be executed in background thread.  */
        fun send(wallTimeMS: Long): Boolean {
            val deltaTS = wallTimeMS - createdWallTimeMS

            // Queue Time:
            // https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#qt
            // GA4 has timestamp_micros at the outer level:
            // https://developers.google.com/analytics/devguides/collection/protocol/ga4/reference?client_type=firebase#payload
            val payload = jsonPayload.replaceFirst(
                "\\{".toRegex(),
                String.format(Locale.US, "{'timestamp_micros':%d,", createdWallTimeMS * 1000 /* ms to μs */)
            )

            try {
                val response = sendPayloadGA4(url, payload)

                val code = response.code()
                if (DEBUG) Log.d(
                    TAG, String.format(
                        Locale.US,
                        "%s delta: %d ms, code: %d",
                        debugLog, deltaTS, code
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
        private fun sendPayloadGA4(url: String, payload: String): Response {
            if (VERBOSE_DEBUG) {
                Log.d(TAG, "GA4 URL: $url")
                Log.d(TAG, "GA4 Payload: $payload")
            }

            val builder = Request.Builder().url(url)

            // GA4 always uses POST
            val body = RequestBody.create(MEDIA_TYPE, payload)
            builder.post(body)
            val request = builder.build()
            return mOkHttpClient.newCall(request).execute()
        }
    }

}
