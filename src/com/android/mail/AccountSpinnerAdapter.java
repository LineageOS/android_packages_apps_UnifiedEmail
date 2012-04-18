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
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.RecentFolderList;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import java.util.ArrayList;

/**
 * An adapter to return the list of accounts and folders for the Account Spinner.
 * This class keeps the account and folder information and returns appropriate views.
 */
public class AccountSpinnerAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    /**
     * The position of the current account being viewed as an index into the mAccounts array.
     */
    private int mCurrentAccountPos = -1;
    /**
     * The position of the current account being viewed.
     */
    private Account mCurrentAccount = null;
    /**
     * Total number of accounts.
     */
    private int mNumAccounts = 0;
    /**
     * Array of all the accounts on the device.
     */
    private Account[] mAccounts = new Account[0];

    /**
     *  The name of the account is the 2nd column in {@link UIProvider#ACCOUNTS_PROJECTION}
     */
    static final int NAME_COLUMN = 2;
    /**
     * An object that provides a collection of recent folders, per account.
     */
    private final RecentFolderList mRecentFolders;
    /**
     * The actual collection of sorted recent folders obtained from {@link #mRecentFolders}
     */
    private ArrayList<Folder> mRecentFolderList = new ArrayList<Folder>();
    /**
     * Boolean indicating whether the "Show All Folders" items should be shown
     */
    private final boolean mShowAllFoldersItem;

    /** The folder currently being viewed */
    private Folder mCurrentFolder;
    private Context mContext;

    public static final int TYPE_ACCOUNT = 0;
    public static final int TYPE_HEADER = 1;
    public static final int TYPE_FOLDER = 2;
    public static final int TYPE_ALL_FOLDERS = 3;

    private static final String LOG_TAG = new LogUtils().getLogTag();
    /**
     * There can be three types of views: Accounts (test@android.com, fifi@example.com), folders
     * (Inbox, Outbox) or header and footer. This method returns the type of account at given
     * position in the drop down list.
     * @param position
     * @return the type of account: one of {@link #TYPE_ACCOUNT}, {@link #TYPE_HEADER}, or
     * {@link #TYPE_FOLDER}.
     */
    public int getType(int position) {
        // First the accounts
        if (position < mNumAccounts) {
            return TYPE_ACCOUNT;
        }
        // Then the header
        if (position == mNumAccounts) {
            return TYPE_HEADER;
        }
        if (mShowAllFoldersItem) {
            // The first few positions have accounts, and then the header.
            final int offset = position - mNumAccounts - 1;
            if (offset >= mRecentFolderList.size()) {
                return TYPE_ALL_FOLDERS;
            }
        }
        // Finally, the recent folders.
        return TYPE_FOLDER;
    }

    /**
     * Returns the position of the dead, unselectable element in the spinner.
     * @return
     */
    public int getSpacerPosition() {
        return mNumAccounts;
    }

    /**
     * Create a spinner adapter with the context and the list of recent folders.
     * @param context
     * @param recentFolders
     * @param showAllFolders
     */
    public AccountSpinnerAdapter(Context context, RecentFolderList recentFolders,
            boolean showAllFolders) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mRecentFolders = recentFolders;
        mShowAllFoldersItem = showAllFolders;
    }

    /**
     * Find the position of the given needle in the given array of accounts.
     * @param haystack the array of accounts to search
     * @param needle the URI of account to find
     * @return a position between 0 and haystack.length-1 if an account is found, -1 if not found.
     */
    private static int findPositionOfAccount(Account[] haystack, Uri needle) {
        if (haystack != null && haystack.length > 0 && needle != null) {
            // Need to go through the list of current accounts, and fix the
            // position.
            for (int i = 0, size = haystack.length; i < size; ++i) {
                if (haystack[i].uri.equals(needle)) {
                    LogUtils.d(LOG_TAG, "Found need at position to %d", i);
                    return i;
                }
            }
        }
        return -1;
    }
    /**
     * Set the accounts for this spinner.
     * @param accounts
     */
    public void setAccounts(Account[] accounts) {
        final Uri currentAccount = getCurrentAccountUri();
        mAccounts = accounts;
        mNumAccounts = accounts.length;
        if (!isCurrentAccountInvalid()) {
            mCurrentAccountPos = findPositionOfAccount(accounts, currentAccount);
        }
        notifyDataSetChanged();
    }

    /**
     * Set the current folder.
     * @param folder
     * @return if changed.
     */
    public boolean setCurrentFolder(Folder folder) {
        if (folder != null && folder != mCurrentFolder) {
            mCurrentFolder = folder;
            requestRecentFoldersAndRedraw();
            return true;
        }
        return false;
    }

    /**
     * Set the current account.
     * @param account
     * @return if changed.
     */
    public boolean setCurrentAccount(Account account) {
        // If the account is missing or we have no accounts array, we cannot
        // proceed.
        if (account == null) {
            return false;
        }
        if (account.uri.equals(getCurrentAccountUri())) {
            // The current account matches what is being given, get out.
            return false;
        }
        mCurrentAccount = account;
        mCurrentAccountPos = findPositionOfAccount(mAccounts, account.uri);
        LogUtils.d(LOG_TAG, "Setting the current account position to %d", mCurrentAccountPos);
        requestRecentFoldersAndRedraw();
        return true;
    }

    @Override
    public int getCount() {
        // All the accounts, plus one header, plus recent folders, plus one if the
        // "show all folders" item should be shown
        return mNumAccounts + 1 + mRecentFolderList.size() + (mShowAllFoldersItem ? 1 : 0);
    }

    @Override
    public Object getItem(int position) {
        switch (getType(position)){
            case TYPE_ACCOUNT:
                return getAccount(position);
            case TYPE_HEADER:
                return "account spinner header";
            case TYPE_ALL_FOLDERS:
                return "show all folders";
            default:
                // The first few positions have accounts, and then the header.
                final int offset = position - mNumAccounts - 1;
                // Return the folder at this location.
                return mRecentFolderList.get(offset);
        }
    }

    @Override
    public long getItemId(int position) {
        // We use the position as the ID
        return position;
    }

    private String getFolderLabel() {
        return mCurrentFolder != null ? mCurrentFolder.name : "";
    }

    private int getFolderUnreadCount() {
        return mCurrentFolder != null ? mCurrentFolder.unreadCount : 0;
    }

    /**
     * Returns whether the current account is an invalid offset into the array.
     * @return true if the current account is invalid, and false otherwise.
     */
    private boolean isCurrentAccountInvalid() {
        return mCurrentAccount == null;
    }

    private String getCurrentAccountName() {
        if (isCurrentAccountInvalid() || mCurrentAccountPos == -1) {
            return "";
        }
        return mAccounts[mCurrentAccountPos].name;
    }

    private Uri getCurrentAccountUri() {
        if (isCurrentAccountInvalid()) {
            return Uri.EMPTY;
        }
        return mCurrentAccount.uri;
    }

    // This call renders the view that will be shown in the header.
    // Since we are tracking the current folder/ account, just
    // always return what we believe that view is.
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = mInflater.inflate(R.layout.account_switch_spinner_item, null);
        ((TextView) convertView.findViewById(R.id.account_spinner_account_name))
                .setText(getCurrentAccountName());
        ((TextView) convertView.findViewById(R.id.account_spinner_folder))
                .setText(getFolderLabel());
        populateUnreadCountView((TextView) convertView.findViewById(R.id.unread),
                getFolderUnreadCount());
        return convertView;
    }

    @Override
    public int getViewTypeCount() {
        // Two views, and one header, and potentially one "show all folders" item
        return 3 + (mShowAllFoldersItem ? 1 : 0);
    }

    @Override
    public boolean hasStableIds() {
        // The account manager could add new accounts, so don't claim that the IDs are stable.
        return false;
    }

    @Override
    public boolean isEmpty() {
        // No item will be empty.
        return false;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        String textLabel = "";
        int unreadCount = 0;
        switch (getType(position)) {
            case TYPE_HEADER:
                convertView = mInflater.inflate(R.layout.account_switch_spinner_dropdown_header,
                        null);
                final String label = getCurrentAccountName();
                TextView accountLabel = ((TextView) convertView.findViewById(
                        R.id.account_spinner_header_account));
                if (accountLabel != null) {
                    accountLabel.setText(label);
                }
                return convertView;
            case TYPE_ACCOUNT:
                textLabel = getAccountFolder(position);
                break;
            case TYPE_FOLDER:
                final int offset = position - mNumAccounts - 1;
                final Folder folder = mRecentFolderList.get(offset);
                textLabel = folder.name;
                unreadCount = folder.unreadCount;
                break;
            case TYPE_ALL_FOLDERS:
                textLabel = mContext.getResources().getString(R.string.show_all_folders);
                break;
        }
        convertView = mInflater.inflate(R.layout.account_switch_spinner_dropdown_item, null);
        ((TextView) convertView.findViewById(R.id.account_spinner_accountname)).setText(textLabel);
        populateUnreadCountView(
                (TextView) convertView.findViewById(R.id.account_spinner_unread_count),
                unreadCount);
        return convertView;

    }


    private void populateUnreadCountView(TextView unreadCountView, int unreadCount) {
        unreadCountView.setText(Utils.getUnreadCountString(mContext, unreadCount));
        unreadCountView.setVisibility(unreadCount == 0 ? View.GONE : View.VISIBLE);
    }

    /**
     * Returns the name of the folder at the given position in the spinner.
     * @param position
     * @return the folder of the account at the given position.
     */
    private String getAccountFolder(int position) {
        if (position >= mNumAccounts) {
            return "";
        }
        return mAccounts[position].name;
    }

    /**
     * Returns the account given position in the spinner.
     * @param position
     * @return the account at the given position.
     */
    private Account getAccount(int position) {
        return mAccounts[position];
    }


    @Override
    public boolean isEnabled(int position) {
        // Don't want the user selecting the header.
        return (getType(position) != TYPE_HEADER);
    }

    @Override
    public boolean areAllItemsEnabled() {
        // The header is not enabled, so return false here.
        return false;
    }

    /**
     * Notify that the folder has changed.
     */
    public void onFolderUpdated(Folder folder) {
        mCurrentFolder = folder;
        notifyDataSetChanged();
    }

    /**
     * Cause a refresh of the recent folders for the current folder and redraw the spinner with
     * the new information.
     */
    public void requestRecentFoldersAndRedraw() {
        mRecentFolderList = mRecentFolders.getRecentFolderList(mCurrentFolder);
        notifyDataSetChanged();
    }
}
