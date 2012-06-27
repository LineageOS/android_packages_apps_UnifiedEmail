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
import com.android.mail.ui.ConversationListCallbacks;
import com.android.mail.ui.ConversationViewFragment;
import com.android.mail.utils.FragmentStatePagerAdapter2;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

public class ConversationPagerAdapter extends FragmentStatePagerAdapter2 {

    private final DataSetObserver mListObserver = new ListObserver();
    private ConversationListCallbacks mListController;
    private final Bundle mCommonFragmentArgs;
    private final Conversation mInitialConversation;
    /**
     * In singleton mode, this adapter ignores the cursor contents and size, and acts as if the
     * data set size is exactly size=1, with {@link #mInitialConversation} at position 0.
     */
    private boolean mSingletonMode = true;
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

    private static final String LOG_TAG = LogTag.getLogTag();

    public ConversationPagerAdapter(Resources res, FragmentManager fm, Account account,
            Folder folder, Conversation initialConversation) {
        super(fm, false /* enableSavedStates */);
        mResources = res;
        mCommonFragmentArgs = ConversationViewFragment.makeBasicArgs(account, folder);
        mInitialConversation = initialConversation;
    }

    public void setSingletonMode(boolean enabled) {
        if (mSingletonMode != enabled) {
            mSingletonMode = enabled;
            notifyDataSetChanged();
        }
    }

    public boolean isSingletonMode() {
        return mSingletonMode || getCursor() == null;
    }

    private Cursor getCursor() {
        if (mListController == null) {
            // Should never happen. It's the pager controller's responsibility to ensure the list
            // controller reference is around at least as long as the pager is active and has this
            // adapter.
            LogUtils.wtf(LOG_TAG, new Error(), "Pager adapter has an unexpected null cursor");
            return null;
        }

        return mListController.getConversationListCursor();
    }

    @Override
    public Fragment getItem(int position) {
        final Conversation c;

        if (isSingletonMode()) {
            // cursor-less adapter is a size-1 cursor that points to mInitialConversation.
            // sanity-check
            if (position != 0) {
                LogUtils.wtf(LOG_TAG, "pager cursor is null and position is non-zero: %d",
                        position);
            }
            c = mInitialConversation;
            c.position = 0;
        } else {
            final Cursor cursor = getCursor();
            if (cursor == null) {
                LogUtils.wtf(LOG_TAG, "unable to get ConversationCursor, pos=%d", position);
                return null;
            }
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
        final Fragment f = ConversationViewFragment.newInstance(mCommonFragmentArgs, c);
        LogUtils.d(LOG_TAG, "IN PagerAdapter.getItem, frag=%s subj=%s", f, c.subject);
        return f;
    }

    @Override
    public int getCount() {
        if (isSingletonMode()) {
            return 1;
        }
        final Cursor cursor = getCursor();
        if (cursor == null) {
            return 0;
        }
        return cursor.getCount();
    }

    @Override
    public int getItemPosition(Object item) {
        if (!(item instanceof ConversationViewFragment)) {
            LogUtils.wtf(LOG_TAG, "getItemPosition received unexpected item: %s", item);
        }

        final ConversationViewFragment fragment = (ConversationViewFragment) item;
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

        if (position == currentPosition) {
            title = mResources.getString(R.string.conversation_count, position + 1, getCount());
        } else {
            title = mResources.getString(position > currentPosition ?
                    R.string.conversation_newer : R.string.conversation_older);
        }
        return title;
    }

    @Override
    public Parcelable saveState() {
        LogUtils.d(LOG_TAG, "IN PagerAdapter.saveState");
        return super.saveState();
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
        LogUtils.d(LOG_TAG, "IN PagerAdapter.restoreState");
        super.restoreState(state, loader);
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
        super.notifyDataSetChanged();
    }

    @Override
    public void setItemVisible(Fragment item, boolean visible) {
        super.setItemVisible(item, visible);
        final ConversationViewFragment fragment = (ConversationViewFragment) item;
        fragment.setExtraUserVisibleHint(visible);

        if (visible && mListController != null) {
            final Conversation c = fragment.getConversation();
            LogUtils.d(LOG_TAG, "pager adapter setting current conv: %s (%s)", c.subject, item);
            mListController.setCurrentConversation(c);
        }
    }

    public int getConversationPosition(Conversation conv) {
        if (isSingletonMode()) {
            if (conv != mInitialConversation) {
                LogUtils.wtf(LOG_TAG, "unable to find conversation with null pager cursor. c=%s",
                        conv);
                return POSITION_NONE;
            }
            return 0;
        }

        final Cursor cursor = getCursor();
        if (cursor == null) {
            return POSITION_NONE;
        }

        final boolean networkWasEnabled = Utils.disableConversationCursorNetworkAccess(cursor);

        int result = POSITION_NONE;
        int pos = -1;
        while (cursor.moveToPosition(++pos)) {
            final long id = cursor.getLong(UIProvider.CONVERSATION_ID_COLUMN);
            if (conv.id == id) {
                LogUtils.d(LOG_TAG, "pager adapter found repositioned convo '%s' at pos=%d",
                        conv.subject, pos);
                result = pos;
                break;
            }
        }

        if (networkWasEnabled) {
            Utils.enableConversationCursorNetworkAccess(cursor);
        }

        return result;
    }

    public void setPager(ViewPager pager) {
        mPager = pager;
    }

    public void setListController(ConversationListCallbacks listController) {
        if (mListController != null) {
            mListController.unregisterConversationListObserver(mListObserver);
        }
        mListController = listController;
        if (mListController != null) {
            mListController.registerConversationListObserver(mListObserver);

            notifyDataSetChanged();
        } else {
            // We're being torn down; do not notify.
            // Let the pager controller manage pager lifecycle.
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
