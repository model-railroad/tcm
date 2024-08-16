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

package com.alflabs.tcm.util;

import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.alflabs.tcm.app.AppPrefsValues;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Analytics extends ThreadLoop {
    private static final String TAG = Analytics.class.getSimpleName();

    private static final boolean VERBOSE_DEBUG = false;
    private static final long IDLE_SLEEP_MS = 1000 / 10;
    private static final int MAX_ERROR_NUM = 3;

    private static final String GA4_URL =
            "https://www.google-analytics.com/"
                    + (VERBOSE_DEBUG ? "debug/" : "")
                    + "mp/collect";

    private static final String DATA_SOURCE = "consist";
    private static final String UTF_8 = "UTF-8";
    private static final MediaType MEDIA_TYPE = MediaType.parse("text/plain");

    private final ILogger mLogger;
    private final IClock mClock;
    private final OkHttpClient mOkHttpClient;
    private final ConcurrentLinkedDeque<Payload> mPayloads = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean mStopLoopOnceEmpty = new AtomicBoolean(false);
    private final CountDownLatch mLatchEndLoop = new CountDownLatch(1);
    private final ILocalDateTimeNowProvider mLocalDateTimeNow;
    // Note: The executor is a dagger singleton, shared with the JsonSender.
    private final ScheduledExecutorService mExecutor;

    private String mAnalyticsId = "";
    private String mGA4ClientId = "";
    private String mGA4AppSecret = "";

    public Analytics(ILogger logger) {
        mLogger = logger;
        mClock = SystemClock::elapsedRealtime;
        mOkHttpClient = new OkHttpClient();
        mLocalDateTimeNow = LocalDateTime::now;
        mExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Requests termination. Pending tasks will be executed, no new task is allowed.
     * Waiting time is 10 seconds max.
     * <p/>
     * Side effect: The executor is now a dagger singleton.
     */
    public void shutdown() throws Exception {
        stop();
    }

    public void setAnalyticsId(@NonNull AppPrefsValues prefs) throws IOException {
        // GA ID format is "UA-Numbers-1" so accept only letters, numbers, hyphen. Ignore the rest.
        // For GA4, we use the format "GA4ID|ClientID|AppSecret".

        // GA4
        String idOrFile = prefs.systemGA4ID();
        idOrFile = idOrFile.replaceAll("[^A-Za-z0-9|-]", "");
        String[] fields = idOrFile.split("\\|");
        mAnalyticsId = fields.length > 0 ? fields[0] : "";
        mGA4ClientId = fields.length > 1 ? fields[1] : "";
        mGA4AppSecret = fields.length > 2 ? fields[2] : "";

        mLogger.log(TAG, "Tracking ID: " + mAnalyticsId);
        mLogger.log(TAG, "GA4 Client : " + mGA4ClientId);
    }

    @NonNull
    public String getAnalyticsId() {
        return mAnalyticsId;
    }

    @Override
    public void start() throws Exception {
        super.start("Analytics");
    }

    /**
     * Requests termination. Pending tasks will be executed, no new task is allowed.
     * Waiting time is 10 seconds max.
     * <p/>
     * Side effect: The executor is now a dagger singleton.
     */
    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        mStopLoopOnceEmpty.set(true);
        mLatchEndLoop.await(10, TimeUnit.SECONDS);
        super.stop();
        mExecutor.shutdown();
        mExecutor.awaitTermination(10, TimeUnit.SECONDS);
        mLogger.log(TAG, "Stopped");
    }

    @Override
    protected void runInThreadLoop() throws EndLoopException {
        final boolean isStopping = mStopLoopOnceEmpty.get();
        final boolean isNotStopping = !isStopping;

        if (mPayloads.isEmpty()) {
            if (isStopping) {
                throw new EndLoopException();
            }
        } else {
            int errors = 0;
            Payload payload;
            while ((payload = mPayloads.pollFirst()) != null) {
                if (!payload.send(mClock.elapsedRealtime())) {
                    if (isNotStopping) {
                        // If it fails, append the payload at the *end* of the queue to retry later
                        // after all newer events.
                        // Except if we fail when stopping, in that case we just drop the events.
                        mPayloads.offerLast(payload);
                        errors++;

                        // Don't hammer the server in case of failures.
                        if (errors >= MAX_ERROR_NUM) {
                            break;
                        }
                    }
                }
            }
        }

        try {
            Thread.sleep(IDLE_SLEEP_MS);
        } catch (Exception e) {
            mLogger.log(TAG, "Stats idle loop interrupted: " + e);
        }
    }

    @Override
    protected void afterThreadLoop() {
        mLogger.log(TAG, "End Loop");
        mLatchEndLoop.countDown();
    }

    public void sendEvent(
            @NonNull String category,
            @NonNull String action,
            @NonNull String label,
            @NonNull String value) {
        final String analyticsId = mAnalyticsId;
        if (analyticsId == null || analyticsId.isEmpty()) {
            mLogger.log(TAG, "Event Ignored -- No Tracking ID");
            return;
        }

        try {
            start();
        } catch (Exception e) {
            mLogger.log(TAG, "Event Ignored -- Failed to start Analytics thread: " + e);
            return;
        }

        try {
            // Events keys:
            // https://developers.google.com/analytics/devguides/collection/protocol/v1/devguide#event
            // GA4:
            // https://developers.google.com/analytics/devguides/collection/protocol/ga4

            String payload;

            // TBD revisit later with a proper GA4 implementation.
            // Nothing ever goes wrong generating JSON using a String.format, right?
            DateTimeFormatter formatter = null;
            String timeWithSeconds = null;
            formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            timeWithSeconds = mLocalDateTimeNow.getNow().format(formatter);
            String timeWithMinutes = timeWithSeconds.substring(0, timeWithSeconds.length() - 2);

            payload = String.format("{" +
                            "'client_id':'%s'" +                // GA4 client id
                            ",'events':[{'name':'%s'" +         // event action
                                ",'params':{'items':[]" +
                                    ",'event_category':'%s'" +          // event category
                                    ",'event_label':'%s'" +             // event label
                                    ",'date_sec':'%s'" +                // date with seconds
                                    ",'date_min':'%s'",                 // date with minutes
                    mGA4ClientId,
                    action,
                    category,
                    label,
                    timeWithSeconds,
                    timeWithMinutes
                    );
            try {
                int value_ = Integer.parseInt(value);
                payload += String.format(Locale.US, ",'value':%d,'currency':'USD'", value_);
            } catch (Exception _ignore) {
                // no-op
            }
            payload += "}}]}";

            mPayloads.offerFirst(new Payload(
                    mClock.elapsedRealtime(),
                    payload,
                    String.format("Event [c:%s a:%s l:%s v:%s]", category, action, label, value)
            ));

        } catch (Exception e) {
            mLogger.log(TAG, "Event Encoding ERROR: " + e);
        }
    }


    private class Payload {
        private final long mCreatedTS;
        private final String mPayload;
        private final String mDebugLog;

        public Payload(long createdTS, String payload, String debugLog) {
            mCreatedTS = createdTS;
            mPayload = payload;
            mDebugLog = debugLog;
        }

        /** Must be executed in background thread. */
        public boolean send(long nowTS) {
            long deltaTS = nowTS - mCreatedTS;

            // Queue Time:
            // https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#qt
            // GA4 has timestamp_micros at the outer level:
            // https://developers.google.com/analytics/devguides/collection/protocol/ga4/reference?client_type=firebase#payload
            String payload;
            payload = mPayload.replaceFirst("\\{",
                    String.format("{'timestamp_micros':%d,", mCreatedTS * 1000 /* ms to μs */)
                    );

            try {
                Response response = sendPayloadGA4(payload);

                int code = response.code();
                mLogger.log(TAG, String.format("%s delta: %d ms, code: %d",
                        mDebugLog, deltaTS, code));

                if (VERBOSE_DEBUG) {
                    mLogger.log(TAG, "Event body: " + response.body().string());
                }

                response.close();
                return code < 400;

            } catch (Exception e) {
                mLogger.log(TAG, "Send ERROR: " + e);
            }

            return false;
        }

        /** Must be executed in background thread. Caller must call Response.close(). */
        private Response sendPayloadGA4(String payload) throws IOException {
            if (VERBOSE_DEBUG) {
                mLogger.log(TAG, "GA4 Event Payload: " + payload);
            }

            String url = String.format("%s?api_secret=%s&measurement_id=%s",
                    GA4_URL, mGA4AppSecret, mAnalyticsId);

            Request.Builder builder = new Request.Builder().url(url);

            // GA4 always uses POST
            RequestBody body = RequestBody.create(MEDIA_TYPE, payload);
            builder.post(body);
            Request request = builder.build();
            return mOkHttpClient.newCall(request).execute();
        }
    }
}
