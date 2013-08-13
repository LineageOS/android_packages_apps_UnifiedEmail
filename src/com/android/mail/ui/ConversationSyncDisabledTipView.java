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
import android.content.Intent;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.preferences.AccountPreferences;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * A tip displayed on top of conversation view to indicate that Gmail sync is
 * currently disabled on this account.
 */
public class ConversationSyncDisabledTipView extends FrameLayout
        implements ConversationSpecialItemView, SwipeableItemView {

    private static final String LOG_TAG = LogTag.getLogTag();

    private static int sScrollSlop = 0;
    private static int sShrinkAnimationDuration;

    private Account mAccount = null;
    private final MailPrefs mMailPrefs;
    private AccountPreferences mAccountPreferences;
    private AnimatedAdapter mAdapter;

    private View mSwipeableContent;
    private TextView mText1;
    private TextView mText2;
    private final OnClickListener mAutoSyncOffTextClickedListener;
    private final OnClickListener mAccountSyncOffTextClickedListener;

    private int mAnimatedHeight = -1;
    private boolean mAcceptUserTaps = false;

    private int mReasonSyncOff = ReasonSyncOff.NONE;

    public interface ReasonSyncOff {
        // Background sync is enabled for current account, do not display this tip
        public static final int NONE = 0;
        // Global auto-sync (affects all apps and all accounts) is turned off
        public static final int AUTO_SYNC_OFF = 1;
        // Global auto-sync is on, but Gmail app level sync is disabled for this particular account
        public static final int ACCOUNT_SYNC_OFF = 2;
        // Auto-sync is enabled at both device and account level, but device is in airplane mode
        public static final int AIRPLANE_MODE_ON = 3;
    }

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

        mMailPrefs = MailPrefs.get(context);

        mAutoSyncOffTextClickedListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                openGlobalAutoSyncSettingDialog();
            }
        };

        mAccountSyncOffTextClickedListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Link to account level settings instead of top level settings.
                Utils.showSettings(getContext(), mAccount);
            }
        };
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

        mText1 = (TextView) findViewById(R.id.text_line1);
        mText2 = (TextView) findViewById(R.id.text_line2);

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

        // TODO: do not show this message for folders/labels that are not set to sync.
        // We need a solution that works for both Gmail and Email.

        setReasonSyncOff(calculateReasonSyncOff(
                getContext(), mMailPrefs, mAccount, mAccountPreferences));

        if (mReasonSyncOff != ReasonSyncOff.NONE) {
            LogUtils.i(LOG_TAG, "Sync is off with reason %d", mReasonSyncOff);
        }

        switch (mReasonSyncOff) {
            case ReasonSyncOff.AUTO_SYNC_OFF:
                return (mMailPrefs.getNumOfDismissesForAutoSyncOff() == 0);
            case ReasonSyncOff.ACCOUNT_SYNC_OFF:
                return (mAccountPreferences.getNumOfDismissesForAccountSyncOff() == 0);
            case ReasonSyncOff.AIRPLANE_MODE_ON:
                return (mMailPrefs.getNumOfDismissesForAirplaneModeOn() == 0);
            default:
                return false;
        }
    }

    public static int calculateReasonSyncOff(Context context, MailPrefs mailPrefs,
            Account account, AccountPreferences accountPreferences) {
        if (!ContentResolver.getMasterSyncAutomatically()) {
            // Global sync is turned off
            accountPreferences.resetNumOfDismissesForAccountSyncOff();
            mailPrefs.resetNumOfDismissesForAirplaneModeOn();
            return ReasonSyncOff.AUTO_SYNC_OFF;
        } else {
            // Global sync is on, clear the number of times users has dismissed this
            // warning so that next time global sync is off, warning gets displayed again.
            mailPrefs.resetNumOfDismissesForAutoSyncOff();

            // Now check for whether account level sync is on/off.
            // Not sure why directly passing mAccount to ContentResolver doesn't just work.
            android.accounts.Account acct = new android.accounts.Account(
                    account.name, account.type);
            if (!ContentResolver.getSyncAutomatically(acct, account.syncAuthority)) {
                // Account level sync is off
                mailPrefs.resetNumOfDismissesForAirplaneModeOn();
                return ReasonSyncOff.ACCOUNT_SYNC_OFF;
            } else {
                // Account sync is on, clear the number of times users has dismissed this
                // warning so that next time sync is off, warning gets displayed again.
                accountPreferences.resetNumOfDismissesForAccountSyncOff();

                // Now check for whether airplane mode is on
                if (Utils.isAirplaneModeOn(context)) {
                    return ReasonSyncOff.AIRPLANE_MODE_ON;
                } else {
                    mailPrefs.resetNumOfDismissesForAirplaneModeOn();
                    return ReasonSyncOff.NONE;
                }
            }
        }
    }

    private void setReasonSyncOff(int reason) {
        if (mReasonSyncOff != reason) {
            mReasonSyncOff = reason;
            switch (mReasonSyncOff) {
                case ReasonSyncOff.AUTO_SYNC_OFF:
                    mText1.setText(R.string.auto_sync_off);
                    mText2.setClickable(true);
                    mText2.setVisibility(View.VISIBLE);
                    mText2.setOnClickListener(mAutoSyncOffTextClickedListener);
                    break;
                case ReasonSyncOff.ACCOUNT_SYNC_OFF:
                    mText1.setText(R.string.account_sync_off);
                    mText2.setClickable(true);
                    mText2.setVisibility(View.VISIBLE);
                    mText2.setOnClickListener(mAccountSyncOffTextClickedListener);
                    break;
                case ReasonSyncOff.AIRPLANE_MODE_ON:
                    mText1.setText(R.string.airplane_mode_on);
                    mText2.setClickable(false);
                    mText2.setVisibility(View.GONE);
                    break;
                default:
                    // Doesn't matter what mText is since this view is not displayed
            }
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
        switch (mReasonSyncOff) {
            case ReasonSyncOff.AUTO_SYNC_OFF:
                mMailPrefs.incNumOfDismissesForAutoSyncOff();
                break;
            case ReasonSyncOff.ACCOUNT_SYNC_OFF:
                mAccountPreferences.incNumOfDismissesForAccountSyncOff();
                break;
            case ReasonSyncOff.AIRPLANE_MODE_ON:
                mMailPrefs.incNumOfDismissesForAirplaneModeOn();
                break;
        }
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

    private void openGlobalAutoSyncSettingDialog() {
        final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.putExtra(Settings.EXTRA_AUTHORITIES, new String[] {mAccount.syncAuthority});
        getContext().startActivity(intent);
    }
}
