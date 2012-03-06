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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Adapter;
import android.widget.ListView;
import android.widget.ScrollView;

import com.android.mail.R;
import com.android.mail.ui.ScrollNotifier.ScrollListener;
import com.android.mail.utils.LogUtils;

import java.util.Deque;
import java.util.Set;

/**
 * A specialized ViewGroup container for conversation view. It is designed to contain a single
 * {@link WebView} and a number of overlay views that draw on top of the WebView. In the Mail app,
 * the WebView contains all HTML message bodies in a conversation, and the overlay views are the
 * subject view, message headers, and attachment views. The WebView does all scroll handling, and
 * this container manages scrolling of the overlay views so that they move in tandem.
 *
 * <h5>INPUT HANDLING</h5>
 * Placing the WebView in the same container as the overlay views means we don't have to do a lot of
 * manual manipulation of touch events. We do have a
 * {@link #forwardFakeMotionEvent(MotionEvent, int)} method that deals with one WebView
 * idiosyncrasy: it doesn't react well when touch MOVE events stream in without a preceding DOWN.
 *
 * <h5>VIEW RECYCLING</h5>
 * Normally, it would make sense to put all overlay views into a {@link ListView}. But this view
 * sandwich has unique characteristics: the list items are scrolled based on an external controller,
 * and we happen to know all of the overlay positions up front. So it didn't make sense to shoehorn
 * a ListView in and instead, we rolled our own view recycler by borrowing key details from
 * ListView and AbsListView.
 *
 */
public class ConversationContainer extends ViewGroup implements ScrollListener {

    private static final String TAG = new LogUtils().getLogTag();

    private Adapter mOverlayAdapter;
    private int[] mOverlayBottoms;
    private int[] mOverlayHeights;
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

    private final Deque<View> mScrapViews;

    /**
     * The set of children queued for later removal by a Runnable posted to the UI thread (no
     * synchronization required). Scroll changes cause children to be added to this set, and the
     * Runnable later removes the children when it safely detaches them outside of a
     * draw/getDisplayList operation.
     * <p>
     * WebView sometimes notifies of scroll changes during a draw (or display list generation), when
     * it's not safe to detach view children because ViewGroup is in the middle of iterating over
     * its child array.
     */
    private final Set<View> mChildrenToRemove;

    private final float mDensity;

    private int mWidthMeasureSpec;

    private static final int VIEW_TAG_CONVERSATION_INDEX = R.id.view_tag_conversation_index;

    public ConversationContainer(Context c) {
        this(c, null);
    }

    public ConversationContainer(Context c, AttributeSet attrs) {
        super(c, attrs);

        mScrapViews = Lists.newLinkedList();
        mChildrenToRemove = Sets.newHashSet();

        mDensity = c.getResources().getDisplayMetrics().density;

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
    public void onNotifierScroll(final int x, final int y) {
        handleScroll(x, y);
    }

    private void handleScroll(int x, int y) {
        mOffsetY = y;
        mScale = mWebView.getScale();

        // recycle scrolled-off views and add newly visible views
        final int containerHeight = getHeight();
        for (int convIndex = 0; convIndex < mOverlayBottoms.length; convIndex++) {
            final int overlayTopY = getOverlayTop(convIndex);
            final int overlayBottomY = getOverlayBottom(convIndex);
            View overlayView = getOverlayWithTag(convIndex);
            if (overlayBottomY > mOffsetY && overlayTopY < mOffsetY + containerHeight) {

                if (overlayView == null) {
                    final View convertView = mScrapViews.poll();
                    overlayView = mOverlayAdapter.getView(convIndex, convertView, this);
                    overlayView.setTag(VIEW_TAG_CONVERSATION_INDEX, convIndex);
                    if (convertView != null) {
                        LogUtils.v(TAG, "want to REUSE scrolled-in view: index=%d obj=%s",
                                convIndex, overlayView);
                        attachViewToParent(overlayView, -1, overlayView.getLayoutParams());
                    } else {
                        LogUtils.v(TAG, "want to CREATE scrolled-in view: index=%d obj=%s",
                                convIndex, overlayView);
                        addViewInLayout(overlayView, -1, overlayView.getLayoutParams(),
                                true /* preventRequestLayout */);
                    }
                    // do a manual measure pass of the new or reused child
                    // a new child needs a measure to size itself, and a reused child's dimensions
                    // may not match those of the new item
                    measureItem(overlayView);
                }
                layoutOverlay(overlayView, convIndex);

            } else {

                if (overlayView != null) {
                    onOverlayScrolledOff(overlayView, convIndex);
                }
            }
        }
    }

    /**
     * Copied/stolen from {@link ListView}.
     */
    private void measureItem(View child) {
        ViewGroup.LayoutParams p = child.getLayoutParams();
        if (p == null) {
            p = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        int childWidthSpec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight(), p.width);
        int lpHeight = p.height;
        int childHeightSpec;
        if (lpHeight > 0) {
            childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
        } else {
            childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    private void onOverlayScrolledOff(final View overlayView, final int convIndex) {
        // do it asynchronously, as scroll notification can happen during a draw, when it's not
        // safe to remove children

        // ensure that repeated scroll events that want to remove the same header only do it
        // once
        if (mChildrenToRemove.contains(overlayView)) {
            LogUtils.v(TAG, "ignoring duplicate request to detach header at convIndex=%d",
                    convIndex);
            return;
        }

        LogUtils.v(TAG, "queueing request to detach header at convIndex=%d", convIndex);
        mChildrenToRemove.add(overlayView);
        post(new Runnable() {
            @Override
            public void run() {
                detachOverlay(overlayView, convIndex);
            }
        });

        // push it out of view immediately
        // otherwise this scrolled-off header will continue to draw until the runnable runs
        layoutOverlay(overlayView, convIndex);
    }

    private void detachOverlay(View overlayView, int convIndex) {
        LogUtils.v(TAG, "want to remove now-hidden view: index=%d obj=%s children=%d",
                convIndex, overlayView, getChildCount());
        detachViewFromParent(overlayView);
        mScrapViews.add(overlayView);
        mChildrenToRemove.remove(overlayView);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        LogUtils.d(TAG, "*** IN header container onMeasure spec for w/h=%d/%d", widthMeasureSpec,
                heightMeasureSpec);

        measureChildren(widthMeasureSpec, heightMeasureSpec);
        mWidthMeasureSpec = widthMeasureSpec;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        LogUtils.d(TAG, "*** IN header container onLayout");
        mWebView.layout(0, 0, mWebView.getMeasuredWidth(),
                mWebView.getMeasuredHeight());

        layoutOverlays();
    }

    private int getOverlayTop(int convIndex) {
        return (int) (mOverlayBottoms[convIndex] * mScale)
                - (int) (mOverlayHeights[convIndex] * mDensity);
    }

    private int getOverlayBottom(int convIndex) {
        return (int) (mOverlayBottoms[convIndex] * mScale);
    }

    private void layoutOverlay(View child, int convIndex) {
        // TODO: round or truncate?
        final int top = getOverlayTop(convIndex) - mOffsetY;
        final int bottom = top + child.getMeasuredHeight();
        child.layout(0, top, child.getMeasuredWidth(), bottom);
    }

    private void layoutOverlays() {
        final int count = getOverlayCount();

        for (int i = 0; i < count; i++) {
            View child = getOverlayAt(i);
            Integer convIndex = (Integer) child.getTag(VIEW_TAG_CONVERSATION_INDEX);
            layoutOverlay(child, convIndex);
        }
    }

    private View getOverlayWithTag(int index) {
        for (int i = 0, count = getOverlayCount(); i < count; i++) {
            final View overlay = getOverlayAt(i);
            final Integer convIndex = (Integer) overlay.getTag(VIEW_TAG_CONVERSATION_INDEX);
            if (convIndex != null && convIndex == index) {
                return overlay;
            }
        }
        return null;
    }

    // TODO: add margin support for children that want it (e.g. tablet headers?)
    // TODO: support calling this method more than once per webpage instance (clear out existing
    // headers and re-create at current offset?)
    public void onGeometryChange(int[] headerBottoms, int[] headerHeights) {
        LogUtils.d(TAG, "*** got message header bottoms:");
        for (int offsetY : headerBottoms) {
            LogUtils.d(TAG, "%d", offsetY);
        }

        mScale = mWebView.getScale();

        mOverlayBottoms = headerBottoms;
        mOverlayHeights = headerHeights;

        if (mOverlayBottoms.length != mOverlayHeights.length) {
            LogUtils.wtf(TAG, "message header count mismatch: # bottoms=%d, # heights=%d",
                    mOverlayBottoms.length, mOverlayHeights.length);
        }

        // TODO: don't remove visible views. not an issue yet since this is only called once.
        while (getOverlayCount() > 0) {
            removeView(getOverlayAt(0));
        }

        // hack to bootstrap initial display of headers
        handleScroll(0, mOffsetY);

        // TODO: inform each header of its bottom (== top of the next header) so it can know where
        // to position bottom-anchored content like attachments
    }

}
