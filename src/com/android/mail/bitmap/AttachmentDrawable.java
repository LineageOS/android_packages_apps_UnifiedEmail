package com.android.mail.bitmap;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.animation.LinearInterpolator;

import com.android.bitmap.BitmapCache;
import com.android.bitmap.BitmapUtils;
import com.android.bitmap.DecodeTask;
import com.android.bitmap.DecodeTask.Request;
import com.android.bitmap.ReusableBitmap;
import com.android.mail.R;
import com.android.mail.browse.ConversationItemViewCoordinates;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class encapsulates all functionality needed to display a single image attachment thumbnail,
 * including request creation/cancelling, data unbinding and re-binding, and fancy animations
 * to draw upon state changes.
 * <p>
 * The actual bitmap decode work is handled by {@link DecodeTask}.
 */
public class AttachmentDrawable extends Drawable implements DecodeTask.BitmapView,
        Drawable.Callback, Runnable, Parallaxable {

    private DecodeTask.Request mCurrKey;
    private ReusableBitmap mBitmap;
    private final BitmapCache mCache;
    private DecodeTask mTask;
    private int mDecodeWidth;
    private int mDecodeHeight;
    private int mLoadState = LOAD_STATE_UNINITIALIZED;
    private float mParallaxFraction = 0.5f;

    // each attachment gets its own placeholder and progress indicator, to be shown, hidden,
    // and animated based on Drawable#setVisible() changes, which are in turn driven by
    // #setLoadState().
    private Placeholder mPlaceholder;
    private Progress mProgress;

    private static final Executor SMALL_POOL_EXECUTOR = new ThreadPoolExecutor(4, 4,
            1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    private static final Executor EXECUTOR = SMALL_POOL_EXECUTOR;

    private static final boolean LIMIT_BITMAP_DENSITY = true;

    private static final int MAX_BITMAP_DENSITY = DisplayMetrics.DENSITY_HIGH;

    private static final int LOAD_STATE_UNINITIALIZED = 0;
    private static final int LOAD_STATE_NOT_LOADED = 1;
    private static final int LOAD_STATE_LOADING = 2;
    private static final int LOAD_STATE_LOADED = 3;

    private final ConversationItemViewCoordinates mCoordinates;
    private final float mDensity;
    private final int mProgressDelayMs;
    private final Paint mPaint = new Paint();
    private final Rect mSrcRect = new Rect();
    private final Handler mHandler = new Handler();

    public AttachmentDrawable(Resources res, BitmapCache cache,
            ConversationItemViewCoordinates coordinates, Drawable placeholder, Drawable progress) {
        mCoordinates = coordinates;
        mDensity = res.getDisplayMetrics().density;
        mCache = cache;
        mPaint.setFilterBitmap(true);

        final int fadeOutDurationMs = res.getInteger(R.integer.ap_fade_animation_duration);
        final int tileColor = res.getColor(R.color.ap_background_color);
        mProgressDelayMs = res.getInteger(R.integer.ap_progress_animation_delay);

        mPlaceholder = new Placeholder(placeholder.getConstantState().newDrawable(res), res,
                coordinates, fadeOutDurationMs, tileColor);
        mPlaceholder.setCallback(this);

        mProgress = new Progress(progress.getConstantState().newDrawable(res), res,
                coordinates, fadeOutDurationMs, tileColor);
        mProgress.setCallback(this);
    }

    public DecodeTask.Request getKey() {
        return mCurrKey;
    }

    public void setDecodeWidth(int width) {
        mDecodeWidth = width;
    }

    public void setDecodeHeight(int height) {
        mDecodeHeight = height;
    }

    public void setImage(DecodeTask.Request key) {
        if (mCurrKey != null && mCurrKey.equals(key)) {
            return;
        }
        if (mBitmap != null) {
            mBitmap.releaseReference();
//            System.out.println("view.bind() decremented ref to old bitmap: " + mBitmap);
            mBitmap = null;
        }
        mCurrKey = key;

        if (mTask != null) {
            mTask.cancel();
            mTask = null;
        }

        mHandler.removeCallbacks(this);
        setLoadState(LOAD_STATE_UNINITIALIZED);

        if (key == null) {
            return;
        }

        // find cached entry here and skip decode if found.
        final ReusableBitmap cached = mCache.get(key);
        if (cached != null) {
            cached.acquireReference();
            setBitmap(cached);
            setLoadState(LOAD_STATE_LOADED);
        } else {
            decode();
        }
    }

    @Override
    public void setParallaxFraction(float fraction) {
        mParallaxFraction = fraction;
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }

        if (mBitmap != null) {
            BitmapUtils.calculateCroppedSrcRect(mBitmap.getLogicalWidth(),
                    mBitmap.getLogicalHeight(), bounds.width(), bounds.height(),
                    mCoordinates.attachmentPreviewsDecodeHeight, mParallaxFraction, mSrcRect);
            canvas.drawBitmap(mBitmap.bmp, mSrcRect, bounds, mPaint);
        }

        // Draw the two possible overlay layers in reverse-priority order.
        // (each layer will no-op the draw when appropriate)
        // This ordering means cross-fade transitions are just fade-outs of each layer.
        mProgress.draw(canvas);
        mPlaceholder.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        final int old = mPaint.getAlpha();
        mPaint.setAlpha(alpha);
        mPlaceholder.setAlpha(alpha);
        mProgress.setAlpha(alpha);
        if (alpha != old) {
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
        mPlaceholder.setColorFilter(cf);
        mProgress.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return (mBitmap != null && (mBitmap.bmp.hasAlpha() || mPaint.getAlpha() < 255)) ?
                PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        mPlaceholder.setBounds(bounds);
        mProgress.setBounds(bounds);
    }

    @Override
    public void onDecodeBegin(Request key) {
        if (!key.equals(mCurrKey)) {
            return;
        }
        // normally, we'd transition to the LOADING state now, but we want to delay that a bit
        // to minimize excess occurrences of the rotating spinner
        mHandler.postDelayed(this, mProgressDelayMs);
    }

    @Override
    public void run() {
        if (mLoadState == LOAD_STATE_NOT_LOADED) {
            setLoadState(LOAD_STATE_LOADING);
        }
    }

    @Override
    public void onDecodeComplete(Request key, ReusableBitmap result) {
        if (key.equals(mCurrKey)) {
            setBitmap(result);
        } else {
            // if the requests don't match (i.e. this request is stale), decrement the
            // ref count to allow the bitmap to be pooled
            if (result != null) {
                result.releaseReference();
            }
        }
    }

    private void setBitmap(ReusableBitmap bmp) {
        if (mBitmap != null && mBitmap != bmp) {
            mBitmap.releaseReference();
        }
        mBitmap = bmp;
        setLoadState(LOAD_STATE_LOADED);
        invalidateSelf();
    }

    private void decode() {
        final int w;
        final int h;

        if (LIMIT_BITMAP_DENSITY) {
            final float scale =
                    Math.min(1f, (float) MAX_BITMAP_DENSITY / DisplayMetrics.DENSITY_DEFAULT
                            / mDensity);
            w = (int) (mDecodeWidth * scale);
            h = (int) (mDecodeHeight * scale);
        } else {
            w = mDecodeWidth;
            h = mDecodeHeight;
        }

        if (w == 0 || h == 0 || mCurrKey == null) {
            return;
        }
//        System.out.println("ITEM " + this + " w=" + w + " h=" + h);
        if (mTask != null) {
            mTask.cancel();
        }
        setLoadState(LOAD_STATE_NOT_LOADED);
        mTask = new DecodeTask(mCurrKey, w, h, this, mCache);
        mTask.executeOnExecutor(EXECUTOR);
    }

    private void setLoadState(int loadState) {
        if (mLoadState == loadState) {
            return;
        }

        switch (loadState) {
            case LOAD_STATE_UNINITIALIZED:
                mPlaceholder.reset();
                mProgress.reset();
                break;
            case LOAD_STATE_NOT_LOADED:
                mPlaceholder.setVisible(true);
                mProgress.setVisible(false);
                break;
            case LOAD_STATE_LOADING:
                mPlaceholder.setVisible(false);
                mProgress.setVisible(true);
                break;
            case LOAD_STATE_LOADED:
                mPlaceholder.setVisible(false);
                mProgress.setVisible(false);
                break;
        }

        mLoadState = loadState;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }

    private static class Placeholder extends TileDrawable {

        private final ValueAnimator mPulseAnimator;

        public Placeholder(Drawable placeholder, Resources res,
                ConversationItemViewCoordinates coordinates, int fadeOutDurationMs,
                int tileColor) {
            super(placeholder, coordinates.placeholderWidth, coordinates.placeholderHeight,
                    tileColor, fadeOutDurationMs);
            mPulseAnimator = ValueAnimator.ofInt(55, 255)
                    .setDuration(res.getInteger(R.integer.ap_placeholder_animation_duration));
            mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            mPulseAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setInnerAlpha((Integer) animation.getAnimatedValue());
                }
            });
        }

        @Override
        public boolean setVisible(boolean visible) {
            final boolean changed = super.setVisible(visible);
            if (changed) {
                if (isVisible()) {
                    // start
                    if (mPulseAnimator != null) {
                        mPulseAnimator.start();
                    }
                } else {
                    // stop
                    if (mPulseAnimator != null) {
                        mPulseAnimator.cancel();
                    }
                }
            }
            return changed;
        }

    }

    private static class Progress extends TileDrawable {

        private final ValueAnimator mRotateAnimator;

        public Progress(Drawable progress, Resources res,
                ConversationItemViewCoordinates coordinates, int fadeOutDurationMs,
                int tileColor) {
            super(progress, coordinates.progressBarWidth, coordinates.progressBarHeight,
                    tileColor, fadeOutDurationMs);

            mRotateAnimator = ValueAnimator.ofInt(0, 10000)
                    .setDuration(res.getInteger(R.integer.ap_progress_animation_duration));
            mRotateAnimator.setInterpolator(new LinearInterpolator());
            mRotateAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mRotateAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setLevel((Integer) animation.getAnimatedValue());
                }
            });
            mFadeOutAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mRotateAnimator != null) {
                        mRotateAnimator.cancel();
                    }
                }
            });
        }

        @Override
        public boolean setVisible(boolean visible) {
            final boolean changed = super.setVisible(visible);
            if (changed) {
                if (isVisible()) {
                    if (mRotateAnimator != null) {
                        mRotateAnimator.start();
                    }
                } else {
                    // can't cancel the rotate yet-- wait for the fade-out animation to end
                }
            }
            return changed;
        }

    }

}
