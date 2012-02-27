/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Adapter;
import android.widget.ScrollView;

import com.android.mail.ui.ScrollNotifier.ScrollListener;
import com.android.mail.utils.LogUtils;

/**
 * TODO
 *
 */
public class ConversationContainer extends ViewGroup implements ScrollListener {

    private static final String TAG = new LogUtils().getLogTag();

    private Adapter mOverlayAdapter;
    private int[] mOverlayTops;
    private ConversationWebView mWebView;

    /**
     * Current document zoom scale per {@link WebView#getScale()}. It does not already account for
     * display density, but by a happy coincidence, this makes the arithmetic for overlay placement
     * easier.
     */
    private float mScale;

    /**
     * System touch-slop distance per {@link ViewConfiguration#getScaledTouchSlop()}.
     */
    private final int mTouchSlop;
    /**
     * Current scroll position, as dictated by the background {@link WebView}.
     */
    private int mOffsetY;
    /**
     * Original pointer Y for slop calculation.
     */
    private float mLastMotionY;
    /**
     * Original pointer ID for slop calculation.
     */
    private int mActivePointerId;
    /**
     * Track pointer up/down state to know whether to send a make-up DOWN event to WebView.
     * WebView internal logic requires that a stream of {@link MotionEvent#ACTION_MOVE} events be
     * preceded by a {@link MotionEvent#ACTION_DOWN} event.
     */
    private boolean mTouchIsDown = false;
    /**
     * Remember if touch interception was triggered on a {@link MotionEvent#ACTION_POINTER_DOWN},
     * so we can send a make-up event in {@link #onTouchEvent(MotionEvent)}.
     */
    private boolean mMissedPointerDown;

    public ConversationContainer(Context c) {
        this(c, null);
    }

    public ConversationContainer(Context c, AttributeSet attrs) {
        super(c, attrs);

        mTouchSlop = ViewConfiguration.get(c).getScaledTouchSlop();

        // Disabling event splitting fixes pinch-zoom when the first pointer goes down on the
        // WebView and the second pointer goes down on an overlay view.
        // Intercepting ACTION_POINTER_DOWN events allows pinch-zoom to work when the first pointer
        // goes down on an overlay view.
        setMotionEventSplittingEnabled(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mWebView = (ConversationWebView) getChildAt(0);
        mWebView.addScrollListener(this);
    }

    public void setOverlayAdapter(Adapter a) {
        mOverlayAdapter = a;
    }

    private int getOverlayCount() {
        return Math.max(0, getChildCount() - 1);
    }

    private View getOverlayAt(int i) {
        return getChildAt(i + 1);
    }

    private void forwardFakeMotionEvent(MotionEvent original, int newAction) {
        MotionEvent newEvent = MotionEvent.obtain(original);
        newEvent.setAction(newAction);
        mWebView.onTouchEvent(newEvent);
        LogUtils.v(TAG, "in Container.OnTouch. fake: action=%d x/y=%f/%f pointers=%d",
                newEvent.getActionMasked(), newEvent.getX(), newEvent.getY(),
                newEvent.getPointerCount());
    }

    /**
     * Touch slop code was copied from {@link ScrollView#onInterceptTouchEvent(MotionEvent)}.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercept = false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                intercept = true;
                mMissedPointerDown = true;
                break;

            case MotionEvent.ACTION_DOWN:
                mLastMotionY = ev.getY();
                mActivePointerId = ev.getPointerId(0);
                break;

            case MotionEvent.ACTION_MOVE:
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                final float y = ev.getY(pointerIndex);
                final int yDiff = (int) Math.abs(y - mLastMotionY);
                if (yDiff > mTouchSlop) {
                    mLastMotionY = y;
                    intercept = true;
                }
                break;
        }

        LogUtils.v(TAG, "in Container.InterceptTouch. action=%d x/y=%f/%f pointers=%d result=%s",
                ev.getActionMasked(), ev.getX(), ev.getY(), ev.getPointerCount(), intercept);
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();

        if (action == MotionEvent.ACTION_UP) {
            mTouchIsDown = false;
        } else if (!mTouchIsDown &&
                (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_POINTER_DOWN)) {

            forwardFakeMotionEvent(ev, MotionEvent.ACTION_DOWN);
            if (mMissedPointerDown) {
                forwardFakeMotionEvent(ev, MotionEvent.ACTION_POINTER_DOWN);
                mMissedPointerDown = false;
            }

            mTouchIsDown = true;
        }

        final boolean webViewResult = mWebView.onTouchEvent(ev);

        LogUtils.v(TAG, "in Container.OnTouch. action=%d x/y=%f/%f pointers=%d",
                ev.getActionMasked(), ev.getX(), ev.getY(), ev.getPointerCount());
        return webViewResult;
    }

    @Override
    public void onNotifierScroll(int x, int y) {
        mOffsetY = y;
        mScale = mWebView.getScale();
        LogUtils.v(TAG, "*** IN on scroll, x/y=%d/%d zoom=%f", x, y, mScale);
        layoutOverlays();

        // TODO: recycle scrolled-off views and add newly visible views
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        LogUtils.d(TAG, "*** IN header container onMeasure spec for w/h=%d/%d", widthMeasureSpec,
                heightMeasureSpec);

        measureChildren(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        LogUtils.d(TAG, "*** IN header container onLayout");
        mWebView.layout(0, 0, mWebView.getMeasuredWidth(),
                mWebView.getMeasuredHeight());

        layoutOverlays();
    }

    private void layoutOverlays() {
        final int count = getOverlayCount();

        if (count > 0 && count != mOverlayTops.length) {
            LogUtils.e(TAG,
                    "Header/body count mismatch. headers=%d, message bodies=%d",
                    count, mOverlayTops.length);
        }

        for (int i = 0; i < count; i++) {
            View child = getOverlayAt(i);
            // TODO: round or truncate?
            final int top = (int) (mOverlayTops[i] * mScale) - mOffsetY;
            final int bottom = top + child.getMeasuredHeight();
            child.layout(0, top, child.getMeasuredWidth(), bottom);
        }
    }

    // TODO: add margin support for children that want it (e.g. tablet headers?)

    public void onGeometryChange(int[] messageTops) {
        LogUtils.d(TAG, "*** got message tops:");
        for (int top : messageTops) {
            LogUtils.d(TAG, "%d", top);
        }

        mOverlayTops = messageTops;

        for (int i = 0; i < messageTops.length; i++) {
            View overlayView = getOverlayAt(i);
            if (overlayView == null) {
                // TODO: dig through recycler instead of creating new views each time
                overlayView = mOverlayAdapter.getView(i, null, this);
                addView(overlayView, i + 1);
            }
            // TODO: inform header of its bottom (== top of the next header) so it can know where
            // to position bottom-anchored content like attachments
        }

        mScale = mWebView.getScale();

    }

}
