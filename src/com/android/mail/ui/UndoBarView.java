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
import android.os.SystemClock;
import android.text.Html;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.providers.Account;
import com.google.common.collect.ImmutableSet;

/**
 * A custom {@link View} that exposes an {@link UndoOperation} to the user.
 */
public class UndoBarView extends FrameLayout {
    /** The clickable view that has the 9-patch to handle focus */
    private View mUndoButtonView;
    private long mStartShowTime = -1;
    private static final long MIN_SHOW_TIME = 1500;
    public static final ImmutableSet<Integer> EXCLUDE_UNDO_OPS = ImmutableSet.of(R.id.unread,
            R.id.star);

    /** The view that contains the description of the action that just happened */
    private TextView mUndoDescriptionView;

    private OnUndoCancelListener mOnCancelListener;

    private final Runnable mDelayedHide = new Runnable() {
        @Override
        public void run() {
            internalHide(true);
        }
    };

    private Handler mHandler;
    private boolean mHidden = false;
    private Animator mUndoShowAnimation;
    private Animator mUndoHideAnimation;

    public UndoBarView(Context context) {
        this(context, null);
    }

    public UndoBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UndoBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mHandler = new Handler();
    }

    public void setOnCancelListener(UndoBarView.OnUndoCancelListener listener) {
        mOnCancelListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mUndoButtonView = findViewById(R.id.undo_button);
        mUndoDescriptionView = (TextView) findViewById(R.id.undo_descriptionview);
    }

    /**
     * Displays this view and makes it visible, binding the behavior to the
     * specified {@link UndoOperation}.
     */
    public void show(boolean animate, final Context context, UndoOperation op,
            final Account account, final AnimatedAdapter listAdapter,
            final ConversationCursor conversationCursor) {
        mUndoButtonView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View widget) {
                if (account.undoUri != null) {
                    // NOTE: We might want undo to return the messages affected, in which case
                    // the resulting cursor might be interesting...
                    // TODO: Use UIProvider.SEQUENCE_QUERY_PARAMETER to indicate the set of
                    // commands to undo
                    if (conversationCursor != null) {
                        conversationCursor.undo(context, account.undoUri);
                    }
                    if (listAdapter != null) {
                        listAdapter.setUndo(true);
                    }
                }
                hide(true);
            }
        });
        mUndoDescriptionView.setText(Html.fromHtml(op.getDescription(context)));
        mStartShowTime = SystemClock.uptimeMillis();
        mHidden = false;
        if (animate) {
            getUndoShowAnimation().start();
        } else {
            setVisibility(View.VISIBLE);
            setAlpha(1);
        }
    }

    /**
     * Hides the undo view and resets the state.
     */
    public void doHide() {
        if (!mHidden) {
            long now = SystemClock.uptimeMillis();
            long diff = now - mStartShowTime;
            if (diff >= MIN_SHOW_TIME) {
                internalHide(true);
            } else {
                mHandler.postDelayed(mDelayedHide, MIN_SHOW_TIME - diff);
            }
            mHidden = true;
        }
    }

    /**
     * Hides the undo view and resets the state.
     */
    public void hide(boolean animate) {
        if (!mHidden) {
            mHidden = true;
            internalHide(animate);
        }
    }

    private void internalHide(boolean animate) {
        if (isShown()) {
            mUndoDescriptionView.setText("");
            mUndoButtonView.setOnClickListener(null);
            // Hide undo text once it's clicked.
            if (animate) {
                getUndoHideAnimation().start();
            } else {
                setAlpha(0);
                setVisibility(View.GONE);
            }
            if (mOnCancelListener != null) {
                mOnCancelListener.onUndoCancel();
            }
        }
    }

    private Animator getUndoShowAnimation() {
        if (mUndoShowAnimation == null) {
            mUndoShowAnimation = AnimatorInflater.loadAnimator(getContext(),
                    R.animator.fade_in);
            mUndoShowAnimation.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    setVisibility(View.VISIBLE);
                }
                @Override
                public void onAnimationEnd(Animator animation) {
                }
                @Override
                public void onAnimationCancel(Animator animation) {
                }
                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
            mUndoShowAnimation.setTarget(this);
        }
        return mUndoShowAnimation;
    }

    private Animator getUndoHideAnimation() {
        if (mUndoHideAnimation == null) {
            mUndoHideAnimation = AnimatorInflater.loadAnimator(getContext(),
                    R.animator.fade_out);
            mUndoHideAnimation.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }
                @Override
                public void onAnimationRepeat(Animator animation) {
                }
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(View.GONE);
                }
                @Override
                public void onAnimationCancel(Animator animation) {
                }
            });
            mUndoHideAnimation.setTarget(this);
        }
        return mUndoHideAnimation;
    }

    /**
     * Implemented by objects that need to know when the undo bar is cancelled.
     */
    // TODO: rename this interface
    public interface OnUndoCancelListener {
        public void onUndoCancel();
    }

    public boolean isEventInUndo(MotionEvent event) {
        if (!isShown()) {
            return false;
        }
        int[] xy = new int[2];
        float x = event.getX();
        float y = event.getY();
        getLocationOnScreen(xy);
        return (x > xy[0] && x < (xy[0] + getWidth()) && y > xy[1] && y < xy[1] + getHeight());
    }

    /**
     * Classes that can undo an operation should implement this interface.
     */
    public interface UndoListener {
        public void onUndoAvailable(UndoOperation undoOp);
    }
}
