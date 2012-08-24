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

import android.animation.ObjectAnimator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Html;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationItemViewCoordinates;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.google.common.collect.ImmutableList;

public class LeaveBehindItem extends FrameLayout implements OnClickListener,
    SwipeableItemView {

    private ToastBarOperation mUndoOp;
    private Account mAccount;
    private AnimatedAdapter mAdapter;
    private ConversationCursor mConversationCursor;
    private TextView mText;
    private View mSwipeableContent;
    private static int sShrinkAnimationDuration = -1;
    private static int sFadeInAnimationDuration = -1;

    public LeaveBehindItem(Context context) {
        this(context, null);
    }

    public LeaveBehindItem(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public LeaveBehindItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (sShrinkAnimationDuration == -1) {
            sShrinkAnimationDuration = context.getResources().getInteger(
                    R.integer.shrink_animation_duration);
            sFadeInAnimationDuration = context.getResources().getInteger(
                    R.integer.fade_in_animation_duration);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.swipeable_content:
                if (mAccount.undoUri != null) {
                    // NOTE: We might want undo to return the messages affected,
                    // in which case the resulting cursor might be interesting...
                    // TODO: Use UIProvider.SEQUENCE_QUERY_PARAMETER to indicate
                    // the set of commands to undo
                    mAdapter.clearLeaveBehind(getConversationId());
                    mAdapter.setSwipeUndo(true);
                    mConversationCursor.undo(getContext(), mAccount.undoUri);
                }
                break;
            case R.id.undo_descriptionview:
                // Essentially, makes sure that tapping description view doesn't highlight
                // either the undo button icon or text.
                break;
        }
    }

    public void bindOperations(int position, Account account, AnimatedAdapter adapter,
            ToastBarOperation undoOp, Conversation target, Folder folder) {
        mUndoOp = undoOp;
        mAccount = account;
        mAdapter = adapter;
        mConversationCursor = (ConversationCursor) adapter.getCursor();
        setData(target);
        mSwipeableContent = findViewById(R.id.swipeable_content);
        // Listen on swipeable content so that we can show both the undo icon
        // and button text as selected since they set duplicateParentState to true
        mSwipeableContent.setOnClickListener(this);
        mText = ((TextView) findViewById(R.id.undo_descriptionview));
        mText.setText(Html.fromHtml(mUndoOp
                .getSingularDescription(getContext(), folder)));
        mText.setOnClickListener(this);
    }

    public void commit() {
        if (mConversationCursor != null) {
            mConversationCursor.delete(getContext(), ImmutableList.of(getData()));
        }
    }

    @Override
    public void dismiss() {
        if (mAdapter != null) {
            mAdapter.fadeOutLeaveBehindItems();
            mAdapter.notifyDataSetChanged();
        }
    }

    public long getConversationId() {
        return getData().id;
    }

    @Override
    public View getSwipeableView() {
        return mSwipeableContent;
    }

    @Override
    public boolean canChildBeDismissed() {
        return true;
    }

    public LeaveBehindData getLeaveBehindData() {
        return new LeaveBehindData(getData(), mUndoOp);
    }

    public class LeaveBehindData implements Parcelable {
        ToastBarOperation op;
        Conversation data;

        public LeaveBehindData(Conversation conv, ToastBarOperation undoOp) {;
            op = undoOp;
            data = conv;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel arg, int flags) {
            arg.writeParcelable(op, 0);
            arg.writeParcelable(data, 0);
        }

        private LeaveBehindData(Parcel arg) {
            this((Conversation) arg.readParcelable(null),
                    (ToastBarOperation) arg.readParcelable(null));
        }

        public final Creator<LeaveBehindData> CREATOR = new Creator<LeaveBehindData>() {

            @Override
            public LeaveBehindData createFromParcel(Parcel source) {
                return new LeaveBehindData(source);
            }

            @Override
            public LeaveBehindData[] newArray(int size) {
                return new LeaveBehindData[size];
            }

        };
    }

    private Conversation mData;
    private int mAnimatedHeight = -1;
    private int mWidth;
    private boolean mAnimating;
    private boolean mFadingInText;

    /**
     * Start the animation on an animating view.
     * @param item the conversation to animate
     * @param listener the method to call when the animation is done
     * @param undo true if an operation is being undone. We animate the item
     *            away during delete. Undoing populates the item.
     */
    public void startAnimation(ViewMode viewMode, AnimatorListener listener) {
        if (!mAnimating) {
            mAnimating = true;
            int minHeight = ConversationItemViewCoordinates.getMinHeight(getContext(), viewMode);
            setMinimumHeight(minHeight);
            final int start = minHeight;
            final int end = 0;
            ObjectAnimator height = ObjectAnimator.ofInt(this, "animatedHeight", start, end);
            mAnimatedHeight = start;
            mWidth = getMeasuredWidth();
            mSwipeableContent.setVisibility(View.GONE);
            height.setInterpolator(new DecelerateInterpolator(2.0f));
            height.addListener(listener);
            height.setDuration(sShrinkAnimationDuration);
            height.start();
        }
    }

    public void startFadeInAnimation() {
        if (!mFadingInText) {
            mFadingInText = true;
            final float start = 0;
            final float end = 1.0f;
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(mText, "alpha", start, end);
            fadeIn.setInterpolator(new DecelerateInterpolator(2.0f));
            fadeIn.setDuration(sFadeInAnimationDuration);
            fadeIn.start();
        }
    }

    public void setData(Conversation conversation) {
        mData = conversation;
    }

    public Conversation getData() {
        return mData;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mAnimatedHeight != -1) {
            setMeasuredDimension(mWidth, mAnimatedHeight);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        return;
    }

    // Used by animator
    @SuppressWarnings("unused")
    public void setAnimatedHeight(int height) {
        mAnimatedHeight = height;
        requestLayout();
    }

    /**
     * We are in a state where we can't afford the alpha fade in, so just show the text.
     */
    public void showTextImmediately() {
        // Fake that we are already fading it in so animations get ignored.
        mFadingInText = true;
    }
}
