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

import android.app.FragmentManager;
import android.support.v4.view.ViewPager;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.ui.AbstractActivityController;
import com.android.mail.ui.ConversationListCallbacks;
import com.android.mail.ui.RestrictedActivity;
import com.android.mail.utils.LogUtils;

/**
 * A simple controller for a {@link ViewPager} of conversations.
 * <p>
 * Instead of placing a ViewPager in a Fragment that replaces the other app views, we leave a
 * ViewPager in the activity's view hierarchy at all times and have this controller manage it.
 * This allows the ViewPager to safely instantiate inner conversation fragments since it is not
 * itself contained in a Fragment (no nested fragments!).
 * <p>
 * This arrangement has pros and cons...<br>
 * pros: FragmentManager manages restoring conversation fragments, each conversation gets its own
 * LoaderManager<br>
 * cons: the activity's Controller has to specially handle show/hide conversation view,
 * conversation fragment transitions must be done manually
 * <p>
 * This controller is a small delegate of {@link AbstractActivityController} and shares its
 * lifetime.
 *
 */
public class ConversationPagerController {

    private ConversationPager mPager;
    private ConversationPagerAdapter mPagerAdapter;
    private FragmentManager mFragmentManager;
    private ConversationListCallbacks mListProxy;
    private boolean mShown;

    private static final String LOG_TAG = new LogUtils().getLogTag();

    /**
     * Enables an optimization to the PagerAdapter that causes ViewPager to initially load just the
     * target conversation, then when the conversation view signals that the conversation is loaded
     * and visible (via onConversationSeen), we switch to paged mode to load the left/right
     * adjacent conversations.
     * <p>
     * Should improve load times. It also works around an issue in ViewPager that always loads item
     * zero (with the fragment visibility hint ON) when the adapter is initially set.
     * <p>
     * However, this is disabled right now because ViewPager seems to have a bug when you enable
     * this adapter data set change when the initial current item was item #1.
     */
    private static final boolean ENABLE_SINGLETON_INITIAL_LOAD = false;

    public ConversationPagerController(RestrictedActivity activity,
            ConversationListCallbacks listProxy) {
        mFragmentManager = activity.getFragmentManager();
        mPager = (ConversationPager) activity.findViewById(R.id.conversation_pane);
        mListProxy = listProxy;
    }

    public void show(Account account, Folder folder, Conversation initialConversation) {
        if (mShown) {
            LogUtils.d(LOG_TAG, "IN CPC.show, but already shown");
            cleanup();

            // TODO: can optimize this case to shuffle the existing adapter to jump to a new
            // position if the account+folder combo are the same, but the conversation is different
        }

        mPagerAdapter = new ConversationPagerAdapter(mFragmentManager, account, folder,
                initialConversation);
        mPagerAdapter.setSingletonMode(ENABLE_SINGLETON_INITIAL_LOAD);
        mPagerAdapter.setListProxy(mListProxy);
        LogUtils.d(LOG_TAG, "IN CPC.show, adapter=%s", mPagerAdapter);

        LogUtils.d(LOG_TAG, "init pager adapter, count=%d initial=%s", mPagerAdapter.getCount(),
                initialConversation.subject);
        mPager.setAdapter(mPagerAdapter);

        if (!ENABLE_SINGLETON_INITIAL_LOAD) {
            // FIXME: unnecessary to do this on restore. setAdapter will restore current position
            final int initialPos = mPagerAdapter.getConversationPosition(initialConversation);
            LogUtils.w(LOG_TAG, "*** pager fragment init pos=%d", initialPos);
            mPager.setCurrentItem(initialPos);
        }

        mShown = true;
    }

    public void hide() {
        if (!mShown) {
            LogUtils.d(LOG_TAG, "IN CPC.hide, but already hidden");
            return;
        }
        mShown = false;

        LogUtils.d(LOG_TAG, "IN CPC.hide, clearing adapter and unregistering list observer");
        mPager.setAdapter(null);
        cleanup();
    }

    public void onDestroy() {
        // need to release resources before a configuration change kills the activity and controller
        cleanup();
    }

    private void cleanup() {
        if (mPagerAdapter != null) {
            // stop observing the conversation list
            mPagerAdapter.setListProxy(null);
            mPagerAdapter = null;
        }
    }

    public void onConversationSeen(Conversation conv) {
        // take the adapter out of singleton mode to begin loading the
        // other non-visible conversations
        if (mPagerAdapter.isSingletonMode()) {
            LogUtils.d(LOG_TAG, "IN pager adapter, finished loading primary conversation," +
                    " switching to cursor mode to load other conversations");
            mPagerAdapter.setSingletonMode(false);
        }
    }
}
