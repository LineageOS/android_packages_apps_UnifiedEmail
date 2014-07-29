/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.ui;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.mail.R;

/**
 * A custom {@link View} that exposes an action to the user.
 */
public class ActionableToastBar extends FrameLayout {

    private boolean mHidden = false;
    private Animator mShowAnimation;
    private Animator mHideAnimation;
    private final Runnable mRunnable;
    private final Handler mFadeOutHandler;

    /** How long toast will last in ms */
    private static final long TOAST_LIFETIME = 15*1000L;

    /** The view that contains the description when laid out as a single line. */
    private TextView mSingleLineDescriptionView;

    /** The view that contains the text for the action button when laid out as a single line. */
    private TextView mSingleLineActionView;

    /** The view that contains the description when laid out as a multiple lines;
     * always <tt>null</tt> in two-pane layouts. */
    private TextView mMultiLineDescriptionView;

    /** The view that contains the text for the action button when laid out as a multiple lines;
     * always <tt>null</tt> in two-pane layouts. */
    private TextView mMultiLineActionView;

    private ToastBarOperation mOperation;

    public ActionableToastBar(Context context) {
        this(context, null);
    }

    public ActionableToastBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionableToastBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mFadeOutHandler = new Handler();
        mRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mHidden) {
                    hide(true, false /* actionClicked */);
                }
            }
        };
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSingleLineDescriptionView = (TextView) findViewById(R.id.description_text);
        mSingleLineActionView = (TextView) findViewById(R.id.action_text);
        mMultiLineDescriptionView = (TextView) findViewById(R.id.multiline_description_text);
        mMultiLineActionView = (TextView) findViewById(R.id.multiline_action_text);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final boolean showAction = !TextUtils.isEmpty(mSingleLineActionView.getText());

        // configure the UI assuming the description fits on a single line
        setVisibility(false /* multiLine */, showAction);

        // measure the view and its content
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // if the description does not fit, switch to multi line display if one is present
        final boolean descriptionIsMultiLine = mSingleLineDescriptionView.getLineCount() > 1;
        final boolean haveMultiLineView = mMultiLineDescriptionView != null;
        if (descriptionIsMultiLine && haveMultiLineView) {
            setVisibility(true /* multiLine */, showAction);

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /**
     * Displays the toast bar and makes it visible. Allows the setting of
     * parameters to customize the display.
     * @param listener Performs some action when the action button is clicked.
     *                 If the {@link ToastBarOperation} overrides
     *                 {@link ToastBarOperation#shouldTakeOnActionClickedPrecedence()}
     *                 to return <code>true</code>, the
     *                 {@link ToastBarOperation#onActionClicked(android.content.Context)}
     *                 will override this listener and be called instead.
     * @param descriptionText a description text to show in the toast bar
     * @param actionTextResourceId resource ID for the text to show in the action button
     * @param replaceVisibleToast if true, this toast should replace any currently visible toast.
     *                            Otherwise, skip showing this toast.
     * @param op the operation that corresponds to the specific toast being shown
     */
    public void show(final ActionClickedListener listener, final CharSequence descriptionText,
                     @StringRes final int actionTextResourceId, final boolean replaceVisibleToast,
                     final ToastBarOperation op) {
        if (!mHidden && !replaceVisibleToast) {
            return;
        }

        // Remove any running delayed animations first
        mFadeOutHandler.removeCallbacks(mRunnable);

        mOperation = op;

        setActionClickListener(new OnClickListener() {
            @Override
            public void onClick(View widget) {
                if (op.shouldTakeOnActionClickedPrecedence()) {
                    op.onActionClicked(getContext());
                } else {
                    listener.onActionClicked(getContext());
                }
                hide(true /* animate */, true /* actionClicked */);
            }
        });

        setDescriptionText(descriptionText);
        setActionText(actionTextResourceId);

        mHidden = false;
        getShowAnimation().start();

        // Set up runnable to execute hide toast once delay is completed
        mFadeOutHandler.postDelayed(mRunnable, TOAST_LIFETIME);
    }

    public ToastBarOperation getOperation() {
        return mOperation;
    }

    /**
     * Hides the view and resets the state.
     */
    public void hide(boolean animate, boolean actionClicked) {
        mHidden = true;
        mFadeOutHandler.removeCallbacks(mRunnable);
        if (getVisibility() == View.VISIBLE) {
            setDescriptionText("");
            setActionClickListener(null);
            // Hide view once it's clicked.
            if (animate) {
                getHideAnimation().start();
            } else {
                setAlpha(0);
                setVisibility(View.GONE);
            }

            if (!actionClicked && mOperation != null) {
                mOperation.onToastBarTimeout(getContext());
            }
        }
    }

    public boolean isAnimating() {
        return mShowAnimation != null && mShowAnimation.isStarted();
    }

    @Override
    public void onDetachedFromWindow() {
        mFadeOutHandler.removeCallbacks(mRunnable);
        super.onDetachedFromWindow();
    }

    public boolean isEventInToastBar(MotionEvent event) {
        if (!isShown()) {
            return false;
        }
        int[] xy = new int[2];
        float x = event.getX();
        float y = event.getY();
        getLocationOnScreen(xy);
        return (x > xy[0] && x < (xy[0] + getWidth()) && y > xy[1] && y < xy[1] + getHeight());
    }

    private Animator getShowAnimation() {
        if (mShowAnimation == null) {
            mShowAnimation = AnimatorInflater.loadAnimator(getContext(), R.anim.fade_in);
            mShowAnimation.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    setVisibility(View.VISIBLE);
                }
                @Override
                public void onAnimationEnd(Animator animation) { }
                @Override
                public void onAnimationCancel(Animator animation) { }
                @Override
                public void onAnimationRepeat(Animator animation) { }
            });
            mShowAnimation.setTarget(this);
        }
        return mShowAnimation;
    }

    private Animator getHideAnimation() {
        if (mHideAnimation == null) {
            mHideAnimation = AnimatorInflater.loadAnimator(getContext(), R.anim.fade_out);
            mHideAnimation.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) { }
                @Override
                public void onAnimationRepeat(Animator animation) { }
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(View.GONE);
                }
                @Override
                public void onAnimationCancel(Animator animation) { }
            });
            mHideAnimation.setTarget(this);
        }
        return mHideAnimation;
    }

    /**
     * If the View requires multiple lines to fully display the toast description then make the
     * multi-line view visible and hide the single line view; otherwise vice versa. If the action
     * text is present, display it, otherwise hide it.
     *
     * @param multiLine <tt>true</tt> if the View requires multiple lines to display the toast
     * @param showAction <tt>true</tt> if the action text is present and should be shown
     */
    private void setVisibility(boolean multiLine, boolean showAction) {
        mSingleLineDescriptionView.setVisibility(!multiLine ? View.VISIBLE : View.GONE);
        mSingleLineActionView.setVisibility(!multiLine && showAction ? View.VISIBLE : View.GONE);
        if (mMultiLineDescriptionView != null) {
            mMultiLineDescriptionView.setVisibility(multiLine ? View.VISIBLE : View.GONE);
        }
        if (mMultiLineActionView != null) {
            mMultiLineActionView.setVisibility(multiLine && showAction ? View.VISIBLE : View.GONE);
        }
    }

    private void setDescriptionText(CharSequence description) {
        mSingleLineDescriptionView.setText(description);
        if (mMultiLineDescriptionView != null) {
            mMultiLineDescriptionView.setText(description);
        }
    }

    private void setActionText(@StringRes int actionTextResourceId) {
        if (actionTextResourceId == 0) {
            mSingleLineActionView.setText("");
            if (mMultiLineActionView != null) {
                mMultiLineActionView.setText("");
            }
        } else {
            mSingleLineActionView.setText(actionTextResourceId);
            if (mMultiLineActionView != null) {
                mMultiLineActionView.setText(actionTextResourceId);
            }
        }
    }

    private void setActionClickListener(OnClickListener listener) {
        mSingleLineActionView.setOnClickListener(listener);

        if (mMultiLineActionView != null) {
            mMultiLineActionView.setOnClickListener(listener);
        }
    }

    /**
     * Classes that wish to perform some action when the action button is clicked
     * should implement this interface.
     */
    public interface ActionClickedListener {
        public void onActionClicked(Context context);
    }
}