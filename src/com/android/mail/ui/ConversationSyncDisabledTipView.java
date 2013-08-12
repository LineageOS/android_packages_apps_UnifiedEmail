/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.preferences.AccountPreferences;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.utils.Utils;

/**
 * A tip displayed on top of conversation view to indicate that Gmail sync is
 * currently disabled on this account.
 */
public class ConversationSyncDisabledTipView extends FrameLayout
        implements ConversationSpecialItemView, SwipeableItemView {

    private static int sScrollSlop = 0;
    private static int sShrinkAnimationDuration;

    private Account mAccount = null;
    private AccountPreferences mAccountPreferences;
    private AnimatedAdapter mAdapter;

    private View mSwipeableContent;
    private TextView mText;

    private int mAnimatedHeight = -1;
    private boolean mAcceptUserTaps = false;

    public ConversationSyncDisabledTipView(final Context context) {
        this(context, null);
    }

    public ConversationSyncDisabledTipView(final Context context, final AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public ConversationSyncDisabledTipView(
            final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        final Resources resources = context.getResources();

        if (sScrollSlop == 0) {
            sScrollSlop = resources.getInteger(R.integer.swipeScrollSlop);
            sShrinkAnimationDuration = resources.getInteger(
                    R.integer.shrink_animation_duration);
        }
    }

    public void bindAccount(Account account) {
        mAccount = account;
        mAccountPreferences = AccountPreferences.get(getContext(), account.name);
    }

    @Override
    public void onGetView() {
        // Do nothing
    }

    @Override
    protected void onFinishInflate() {
        mSwipeableContent = findViewById(R.id.swipeable_content);

        mText = (TextView) findViewById(R.id.text);
        mText.setText(R.string.account_sync_off);
        mText.setClickable(true);
        mText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Utils.showSettings(getContext(), mAccount);
            }
        });

        findViewById(R.id.dismiss_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    @Override
    public void onUpdate(String account, Folder folder, ConversationCursor cursor) {
        // do nothing
    }

    @Override
    public boolean getShouldDisplayInList() {
        if (mAccount == null || mAccount.syncAuthority == null) {
            return false;
        }
        boolean globalSyncAutomatically = ContentResolver.getMasterSyncAutomatically();
        // Not sure why directly passing mAccount to ContentResolver doesn't just work.
        android.accounts.Account account = new android.accounts.Account(
                mAccount.name, mAccount.type);
        if (globalSyncAutomatically &&
                ContentResolver.getSyncAutomatically(account, mAccount.syncAuthority)) {
            // Sync is on, clear the number of times users has dismissed this
            // warning so that next time sync is off, warning gets displayed again.
            mAccountPreferences.resetNumOfDismissesForAccountSyncOff();
            return false;
        } else {
            // Sync is off
            return (mAccountPreferences.getNumOfDismissesForAccountSyncOff() == 0);
        }
    }

    @Override
    public int getPosition() {
        // We want this teaser to go before the first real conversation
        // If another teaser wants position 0, we will want position 1
        return mAdapter.getPositionOffset(0);
    }

    @Override
    public void setAdapter(AnimatedAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public void bindLoaderManager(LoaderManager loaderManager) {
    }

    @Override
    public void cleanup() {
    }

    @Override
    public void onConversationSelected() {
        // DO NOTHING
    }

    @Override
    public void onCabModeEntered() {
        dismiss();
    }

    @Override
    public boolean acceptsUserTaps() {
        return mAcceptUserTaps;
    }

    @Override
    public void dismiss() {
        mAccountPreferences.incNumOfDismissesForAccountSyncOff();
        startDestroyAnimation();
    }

    @Override
    public SwipeableView getSwipeableView() {
        return SwipeableView.from(mSwipeableContent);
    }

    @Override
    public boolean canChildBeDismissed() {
        return true;
    }

    @Override
    public float getMinAllowScrollDistance() {
        return sScrollSlop;
    }

    private void startDestroyAnimation() {
        final int start = getHeight();
        final int end = 0;
        mAnimatedHeight = start;
        final ObjectAnimator heightAnimator =
                ObjectAnimator.ofInt(this, "animatedHeight", start, end);
        heightAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        heightAnimator.setDuration(sShrinkAnimationDuration);
        heightAnimator.start();
    }

    /**
     * This method is used by the animator.  It is explicitly kept in proguard.flags to prevent it
     * from being removed, inlined, or obfuscated.
     * Edit ./vendor/unbundled/packages/apps/UnifiedGmail/proguard.flags
     * In the future, we want to use @Keep
     */
    public void setAnimatedHeight(final int height) {
        mAnimatedHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (mAnimatedHeight == -1) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mAnimatedHeight);
        }
    }
}
