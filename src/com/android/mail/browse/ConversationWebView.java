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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.webkit.WebView;

import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ConversationWebView extends WebView implements ScrollNotifier {
    /** The initial delay when rendering in hardware layer. */
    private static int sWebviewInitialDelay;

    /** The dimension of the offscreen bitmap used for software layer. */
    private static int sBitmapWidth;
    private static int sBitmapHeight;

    /** The default {@link Matrix} and {@link Paint} used to draw software layer. */
    private static Matrix sMatrix;
    private static Paint sPaint;

    private final Handler mHandler = new Handler();

    private Bitmap mBitmap;
    private Canvas mCanvas;

    private boolean mUseSoftwareLayer;

    /** The visible portion of webview. */
    private int mLeft = 0;
    private int mTop = 0;

    /** {@link Runnable} to be run when the page is rendered in hardware layer. */
    private final Runnable mNotifyPageRenderedInHardwareLayer = new Runnable() {
        @Override
        public void run() {
            // Switch to hardware layer.
            mUseSoftwareLayer = false;
            destroyBitmap();
            invalidate();
        }
    };

    @Override
    public void onDraw(Canvas canvas) {
        // Always render in hardware layer to avoid flicker when switch.
        super.onDraw(canvas);

        // Render in software layer on top if needed.
        if (mUseSoftwareLayer && mBitmap != null) {
            mCanvas.save();
            mCanvas.translate(-mLeft, -mTop);
            super.onDraw(mCanvas);
            mCanvas.restore();
            canvas.save();
            canvas.translate(mLeft, mTop);
            canvas.drawBitmap(mBitmap, sMatrix, sPaint);
            canvas.restore();
        }
    }

    @Override
    public void destroy() {
        destroyBitmap();
        mHandler.removeCallbacksAndMessages(null);

        super.destroy();
    }

    /**
     * Destroys the {@link Bitmap} used for software layer.
     */
    private void destroyBitmap() {
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
    }

    /**
     * Enable this WebView to also draw to an internal software canvas until
     * {@link #onVisibilityChanged(boolean)} is called. The software draw will happen every time
     * a normal {@link #onDraw(Canvas)} happens, and will overwrite whatever is normally drawn
     * (i.e. drawn in hardware) with the results of software rendering.
     * <p>
     * This is useful when you know that the WebView draws sooner to a software layer than it does
     * to its normal hardware layer.
     */
    public void setUseSoftwareLayer(boolean useSoftware) {
        mUseSoftwareLayer = useSoftware;
        if (useSoftware) {
            if (sMatrix == null) {
                final Resources res = getContext().getResources();
                sWebviewInitialDelay = res.getInteger(R.integer.webview_initial_delay);

                final Display display = ((WindowManager) getContext().getSystemService(
                        Context.WINDOW_SERVICE)).getDefaultDisplay();
                sBitmapWidth = display.getWidth();
                sBitmapHeight = display.getHeight();

                sMatrix = new Matrix();
                sPaint = new Paint();
            }

            // Create an offscreen bitmap.
            mBitmap = Bitmap.createBitmap(sBitmapWidth, sBitmapHeight, Bitmap.Config.RGB_565);
            mCanvas = new Canvas(mBitmap);
        }
    }

    /**
     * Notifies the {@link CustomWebView} that it has become visible. It can use this signal to
     * switch between software and hardware layer.
     */
    public void onVisibilityChanged(boolean isVisible) {
        if (isVisible && mUseSoftwareLayer) {
            // Schedule to switch from software layer to hardware layer in 1s.
            mHandler.postDelayed(mNotifyPageRenderedInHardwareLayer, sWebviewInitialDelay);
        }
    }

    // NARROW_COLUMNS reflow can trigger the document to change size, so notify interested parties.
    public interface ContentSizeChangeListener {
        void onHeightChange(int h);
    }

    private ContentSizeChangeListener mSizeChangeListener;

    private int mCachedContentHeight;

    private final int mViewportWidth;
    private final float mDensity;

    private final Set<ScrollListener> mScrollListeners =
            new CopyOnWriteArraySet<ScrollListener>();

    /**
     * True when WebView is handling a touch-- in between POINTER_DOWN and
     * POINTER_UP/POINTER_CANCEL.
     */
    private boolean mHandlingTouch;

    private static final String LOG_TAG = LogTag.getLogTag();

    public ConversationWebView(Context c) {
        this(c, null);
    }

    public ConversationWebView(Context c, AttributeSet attrs) {
        super(c, attrs);

        mViewportWidth = getResources().getInteger(R.integer.conversation_webview_viewport_px);
        mDensity = getResources().getDisplayMetrics().density;
    }

    @Override
    public void addScrollListener(ScrollListener l) {
        mScrollListeners.add(l);
    }

    @Override
    public void removeScrollListener(ScrollListener l) {
        mScrollListeners.remove(l);
    }

    public void setContentSizeChangeListener(ContentSizeChangeListener l) {
        mSizeChangeListener = l;
    }


    @Override
    public int computeVerticalScrollRange() {
        return super.computeVerticalScrollRange();
    }

    @Override
    public int computeVerticalScrollOffset() {
        return super.computeVerticalScrollOffset();
    }

    @Override
    public int computeVerticalScrollExtent() {
        return super.computeVerticalScrollExtent();
    }

    @Override
    public int computeHorizontalScrollRange() {
        return super.computeHorizontalScrollRange();
    }

    @Override
    public int computeHorizontalScrollOffset() {
        return super.computeHorizontalScrollOffset();
    }

    @Override
    public int computeHorizontalScrollExtent() {
        return super.computeHorizontalScrollExtent();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        // Update the visible portion of webview.
        mLeft = l;
        mTop = t;

        for (ScrollListener listener : mScrollListeners) {
            listener.onNotifierScroll(l, t);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();

        if (mSizeChangeListener != null) {
            final int contentHeight = getContentHeight();
            if (contentHeight != mCachedContentHeight) {
                mCachedContentHeight = contentHeight;
                mSizeChangeListener.onHeightChange(contentHeight);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mHandlingTouch = true;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                LogUtils.d(LOG_TAG, "WebView disabling intercepts: POINTER_DOWN");
                requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mHandlingTouch = false;
                break;
        }

        return super.onTouchEvent(ev);
    }

    public boolean isHandlingTouch() {
        return mHandlingTouch;
    }

    public int getViewportWidth() {
        return mViewportWidth;
    }

    /**
     * Similar to {@link #getScale()}, except that it returns the initially expected scale, as
     * determined by the ratio of actual screen pixels to logical HTML pixels.
     * <p>This assumes that we are able to control the logical HTML viewport with a meta-viewport
     * tag.
     */
    public float getInitialScale() {
        // an HTML meta-viewport width of "device-width" and unspecified (medium) density means
        // that the default scale is effectively the screen density.
        return mDensity;
    }

    public int screenPxToWebPx(int screenPx) {
        return (int) (screenPx / getInitialScale());
    }

    public int webPxToScreenPx(int webPx) {
        return (int) (webPx * getInitialScale());
    }

    public float screenPxToWebPxError(int screenPx) {
        return screenPx / getInitialScale() - screenPxToWebPx(screenPx);
    }

    public float webPxToScreenPxError(int webPx) {
        return webPx * getInitialScale() - webPxToScreenPx(webPx);
    }
}
