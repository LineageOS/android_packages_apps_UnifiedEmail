package com.android.mail.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.mail.R;

/**
 * Procedurally-drawn version of a horizontal indeterminate progress bar. Draws faster and more
 * frequently (by making use of the animation timer), requires minimal memory overhead, and allows
 * some configuration via attributes:
 * <ul>
 * <li>barColor (color attribute for the bar's solid color)
 * <li>barHeight (dimension attribute for the height of the solid progress bar)
 * <li>detentWidth (dimension attribute for the width of each transparent detent in the bar)
 * </ul>
 * <p>
 * This progress bar has no intrinsic height, so you must declare it with one explicitly. (It will
 * use the given height as the bar's shadow height.)
 */
public class ButteryProgressBar extends View {

    private final GradientDrawable mShadow;
    private final ValueAnimator mAnimator;

    private final Paint mPaint = new Paint();

    final int mBarColor;
    final int mSolidBarHeight;
    final int mSolidBarDetentWidth;

    private static final int SEGMENT_COUNT = 3;

    public ButteryProgressBar(Context c) {
        this(c, null);
    }

    public ButteryProgressBar(Context c, AttributeSet attrs) {
        super(c, attrs);

        final TypedArray ta = c.obtainStyledAttributes(attrs, R.styleable.ButteryProgressBar);
        try {
            mBarColor = ta.getColor(R.styleable.ButteryProgressBar_barColor, 0);
            mSolidBarHeight = ta.getDimensionPixelSize(
                    R.styleable.ButteryProgressBar_barHeight, 0);
            mSolidBarDetentWidth = ta.getDimensionPixelSize(
                    R.styleable.ButteryProgressBar_detentWidth, 0);
        } finally {
            ta.recycle();
        }

        mAnimator = new ValueAnimator();
        mAnimator.setFloatValues(1.0f, 2.0f);
        mAnimator.setDuration(300);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setInterpolator(new ExponentialInterpolator());
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                invalidate();
            }

        });

        mPaint.setColor(mBarColor);

        mShadow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{(mBarColor & 0x00ffffff) | 0x22000000, 0});
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            mShadow.setBounds(0, mSolidBarHeight, getWidth(), getHeight() - mSolidBarHeight);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mAnimator.isStarted()) {
            return;
        }

        mShadow.draw(canvas);

        final float val = (Float) mAnimator.getAnimatedValue();

        final int totalW = getWidth();
        int w = totalW;
        float l = val * w;
        // segments are spaced at half-width, quarter, eighth (powers-of-two). to maintain a smooth
        // transition between segments, we used a power-of-two interpolator.
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            w = totalW >> (i + 1);
            l = val * w;
            canvas.drawRect(l + mSolidBarDetentWidth, 0, l * 2, mSolidBarHeight, mPaint);
        }
        canvas.drawRect(0, 0, l, mSolidBarHeight, mPaint);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (visibility == VISIBLE) {
            start();
        } else {
            stop();
        }
    }

    private void start() {
        if (mAnimator == null) {
            return;
        }
        mAnimator.start();
    }

    private void stop() {
        if (mAnimator == null) {
            return;
        }
        mAnimator.cancel();
    }

    private static class ExponentialInterpolator implements Interpolator {

        @Override
        public float getInterpolation(float input) {
            return (float) Math.pow(2.0, input) - 1;
        }

    }

}
