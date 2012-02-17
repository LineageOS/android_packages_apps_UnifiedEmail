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
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Adapter;

import com.android.mail.ui.ScrollNotifier.ScrollListener;
import com.android.mail.utils.LogUtils;

/**
 * TODO
 *
 */
public class ConversationContainer extends ViewGroup implements ScrollListener {

    private Adapter mOverlayAdapter;
    private int[] mOverlayTops;

    private static final String TAG = new LogUtils().getLogTag();

    private int mOffsetY;
    private float mScale;

    public ConversationContainer(Context c) {
        this(c, null);
    }

    public ConversationContainer(Context c, AttributeSet attrs) {
        super(c, attrs);
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

    private WebView getBackgroundView() {
        return (WebView) getChildAt(0);
    }

    @Override
    public void onNotifierScroll(int x, int y) {
        mOffsetY = y;
        mScale = getBackgroundView().getScale();
        LogUtils.v(TAG, "*** IN on scroll, x/y=%d/%d zoom=%f", x, y, mScale);
        layoutOverlays();

        // TODO: recycle scrolled-off views and add newly visible views
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        LogUtils.d(TAG, "*** IN header container onMeasure");
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        measureChildren(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        LogUtils.d(TAG, "*** IN header container onLayout");
        final View backgroundView = getBackgroundView();
        backgroundView.layout(0, 0, backgroundView.getMeasuredWidth(),
                backgroundView.getMeasuredHeight());

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

    // TODO: add margin support for children that want it (e.g. tablet headers)

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

        mScale = getBackgroundView().getScale();

    }

}
