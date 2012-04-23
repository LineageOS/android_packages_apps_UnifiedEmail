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
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.ViewGroup;

import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.ConversationListCallbacks;
import com.android.mail.ui.ConversationViewFragment;
import com.android.mail.utils.FragmentStatePagerAdapter2;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

public class ConversationPagerAdapter extends FragmentStatePagerAdapter2 {

    private final DataSetObserver mListObserver = new ListObserver();
    private ConversationListCallbacks mListController;
    private Cursor mCursor;
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

    private static final String LOG_TAG = new LogUtils().getLogTag();

    public ConversationPagerAdapter(FragmentManager fm, Account account, Folder folder,
            Conversation initialConversation) {
        super(fm, false /* enableSavedStates */);
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
        return mSingletonMode || mCursor == null;
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
            if (!mCursor.moveToPosition(position)) {
                LogUtils.wtf(LOG_TAG, "unable to seek to ConversationCursor pos=%d (%s)", position,
                        mCursor);
                return null;
            }
            // TODO: switch to something like MessageCursor or AttachmentCursor
            // to re-use these models
            c = new Conversation(mCursor);
            c.position = position;
        }
        final Fragment f = ConversationViewFragment.newInstance(mCommonFragmentArgs, c);
        LogUtils.d(LOG_TAG, "IN PagerAdapter.getItem, frag=%s subj=%s", f, c.subject);
        return f;
    }

    @Override
    public int getCount() {
        return (isSingletonMode()) ? 1 : mCursor.getCount();
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

        // TODO: implement this to show "1 of 123" or whatever
        // maybe when the position is not the pager's current position, this could
        // return "newer" or "older"?

        return null;
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

    public void swapCursor(Cursor listCursor) {
        mCursor = listCursor;
        notifyDataSetChanged();
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

        final boolean networkWasEnabled = Utils.disableConversationCursorNetworkAccess(mCursor);

        int result = POSITION_NONE;
        int pos = -1;
        while (mCursor.moveToPosition(++pos)) {
            final long id = mCursor.getLong(UIProvider.CONVERSATION_ID_COLUMN);
            if (conv.id == id) {
                LogUtils.d(LOG_TAG, "pager adapter found repositioned convo '%s' at pos=%d",
                        conv.subject, pos);
                result = pos;
                break;
            }
        }

        if (networkWasEnabled) {
            Utils.enableConversationCursorNetworkAccess(mCursor);
        }

        return result;
    }

    public void setListController(ConversationListCallbacks listController) {
        if (mListController != null) {
            mListController.unregisterConversationListObserver(mListObserver);
        }
        mListController = listController;
        if (mListController != null) {
            mListController.registerConversationListObserver(mListObserver);

            swapCursor(mListController.getConversationListCursor());
        } else {
            mCursor = null;
        }
    }

    // update the pager dataset as the Controller's cursor changes
    private class ListObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            swapCursor(mListController.getConversationListCursor());
        }
        @Override
        public void onInvalidated() {
        }
    }

}
