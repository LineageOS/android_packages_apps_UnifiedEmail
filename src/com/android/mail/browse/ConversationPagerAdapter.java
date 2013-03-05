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

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.ViewPager;
import android.view.ViewGroup;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.AbstractConversationViewFragment;
import com.android.mail.ui.ActivityController;
import com.android.mail.ui.ConversationViewFragment;
import com.android.mail.ui.SecureConversationViewFragment;
import com.android.mail.utils.FragmentStatePagerAdapter2;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

public class ConversationPagerAdapter extends FragmentStatePagerAdapter2
        implements ViewPager.OnPageChangeListener {

    private final DataSetObserver mListObserver = new ListObserver();
    private final DataSetObserver mFolderObserver = new FolderObserver();
    private ActivityController mController;
    private final Bundle mCommonFragmentArgs;
    private final Conversation mInitialConversation;
    private final Account mAccount;
    private final Folder mFolder;
    /**
     * In singleton mode, this adapter ignores the cursor contents and size, and acts as if the
     * data set size is exactly size=1, with {@link #getDefaultConversation()} at position 0.
     */
    private boolean mSingletonMode = true;
    /**
     * Similar to singleton mode, but once enabled, detached mode is permanent for this adapter.
     */
    private boolean mDetachedMode = false;
    /**
     * Adapter methods may trigger a data set change notification in the middle of a ViewPager
     * update, but they are not safe to handle, so we have to ignore them. This will not ignore
     * pager-external updates; it's impossible to be notified of an external change during
     * an update.
     *
     * TODO: Queue up changes like this, if there ever are any that actually modify the data set.
     * Right now there are none. Such a change would have to be of the form: instantiation or
     * setPrimary somehow adds or removes items from the conversation cursor. Crazy!
     */
    private boolean mSafeToNotify;
    /**
     * Need to keep this around to look up pager title strings.
     */
    private Resources mResources;
    /**
     * This isn't great to create a circular dependency, but our usage of {@link #getPageTitle(int)}
     * requires knowing which page is the currently visible to dynamically name offscreen pages
     * "newer" and "older". And {@link #setPrimaryItem(ViewGroup, int, Object)} does not work well
     * because it isn't updated as often as {@link ViewPager#getCurrentItem()} is.
     * <p>
     * We must be careful to null out this reference when the pager and adapter are decoupled to
     * minimize dangling references.
     */
    private ViewPager mPager;
    private boolean mSanitizedHtml;

    private boolean mStopListeningMode = false;

    /**
     * After {@link #stopListening()} is called, this contains the last-known count of this adapter.
     * We keep this around and use it in lieu of the Cursor's true count until imminent destruction
     * to satisfy two opposing requirements:
     * <ol>
     * <li>The ViewPager always likes to know about all dataset changes via notifyDatasetChanged.
     * <li>Destructive changes during pager destruction (e.g. mode transition from conversation mode
     * to list mode) must be ignored, or else ViewPager will shift focus onto a neighboring
     * conversation and <b>mark it read</b>.
     * </ol>
     *
     */
    private int mLastKnownCount;

    private static final String LOG_TAG = LogTag.getLogTag();

    private static final String BUNDLE_DETACHED_MODE =
            ConversationPagerAdapter.class.getName() + "-detachedmode";

    public ConversationPagerAdapter(Resources res, FragmentManager fm, Account account,
            Folder folder, Conversation initialConversation) {
        super(fm, false /* enableSavedStates */);
        mResources = res;
        mCommonFragmentArgs = AbstractConversationViewFragment.makeBasicArgs(account, folder);
        mInitialConversation = initialConversation;
        mAccount = account;
        mFolder = folder;
        mSanitizedHtml = mAccount.supportsCapability
                (UIProvider.AccountCapabilities.SANITIZED_HTML);
    }

    public boolean matches(Account account, Folder folder) {
        return mAccount != null && mFolder != null && mAccount.matches(account)
                && mFolder.equals(folder);
    }

    public void setSingletonMode(boolean enabled) {
        if (mSingletonMode != enabled) {
            mSingletonMode = enabled;
            notifyDataSetChanged();
        }
    }

    public boolean isSingletonMode() {
        return mSingletonMode;
    }

    public boolean isDetached() {
        return mDetachedMode;
    }

    /**
     * Returns true if singleton mode or detached mode have been enabled, or if the current cursor
     * is null.
     * @param cursor the current conversation cursor (obtained through {@link #getCursor()}.
     * @return
     */
    public boolean isPagingDisabled(Cursor cursor) {
        return mSingletonMode || mDetachedMode || cursor == null;
    }

    private ConversationCursor getCursor() {
        if (mDetachedMode) {
            // In detached mode, the pager is decoupled from the cursor. Nothing should rely on the
            // cursor at this point.
            return null;
        }
        if (mController == null) {
            // Happens when someone calls setActivityController(null) on us. This is done in
            // ConversationPagerController.stopListening() to indicate that the Conversation View
            // is going away *very* soon.
            LogUtils.i(LOG_TAG, "Pager adapter has a null controller. If the conversation view"
                    + " is going away, this is fine.  Otherwise, the state is inconsistent");
            return null;
        }

        return mController.getConversationListCursor();
    }

    @Override
    public Fragment getItem(int position) {
        final Conversation c;
        final Cursor cursor = getCursor();

        if (isPagingDisabled(cursor)) {
            // cursor-less adapter is a size-1 cursor that points to mInitialConversation.
            // sanity-check
            if (position != 0) {
                LogUtils.wtf(LOG_TAG, "pager cursor is null and position is non-zero: %d",
                        position);
            }
            c = getDefaultConversation();
            c.position = 0;
        } else {
            if (!cursor.moveToPosition(position)) {
                LogUtils.wtf(LOG_TAG, "unable to seek to ConversationCursor pos=%d (%s)", position,
                        cursor);
                return null;
            }
            // TODO: switch to something like MessageCursor or AttachmentCursor
            // to re-use these models
            c = new Conversation(cursor);
            c.position = position;
        }
        final AbstractConversationViewFragment f = getConversationViewFragment(c);
        LogUtils.d(LOG_TAG, "IN PagerAdapter.getItem, frag=%s conv=%s", f, c);
        return f;
    }

    private AbstractConversationViewFragment getConversationViewFragment(Conversation c) {
        if (mSanitizedHtml) {
            return ConversationViewFragment.newInstance(mCommonFragmentArgs, c);
        } else {
            return SecureConversationViewFragment.newInstance(mCommonFragmentArgs, c);
        }
    }

    @Override
    public int getCount() {
        if (mStopListeningMode) {
            if (LogUtils.isLoggable(LOG_TAG, LogUtils.DEBUG)) {
                final Cursor cursor = getCursor();
                LogUtils.d(LOG_TAG,
                        "IN CPA.getCount stopListeningMode, returning lastKnownCount=%d."
                        + " cursor=%s real count=%s", mLastKnownCount, cursor,
                        (cursor != null) ? cursor.getCount() : "N/A");
            }
            return mLastKnownCount;
        }

        final Cursor cursor = getCursor();
        if (isPagingDisabled(cursor)) {
            LogUtils.d(LOG_TAG, "IN CPA.getCount, returning 1 (effective singleton). cursor=%s",
                    cursor);
            return 1;
        }
        return cursor.getCount();
    }

    @Override
    public int getItemPosition(Object item) {
        if (!(item instanceof AbstractConversationViewFragment)) {
            LogUtils.wtf(LOG_TAG, "getItemPosition received unexpected item: %s", item);
        }

        final AbstractConversationViewFragment fragment = (AbstractConversationViewFragment) item;
        return getConversationPosition(fragment.getConversation());
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        LogUtils.d(LOG_TAG, "IN PagerAdapter.setPrimaryItem, pos=%d, frag=%s", position,
                object);
        super.setPrimaryItem(container, position, object);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        final String title;
        final int currentPosition = mPager.getCurrentItem();
        final Cursor cursor = getCursor();
        if (isPagingDisabled(cursor)) {
            title = null;
        } else if (position == currentPosition) {
            int total = getCount();
            if (mController != null) {
                final Folder f = mController.getFolder();
                if (f != null && f.totalCount > total) {
                    total = f.totalCount;
                }
            }
            title = mResources.getString(R.string.conversation_count, position + 1, total);
        } else {
            title = mResources.getString(position < currentPosition ?
                    R.string.conversation_newer : R.string.conversation_older);
        }
        return title;
    }

    @Override
    public Parcelable saveState() {
        LogUtils.d(LOG_TAG, "IN PagerAdapter.saveState. this=%s", this);
        Bundle state = (Bundle) super.saveState(); // superclass uses a Bundle
        if (state == null) {
            state = new Bundle();
        }
        state.putBoolean(BUNDLE_DETACHED_MODE, mDetachedMode);
        return state;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
        LogUtils.d(LOG_TAG, "IN PagerAdapter.restoreState. this=%s", this);
        super.restoreState(state, loader);
        if (state != null) {
            Bundle b = (Bundle) state;
            b.setClassLoader(loader);
            mDetachedMode = b.getBoolean(BUNDLE_DETACHED_MODE);
        }
    }

    @Override
    public void startUpdate(ViewGroup container) {
        mSafeToNotify = false;
        super.startUpdate(container);
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        super.finishUpdate(container);
        mSafeToNotify = true;
    }

    @Override
    public void notifyDataSetChanged() {
        if (!mSafeToNotify) {
            LogUtils.d(LOG_TAG, "IN PagerAdapter.notifyDataSetChanged, ignoring unsafe update");
            return;
        }

        boolean notify = true;

        // If we are in detached mode, changes to the cursor are of no interest to us, but they may
        // be to parent classes.

        // when the currently visible item disappears from the dataset:
        //   if the new version of the currently visible item has zero messages:
        //     notify the list controller so it can handle this 'current conversation gone' case
        //     (by backing out of conversation mode)
        //   else
        //     'detach' the conversation view from the cursor, keeping the current item as-is but
        //     disabling swipe (effectively the same as singleton mode)
        if (mController != null && !mDetachedMode && mPager != null) {
            final Conversation currConversation = mController.getCurrentConversation();
            final int pos = getConversationPosition(currConversation);
            final Cursor cursor = getCursor();
            if (pos == POSITION_NONE && cursor != null && currConversation != null) {

                final boolean isSingletonDraft = (currConversation.getNumMessages() == 1
                        && currConversation.numDrafts() == 1);
                if (isSingletonDraft) {
                    // A single-message draft thread has gone missing from the cursor.
                    // Bail out of conversation view immediately, since there is no reason to stick
                    // around in detached mode.
                    LogUtils.i(LOG_TAG,
                            "CPA: current singleton draft conv is not in cursor, popping out. c=%s",
                            currConversation.uri);
                    mController.onConversationSelected(null, true /* inLoaderCallbacks */);
                    notify = false;
                } else {
                    // enable detached mode and do no more here. the fragment itself will figure out
                    // if the conversation is empty (using message list cursor) and back out if
                    // needed.
                    mDetachedMode = true;
                    mController.setDetachedMode();
                    LogUtils.i(LOG_TAG,
                            "CPA: current conv is gone, reverting to detached mode. c=%s",
                            currConversation.uri);
                }
            } else {
                // notify unaffected fragment items of the change, so they can re-render
                // (the change may have been to the labels for a single conversation, for example)
                final AbstractConversationViewFragment frag = (cursor == null) ? null :
                        (AbstractConversationViewFragment) getFragmentAt(pos);
                if (frag != null && cursor.moveToPosition(pos) && frag.isUserVisible()) {
                    // reload what we think is in the current position.
                    final Conversation conv = new Conversation(cursor);
                    conv.position = pos;
                    frag.onConversationUpdated(conv);
                    mController.setCurrentConversation(conv);
                }
            }
        }

        if (notify) {
            super.notifyDataSetChanged();
        }
    }

    @Override
    public void setItemVisible(Fragment item, boolean visible) {
        super.setItemVisible(item, visible);
        final AbstractConversationViewFragment fragment = (AbstractConversationViewFragment) item;
        fragment.setExtraUserVisibleHint(visible);
    }

    private Conversation getDefaultConversation() {
        Conversation c = (mController != null) ? mController.getCurrentConversation() : null;
        if (c == null) {
            c = mInitialConversation;
        }
        return c;
    }

    public int getConversationPosition(Conversation conv) {
        final ConversationCursor cursor = getCursor();
        if (isPagingDisabled(cursor)) {
            if (conv != getDefaultConversation()) {
                LogUtils.d(LOG_TAG, "unable to find conversation in singleton mode. c=%s", conv);
                return POSITION_NONE;
            }
            return 0;
        }

        // cursor is guaranteed to be non-null because isPagingDisabled() above checks for null
        // cursor.
        if (conv == null) {
            return POSITION_NONE;
        }

        int result = POSITION_NONE;
        final int pos = cursor.getConversationPosition(conv.id);
        if (pos >= 0) {
            LogUtils.d(LOG_TAG, "pager adapter found repositioned convo %s at pos=%d",
                    conv, pos);
            result = pos;
        }

        return result;
    }

    public void setPager(ViewPager pager) {
        if (mPager != null) {
            mPager.setOnPageChangeListener(null);
        }
        mPager = pager;
        if (mPager != null) {
            mPager.setOnPageChangeListener(this);
        }
    }

    public void setActivityController(ActivityController controller) {
        if (mController != null && !mStopListeningMode) {
            mController.unregisterConversationListObserver(mListObserver);
            mController.unregisterFolderObserver(mFolderObserver);
        }
        mController = controller;
        if (mController != null && !mStopListeningMode) {
            mController.registerConversationListObserver(mListObserver);
            mController.registerFolderObserver(mFolderObserver);

            notifyDataSetChanged();
        } else {
            // We're being torn down; do not notify.
            // Let the pager controller manage pager lifecycle.
        }
    }

    /**
     * See {@link ConversationPagerController#stopListening()}.
     */
    public void stopListening() {
        if (mStopListeningMode) {
            // Do nothing since we're already in stop listening mode.  This avoids repeated
            // unregister observer calls.
            return;
        }

        // disable the observer, but save off the current count, in case the Pager asks for it
        // from now until imminent destruction

        if (mController != null) {
            mController.unregisterConversationListObserver(mListObserver);
            mController.unregisterFolderObserver(mFolderObserver);
        }
        mLastKnownCount = getCount();
        mStopListeningMode = true;
        LogUtils.d(LOG_TAG, "CPA.stopListening, recording lastKnownCount=%d", mLastKnownCount);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // no-op
    }

    @Override
    public void onPageSelected(int position) {
        if (mController == null) {
            return;
        }
        final Cursor cursor = getCursor();
        if (cursor == null || !cursor.moveToPosition(position)) {
            // No valid cursor or it doesn't have the position we want. Bail.
            return;
        }
        final Conversation c = new Conversation(cursor);
        c.position = position;
        LogUtils.d(LOG_TAG, "pager adapter setting current conv: %s", c);
        mController.setCurrentConversation(c);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // no-op
    }

    // update the pager title strip as the Folder's conversation count changes
    private class FolderObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }
    }

    // update the pager dataset as the Controller's cursor changes
    private class ListObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            notifyDataSetChanged();
        }
        @Override
        public void onInvalidated() {
        }
    }

}
