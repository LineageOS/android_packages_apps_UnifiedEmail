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

package com.android.mail.browse;

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
import com.android.mail.browse.ConversationViewAdapter.ConversationItem;
import com.android.mail.browse.ScrollNotifier.ScrollListener;
import com.android.mail.utils.DequeMap;
import com.android.mail.utils.LogUtils;

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

    private ConversationViewAdapter mOverlayAdapter;
    private int[] mOverlayBottoms;
    private ConversationWebView mWebView;

    /**
     * Current document zoom scale per {@link WebView#getScale()}. It does not already account for
     * display density, but by a happy coincidence, this makes the arithmetic for overlay placement
     * easier.
     */
    private float mScale;
    /**
     * Set to true upon receiving the first touch event. Used to help reject invalid WebView scale
     * values.
     */
    private boolean mTouchInitialized;

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

    /**
     * A recycler for scrap views, organized by integer item view type.
     */
    private final DequeMap<Integer, View> mScrapViews = new DequeMap<Integer, View>();

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

    /**
     * Child views of this container should implement this interface to be notified when they are
     * being detached.
     *
     */
    public interface DetachListener {
        /**
         * Called on a child view when it is removed from its parent as part of
         * {@link ConversationContainer} view recycling.
         */
        void onDetachedFromParent();
    }

    public ConversationContainer(Context c) {
        this(c, null);
    }

    public ConversationContainer(Context c, AttributeSet attrs) {
        super(c, attrs);

        mChildrenToRemove = Sets.newHashSet();

        mDensity = c.getResources().getDisplayMetrics().density;
        mScale = mDensity;

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

        mWebView = (ConversationWebView) findViewById(R.id.webview);
        mWebView.addScrollListener(this);
    }

    public void setOverlayAdapter(ConversationViewAdapter a) {
        mOverlayAdapter = a;
        // TODO: register/unregister for dataset notifications on the new/old adapter
    }

    public Adapter getOverlayAdapter() {
        return mOverlayAdapter;
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

        if (!mTouchInitialized) {
            mTouchInitialized = true;
        }

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
        positionOverlays(x, y);
    }

    private void positionOverlays(int x, int y) {
        mOffsetY = y;

        /*
         * The scale value that WebView reports is inaccurate when measured during WebView
         * initialization. This bug is present in ICS, so to work around it, we ignore all
         * reported values and use the density (expected value) instead. Only when the user
         * actually begins to touch the view (to, say, begin a zoom) do we begin to pay attention
         * to WebView-reported scale values.
         */
        if (mTouchInitialized) {
            mScale = mWebView.getScale();
        }
        LogUtils.v(TAG, "in positionOverlays, raw scale=%f, effective scale=%f",
                mWebView.getScale(), mScale);

        if (mOverlayBottoms == null) {
            return;
        }

        // recycle scrolled-off views and add newly visible views

        // we want consecutive spacers/overlays to stack towards the bottom
        // so iterate from the bottom of the conversation up
        // starting with the last spacer bottom and the last adapter item, position adapter views
        // in a single stack until you encounter a non-contiguous expanded message header,
        // then decrement to the next spacer.

        int adapterIndex = mOverlayAdapter.getCount() - 1;
        for (int spacerIndex = mOverlayBottoms.length - 1; spacerIndex >= 0; spacerIndex--) {
            final int spacerBottomY = getOverlayBottom(spacerIndex);

            // always place at least one overlay per spacer
            ConversationItem adapterItem = mOverlayAdapter.getItem(adapterIndex);

            int overlayBottomY = spacerBottomY;
            int overlayTopY = overlayBottomY - adapterItem.getHeight();

            positionOverlay(adapterIndex, overlayTopY, overlayBottomY);

            // and keep stacking overlays as long as they are contiguous
            while (--adapterIndex >= 0) {
                adapterItem = mOverlayAdapter.getItem(adapterIndex);
                if (!adapterItem.isContiguous()) {
                    // advance to the next spacer, but stay on this adapter item
                    break;
                }

                overlayBottomY = overlayTopY; // stack on top of previous overlay
                overlayTopY = overlayBottomY - adapterItem.getHeight();

                positionOverlay(adapterIndex, overlayTopY, overlayBottomY);
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

    private void onOverlayScrolledOff(final View overlayView, final int itemType,
            int overlayTop) {
        // do it asynchronously, as scroll notification can happen during a draw, when it's not
        // safe to remove children

        // ensure that repeated scroll events that want to remove the same header only do it
        // once
        if (mChildrenToRemove.contains(overlayView)) {
            return;
        }

        mChildrenToRemove.add(overlayView);
        post(new Runnable() {
            @Override
            public void run() {
                detachOverlay(overlayView, itemType);
            }
        });

        // push it out of view immediately
        // otherwise this scrolled-off header will continue to draw until the runnable runs
        layoutOverlay(overlayView, overlayTop);
    }

    public View getScrapView(int type) {
        return mScrapViews.poll(type);
    }

    public void addScrapView(int type, View v) {
        mScrapViews.add(type, v);
    }

    private void detachOverlay(View overlayView, int itemType) {
        detachViewFromParent(overlayView);
        mScrapViews.add(itemType, overlayView);
        mChildrenToRemove.remove(overlayView);
        if (overlayView instanceof DetachListener) {
            ((DetachListener) overlayView).onDetachedFromParent();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mScrapViews.visitAll(new DequeMap.Visitor<View>() {
            @Override
            public void visit(View item) {
                removeDetachedView(item, false /* animate */);
            }
        });
        mScrapViews.clear();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        LogUtils.d(TAG, "*** IN header container onMeasure spec for w/h=%d/%d", widthMeasureSpec,
                heightMeasureSpec);

        if (mWebView.getVisibility() != GONE) {
            measureChild(mWebView, widthMeasureSpec, heightMeasureSpec);
        }
        mWidthMeasureSpec = widthMeasureSpec;

        // Need to measure children in case this layout pass was triggered by a child layout change.
        // TODO: restrict child measurement to just that case.
        for (int i = 0, overlayCount = getOverlayCount(); i < overlayCount; i++) {
            final View overlayView = getOverlayAt(i);
            if (overlayView.getVisibility() != GONE) {
                measureItem(overlayView);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        LogUtils.d(TAG, "*** IN header container onLayout");

        mWebView.layout(0, 0, mWebView.getMeasuredWidth(), mWebView.getMeasuredHeight());
        positionOverlays(0, mOffsetY);
    }

    private int getOverlayBottom(int spacerIndex) {
        // TODO: round or truncate?
        return (int) (mOverlayBottoms[spacerIndex] * mScale);
    }

    private void positionOverlay(int adapterIndex, int overlayTopY, int overlayBottomY) {
        View overlayView = findExistingOverlayView(adapterIndex);
        final int itemType = mOverlayAdapter.getItemViewType(adapterIndex);
        // is the overlay visible?
        if (overlayBottomY > mOffsetY && overlayTopY < mOffsetY + getHeight()) {
            // show and/or move overlay
            if (overlayView == null) {
                overlayView = addOverlayView(adapterIndex);
                measureItem(overlayView);
            }
            layoutOverlay(overlayView, overlayTopY);
        } else {
            // hide overlay
            if (overlayView != null) {
                onOverlayScrolledOff(overlayView, itemType, overlayTopY);
            }
        }
    }

    // layout an existing view
    // need its top offset into the conversation, its height, and the scroll offset
    private void layoutOverlay(View child, int childTop) {
        final int top = childTop - mOffsetY;
        final int bottom = top + child.getMeasuredHeight();
        child.layout(0, top, child.getMeasuredWidth(), bottom);
    }

    private View addOverlayView(int adapterIndex) {
        final int itemType = mOverlayAdapter.getItemViewType(adapterIndex);
        final View convertView = mScrapViews.poll(itemType);

        View view = mOverlayAdapter.getView(adapterIndex, convertView, this);
        view.setTag(VIEW_TAG_CONVERSATION_INDEX, adapterIndex);

        // Only re-attach if the view had previously been added to a view hierarchy.
        // Since external components can contribute to the scrap heap (addScrapView), we can't
        // assume scrap views had already been attached.
        if (view.getRootView() != view) {
            LogUtils.d(TAG, "want to REUSE scrolled-in view: index=%d obj=%s",
                    adapterIndex, view);
            attachViewToParent(view, -1, view.getLayoutParams());
        } else {
            LogUtils.d(TAG, "want to CREATE scrolled-in view: index=%d obj=%s",
                    adapterIndex, view);
            addViewInLayout(view, -1, view.getLayoutParams(),
                    true /* preventRequestLayout */);
        }

        return view;
    }

    private View findExistingOverlayView(int adapterIndex) {
        for (int i = 0, count = getOverlayCount(); i < count; i++) {
            final View overlay = getOverlayAt(i);
            final Integer tag = (Integer) overlay.getTag(VIEW_TAG_CONVERSATION_INDEX);
            if (tag != null && tag == adapterIndex) {
                return overlay;
            }
        }
        return null;
    }

    // TODO: add margin support for children that want it (e.g. tablet headers?)
    public void onGeometryChange(int[] overlayBottoms) {
        LogUtils.d(TAG, "*** got overlay spacer bottoms:");
        for (int offsetY : overlayBottoms) {
            LogUtils.d(TAG, "%d", offsetY);
        }

        mOverlayBottoms = overlayBottoms;
        positionOverlays(0, mOffsetY);
    }

}
