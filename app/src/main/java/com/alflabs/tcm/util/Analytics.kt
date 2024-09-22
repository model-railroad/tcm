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
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
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

        private val MEDIA_TYPE = MediaType.parse("text/plain")
    }

    private val mOkHttpClient = OkHttpClient()      // Uses android.permission.INTERNET in Manifest
    private val mPayloads = LinkedBlockingDeque<Payload>()
    private val mExecutor = Executors.newSingleThreadScheduledExecutor()
    private var mErrorSleepMs = IDLE_SLEEP_MS

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

    /**
     * Logs a GA4 custom event.
     *
     * GA4's main event attributes are event name and it's optional value.
     * These have contraints:
     * - The name must NOT have spaces. Keep it [AZa-z-] for best effect.
     * - The value is provided as a USD value and thus can be an integer.
     *
     * GA4 does not natively support "event_category" nor "event_label". These are provided as
     * custom fields for GA2 backward compatibility. To be used in GA4, they need to be added to
     * the custom event lists on the stream definition.
     */
    @Synchronized
    fun sendEvent(
        category: String,
        name: String,
        label: String = "",
        value: Int? = null,
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

            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val timeWithSeconds = LocalDateTime.now().format(formatter)
            val timeWithMinutes = timeWithSeconds.substring(0, timeWithSeconds.length - 2)

            val url = String.format(
                "%s?api_secret=%s&measurement_id=%s",
                GA4_MP_URL, mGA4AppSecret, analyticsId
            )

            val mapper = ObjectMapper()
            val root = mapper.createObjectNode().apply {
                put("client_id", mGA4ClientId)
                putArray("events").apply {
                    addObject().apply {
                        put("name", name)                   // aka event_action in GA2
                        putObject("params").apply {
                            if (category.isNotEmpty()) {
                                put("event_category", category)
                            }
                            if (label.isNotEmpty()) {
                                put("event_label", label)
                            }
                            value?.let {
                                put("value", it)
                                put("currency", "USD")
                            }
                            put("date_sec", timeWithSeconds)
                            put("date_min", timeWithMinutes)
                        }
                    }
                }
            }
            val payload = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)

            mPayloads.offerFirst(
                Payload(
                    System.currentTimeMillis(),
                    url,
                    payload,
                    String.format("Event [c:%s a:%s l:%s v:%s]", category, name, label, value)
                )
            )
        } catch (e: Exception) {
            if (DEBUG) Log.d(TAG, "Event Encoding ERROR: $e")
        }
    }


    /**
     * Logs a screen transition using Acitivty info.
     *
     * See GA4 screen_view:
     * https://developers.google.com/analytics/devguides/collection/protocol/ga4/reference/events#screen_view
     */
    @Synchronized
    fun sendActivityView(
        activityClassName: String,
        screenName: String
    ) {
        val analyticsId = analyticsId
        if (analyticsId.isEmpty()) {
            if (DEBUG) Log.d(TAG, "Event Ignored -- No Tracking ID")
            return
        }

        try {
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val timeWithSeconds = LocalDateTime.now().format(formatter)
            val timeWithMinutes = timeWithSeconds.substring(0, timeWithSeconds.length - 2)

            val url = String.format(
                "%s?api_secret=%s&measurement_id=%s",
                GA4_MP_URL, mGA4AppSecret, analyticsId
            )

            val mapper = ObjectMapper()
            val root = mapper.createObjectNode().apply {
                put("client_id", mGA4ClientId)
                putArray("events").apply {
                    addObject().apply {
                        put("name", "screen_view")
                        putObject("params").apply {
                            put("screen_class", activityClassName)
                            put("screen_name", screenName)
                            put("date_sec", timeWithSeconds)
                            put("date_min", timeWithMinutes)
                        }
                    }
                }
            }
            val payload = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)

            mPayloads.offerFirst(
                Payload(
                    System.currentTimeMillis(),
                    url,
                    payload,
                    String.format("Event [screen_view %s %s]", activityClassName, screenName)
                )
            )
        } catch (e: Exception) {
            if (DEBUG) Log.d(TAG, "ActivityView Encoding ERROR: $e")
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
