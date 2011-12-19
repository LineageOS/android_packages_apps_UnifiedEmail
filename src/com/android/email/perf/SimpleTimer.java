// Copyright 2011 Google Inc. All Rights Reserved.

package com.android.email.perf;

import android.os.SystemClock;

import com.android.email.utils.LogUtils;
import com.android.email.utils.Utils;

/**
 * A simple perf timer class that supports lap-time-style measurements. Once a timer is started,
 * any number of laps can be marked, but they are all relative to the original start time.
 *
 */
public class SimpleTimer {

    private static final boolean ENABLE_SIMPLE_TIMER = true;
    private static final String LOG_TAG = new LogUtils().getLogTag();

    private final boolean mEnabled;
    private long mStartTime;
    private String mSessionName;

    public SimpleTimer() {
        this(false);
    }

    public SimpleTimer(boolean enabled) {
        mEnabled = enabled;
    }

    public boolean isEnabled() {
        return ENABLE_SIMPLE_TIMER && LogUtils.isLoggable(LOG_TAG, LogUtils.DEBUG)
                && mEnabled;
    }

    public void start() {
        start(null);
    }

    public void start(String sessionName) {
        mStartTime = SystemClock.uptimeMillis();
        mSessionName = sessionName;
    }

    public void mark(String msg) {
        if (isEnabled()) {
            StringBuilder sb = new StringBuilder();
            if (mSessionName != null) {
                sb.append("(");
                sb.append(mSessionName);
                sb.append(") ");
            }
            sb.append(msg);
            sb.append(": ");
            sb.append(SystemClock.uptimeMillis() - mStartTime);
            sb.append("ms elapsed");
            LogUtils.d(LOG_TAG, sb.toString());
        }
    }

}
