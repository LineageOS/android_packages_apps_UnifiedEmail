/*
 * Copyright (C) 2011 Google Inc.
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

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;

import com.android.mail.utils.LogUtils;

/**
 * A WebView for HTML messages with custom zoom and scroll behavior.
 *
 */
public class MessageWebView extends WebView {

    private float mMaxScale = 0;
    /**
     * WebView with height=content_wrap doesn't shrink its height when zooming out.
     * So to force this behavior, when zooming out (scale has shrunken) trick the measurement
     * of WebView so as to make it think that it *should* fit the height to its bound AND
     * that its view height is now zero. WebView will then attempt to fit its content into
     * a zero-height view, and then resize the view height to the 'natural' content height.
     *
     * The measurement trick should be turned off on following onLayout to allow WebKit to grow its
     * height again.
     */
    private boolean mShrinkMeasuredHeight;
    /**
     * When tricking the WebView to be zero height, views below it will shift up when drawing,
     * and then shift back down when the WebView does its corrective layout pass.
     * To avoid this shifting, force the WebView's parent view to keep its height fixed until
     * the corrective layout pass is over, at which point we can restore the normal WebView height
     * and the views below will draw in their correct final positions.
     */
    private int mParentLayoutHeight;
    private boolean mCheckedWidePage;

    private static final boolean ENABLE_WIDE_VIEWPORT_MODE = false;
    private static final String LOG_TAG = new LogUtils().getLogTag();

    public MessageWebView(Context context) {
        this(context, null);
    }

    public MessageWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Set the view parent's height and trigger a layout.
     *
     * @param measure if true, set the height to the current measured height and ignore the height
     * parameter
     * @param height a height to set it to. ignored if measure is true
     * @return the original height
     */
    private int setParentHeight(boolean measure, int height) {
        int originalHeight = 0;
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup parentGroup = (ViewGroup) getParent();
            ViewGroup.LayoutParams parentLayoutParams = parentGroup.getLayoutParams();
            originalHeight = parentLayoutParams.height;
            parentLayoutParams.height = (measure) ? parentGroup.getHeight() : height;
            parentGroup.setLayoutParams(parentLayoutParams);
        }
        return originalHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        // Allow WebView to measure as it normally would, which sets the HeightCanMeasure flag to
        // trigger a WebKit layout. Afterwards, override the measured height with zero so that
        // WebKit layout uses a desired height of zero, which shrinks the view to true content
        // height.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mShrinkMeasuredHeight && heightMode != MeasureSpec.EXACTLY) {
            setMeasuredDimension(getMeasuredWidthAndState(), 0);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        LogUtils.d(LOG_TAG, "IN %d onLayout, changed=%b w/h=%d/%d l/t/r/b=%d/%d/%d/%d z=%f",
                (hashCode() % 1000), changed, getWidth(), getHeight(), l, t, r, b , getScale());
        super.onLayout(changed, l, t, r, b);

        float scale = getScale();

        if (mShrinkMeasuredHeight) {
            // restore normal measurement behavior on the first layout after overriding height
            // also restore parent container height
            setParentHeight(false, mParentLayoutHeight);
            mShrinkMeasuredHeight = false;

        } else if (getHeight() > 0) {
            if (scale < mMaxScale && !getSettings().getUseWideViewPort()) {
                // fix the parent's height to whatever it is now.
                // (normally the parent would be recalculated as header + body height)
                // this will prevent messages below from shifting up
                // and then when it's all done, set it back to the original value
                // so the below messages just finally adjust now that the height is settled.

                // we expect that WebView will trigger another layout anyway,
                // and we have to inject this new sizing in before then.
                LogUtils.d(LOG_TAG, "*** shrinking height of webview=" + (hashCode() % 1000));

                mParentLayoutHeight = setParentHeight(true, 0);
                mMaxScale = 0;
                // force all measurements from now until the next layout to claim a zero height
                mShrinkMeasuredHeight = true;

            } else if (scale > mMaxScale) {
                mMaxScale = getScale();

                if (ENABLE_WIDE_VIEWPORT_MODE) {
                    if (!mCheckedWidePage) {
                        if ((getMeasuredWidthAndState() & MEASURED_STATE_TOO_SMALL) != 0) {
                            LogUtils.i(LOG_TAG, "*** setting wide page mode for webview=%d",
                                    (hashCode() % 1000));
                            getSettings().setUseWideViewPort(true);
                            // FIXME: wide viewport mode does not grow/shrink height as expected
                            // maybe override height at this point and turn off reflow so that
                            // scaling is linear, and we can manually maintain view height.
                        }
                        mCheckedWidePage = true;
                    }
                }
            }
        }
    }

}
