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

package com.android.mail;

import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;

import java.util.ArrayList;

/**
 * This class is supposed to have the same thing that the Gmail ConversationListContext
 * contained. For now, it has no implementation at all. The goal is to bring over functionality
 * as required.
 *
 * Original purpose:
 * An encapsulation over a request to a list of conversations and the various states around it.
 * This includes the folder the user selected to view the list, or the search query for the
 * list, etc.
 */
public class ConversationListContext {
    private static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_FOLDER = "folder";
    private static final String EXTRA_SEARCH_QUERY = "query";

    /**
     * A matcher for data URI's that specify conversation list info.
     */
    private static final UriMatcher sUrlMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    /**
     * The account for whom we are showing a list
     */
    public final Account mAccount;
    /**
     * The folder whose conversations we are displaying, if any.
     */
    public final Folder mFolder;
    /**
     * The search query whose results we are displaying, if any.
     */
    public final String mSearchQuery;

    // Tokenized search terms for search queries.
    private ArrayList<String> mSearchTerms;

    static {
        // Get the real authority here.
        // TODO(viki): Get the real authority, and the real URI matcher!!!
        sUrlMatcher.addURI(UIProvider.AUTHORITY, "account/*/folder/*", 0);
    }

    /**
     * De-serializes a context from a bundle.
     */
    public static ConversationListContext forBundle(Bundle bundle) {
        // The account is created here as a new object. This is probably not the best thing to do.
        // We should probably be reading an account instance from our controller.
        Account account = bundle.getParcelable(EXTRA_ACCOUNT);
        Folder folder = bundle.getParcelable(EXTRA_FOLDER);
        return new ConversationListContext(account, bundle.getString(EXTRA_SEARCH_QUERY), folder);
    }

    /**
     * Builds a context for a view to a Gmail folder. Note that folder may be null, in which case
     * the context defaults to a view of the inbox.
     */
    public static ConversationListContext forFolder(
            Context context, Account account, Folder folder) {
        // Mock stuff for now.
        return new ConversationListContext(account, null, folder);
    }


    public static ConversationListContext forFolder(Context mContext, Account mAccount2,
            String inboxFolder) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Resolves an intent and builds an appropriate context for it.
     */
    public static ConversationListContext forIntent(Context context, Account callerAccount,
            Intent intent) {
        Account account = callerAccount;
        String action = intent.getAction();
        // TODO(viki): Implement the other intents: Intent.SEARCH,
        // Intent.PROVIDER_CHANGED.
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            // TODO(viki): Look through the URI to find the account and the
            // folder.
        }
        Folder folder = (Folder) intent.getParcelableExtra(EXTRA_FOLDER);
        if (folder == null) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(Uri.parse(callerAccount.folderListUri),
                        UIProvider.FOLDERS_PROJECTION, null, null, null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    folder = new Folder(cursor);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

        }
        return forFolder(context, account, folder);
    }

    /**
     * Internal constructor
     *
     * To create a class, use the static {@link #forIntent} or {@link #forBundle(Bundle)} method.
     * @param account
     * @param searchQuery
     * @param folder
     */
    private ConversationListContext(Account account, String searchQuery, Folder folder) {
        mAccount = account;
        mSearchQuery = searchQuery;
        mFolder = folder;
    }

    /**
     * Returns true if the current list is showing search results
     * @return true if list is showing search results. False otherwise
     */
    public boolean isSearchResult() {
        return false;
    }

    /**
     * Serializes the context to a bundle.
     */
    public Bundle toBundle() {
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_ACCOUNT, mAccount);
        result.putString(EXTRA_SEARCH_QUERY, mSearchQuery);
        result.putParcelable(EXTRA_FOLDER, mFolder);
        return result;
    }
}
