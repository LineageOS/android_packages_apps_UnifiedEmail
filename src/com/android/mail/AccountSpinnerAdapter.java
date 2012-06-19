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
    private Account[] mAllAccounts = new Account[0];

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
    /**
     * Set to true to enable recent folders, false to disable.
     */
    private boolean mRecentFoldersVisible;

    /** The folder currently being viewed */
    private Folder mCurrentFolder;
    private Context mContext;

    private static final int TYPE_DEAD_HEADER = -1;
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
        if (position == 0) {
            return TYPE_DEAD_HEADER;
        }
        // First the accounts
        if (position <= mNumAccounts) {
            return TYPE_ACCOUNT;
        }
        // Then the header
        if (position == mNumAccounts + 1) {
            return TYPE_HEADER;
        }
        if (mShowAllFoldersItem && getRecentOffset(position) >= mRecentFolderList.size()) {
            return TYPE_ALL_FOLDERS;
        }
        // Finally, the recent folders.
        return TYPE_FOLDER;
    }

    /**
     * Given a position in the list, what offset does it correspond to in the Recent Folders
     * list?
     * @param position
     * @return
     */
    private final int getRecentOffset(int position) {
        return position - mNumAccounts - 2;
    }

    /**
     * Returns the position of the dead, unselectable element in the spinner.
     * @return
     */
    public final int getSpacerPosition() {
        // Return the position of the dead header, which is always at the top.
        return 0;
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
                    LogUtils.d(LOG_TAG, "findPositionOfAccount: Found needle at position %d", i);
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
    public void setAccountArray(Account[] accounts) {
        final Uri currentAccount = getCurrentAccountUri();
        mAllAccounts = accounts;
        mNumAccounts = accounts.length;
        if (!isCurrentAccountInvalid()) {
            mCurrentAccountPos = findPositionOfAccount(accounts, currentAccount);
            LogUtils.d(LOG_TAG, "setAccountArray: mCurrentAccountPos = %d", mCurrentAccountPos);
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
        mCurrentAccountPos = findPositionOfAccount(mAllAccounts, account.uri);
        LogUtils.d(LOG_TAG, "setCurrentAccount: mCurrentAccountPos = %d", mCurrentAccountPos);
        if (mCurrentAccountPos >= 0) {
            requestRecentFoldersAndRedraw();
        }
        return true;
    }

    @Override
    public int getCount() {
        // If the recent folders are visible, then one header, recent folders, plus one if the
        // "show all folders" item should be shown
        final int numFolders = mRecentFoldersVisible ?
                (1 + mRecentFolderList.size() + (mShowAllFoldersItem ? 1 : 0)) : 0;
        return 1 + mNumAccounts + numFolders;
    }

    @Override
    public Object getItem(int position) {
        switch (getType(position)){
            case TYPE_DEAD_HEADER:
                return "dead header";
            case TYPE_ACCOUNT:
                return getAccount(position);
            case TYPE_HEADER:
                return "account spinner header";
            case TYPE_ALL_FOLDERS:
                return "show all folders";
            default:
                // Return the folder at this location.
                return mRecentFolderList.get(getRecentOffset(position));
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
        return mCurrentAccount.name;
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
        ((TextView) convertView.findViewById(R.id.account_spinner_first))
            .setText(getFolderLabel());
        ((TextView) convertView.findViewById(R.id.account_spinner_second))
            .setText(getCurrentAccountName());
        populateUnreadCountView((TextView) convertView.findViewById(R.id.unread),
                getFolderUnreadCount());
        return convertView;
    }

    @Override
    public int getViewTypeCount() {
        // If recent folders are shown, then two views: Recent folders and a header, and potentially
        // one "show all folders" item.
        final int folderTypes = mRecentFoldersVisible ?  (2 + (mShowAllFoldersItem ? 1 : 0)) : 0;
        // Accounts are the type of view always shown.
        return 2 + folderTypes;
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
        // Shown in the first text view with big font.
        String bigText = "";
        // Shown in the second text view with smaller font.
        String smallText = "";
        int color = 0;
        int unreadCount = 0;
        switch (getType(position)) {
            case TYPE_DEAD_HEADER:
                convertView = mInflater.inflate(R.layout.empty, null);
                return convertView;
            case TYPE_ACCOUNT:
                // TODO(viki): Get real Inbox or Priority Inbox using the URI. Remove ugly hack.
                bigText = "Inbox";
                smallText = getAccountFolder(position);
                color = getAccountColor(position);
                break;
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
            case TYPE_FOLDER:
                final Folder folder = mRecentFolderList.get(getRecentOffset(position));
                bigText = folder.name;
                unreadCount = folder.unreadCount;
                break;
            case TYPE_ALL_FOLDERS:
                bigText = mContext.getResources().getString(R.string.show_all_folders);
                break;
        }
        convertView = mInflater.inflate(R.layout.account_switch_spinner_dropdown_item, null);
        displayOrHide(convertView, R.id.account_spinner_first, bigText);
        displayOrHide(convertView, R.id.account_spinner_second, smallText);

        final View colorView = convertView.findViewById(R.id.account_spinner_color);
        if (color != 0) {
            colorView.setVisibility(View.VISIBLE);
            colorView.setBackgroundColor(color);
        } else {
            colorView.setVisibility(View.INVISIBLE);
        }
        populateUnreadCountView(
                (TextView) convertView.findViewById(R.id.account_spinner_unread_count),
                unreadCount);
        return convertView;

    }

    /**
     * Sets the text of the TextView to the given text, if it is non-empty. If the given
     * text is empty, then the TextView is hidden (set to View.GONE).
     * @param v
     * @param resourceId
     * @param toDisplay the given text
     */
    static private void displayOrHide(View v, int resourceId, String toDisplay) {
        final TextView target = (TextView) v.findViewById(resourceId);
        if (toDisplay.isEmpty()) {
            target.setVisibility(View.GONE);
            return;
        }
        target.setText(toDisplay);
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
        if (position >= mNumAccounts + 1) {
            return "";
        }
        return mAllAccounts[position - 1].name;
    }

    /**
     * Returns the color of the account (or zero, if none).
     * @param position
     * @return the folder of the account at the given position.
     */
    private int getAccountColor(int position) {
        if (position >= mNumAccounts + 1) {
            return 0;
        }
        return mAllAccounts[position - 1].color;
    }

    /**
     * Returns the account given position in the spinner.
     * @param position
     * @return the account at the given position.
     */
    private Account getAccount(int position) {
        return mAllAccounts[position - 1];
    }


    @Override
    public boolean isEnabled(int position) {
        // Don't want the user selecting the header.
        final int type = getType(position);
        return type != TYPE_DEAD_HEADER && type != TYPE_HEADER;
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

    /**
     * Disable recent folders. Can be enabled again with {@link #enableRecentFolders()}
     */
    public void disableRecentFolders() {
        if (mRecentFoldersVisible) {
            notifyDataSetChanged();
            mRecentFoldersVisible = false;
        }
    }

    /**
     * Enable recent folders. Can be disabled again with {@link #disableRecentFolders()}
     */
    public void enableRecentFolders() {
        if (!mRecentFoldersVisible) {
            notifyDataSetChanged();
            mRecentFoldersVisible = true;
        }
    }
}
