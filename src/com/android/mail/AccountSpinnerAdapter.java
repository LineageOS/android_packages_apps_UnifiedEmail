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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.RecentFolderList;
import com.android.mail.utils.Utils;

/**
 * An adapter to return the list of accounts and folders for the Account Spinner.
 * This class keeps the account and folder information and returns appropriate views.
 */
public class AccountSpinnerAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    /**
     * The current account being viewed
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
    private Folder[] mRecentFolderList = new Folder[0];

    /** The folder currently being viewed */
    private Folder mCurrentFolder;
    private Context mContext;

    /**
     * When the user selects the spinner, a dropdown list of objects is shown. Each item in the
     * dropdown list has two textviews.
     */
    private static class DropdownHolder {
        TextView folder;
        TextView unreadCount;
    }

    /**
     * After the accounts, the dropdown item is a header.
     */
    private static class HeaderHolder {
        TextView account;
    }

    /**
     * The spinner shows the name of the folder, the account name, and the unread count.
     */
    private static class ViewHolder {
        TextView folder;
        TextView account;
        TextView unread_count;
    }

    public static final int TYPE_ACCOUNT = 0;
    public static final int TYPE_HEADER = 1;
    public static final int TYPE_FOLDER = 2;

    /**
     * There can be three types of views: Accounts (test@android.com, fifi@example.com), folders
     * (Inbox, Outbox) or header and footer. This method returns the type of account at given
     * position in the drop down list.
     * @param position
     * @return the type of account: one of {@link #TYPE_ACCOUNT}, {@link #TYPE_HEADER}, or
     * {@link #TYPE_FOLDER}.
     */
    private int getType(int position) {
        // First the accounts
        if (position < mNumAccounts) {
            return TYPE_ACCOUNT;
        }
        // Then the header
        if (position == mNumAccounts) {
            return TYPE_HEADER;
        }
        // Finally, the recent folders.
        return TYPE_FOLDER;
    }

    /**
     * Create a spinner adapter with the context and the list of recent folders.
     * @param context
     * @param recentFolders
     */
    public AccountSpinnerAdapter(Context context, RecentFolderList recentFolders) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mRecentFolders = recentFolders;
    }

    /**
     * Set the accounts for this spinner.
     * @param accounts
     */
    public void setAccounts(Account[] accounts) {
        mAccounts = accounts;
        mNumAccounts = accounts.length;
        notifyDataSetChanged();
    }

    /**
     * Set the current folder.
     * @param folder
     */
    public void setCurrentFolder(Folder folder) {
        mCurrentFolder = folder;
        mRecentFolderList = mRecentFolders.getSortedArray(folder);
    }

    /**
     * Set the current account.
     * @param account
     */
    public void setCurrentAccount(Account account) {
        mCurrentAccount = account;
        mRecentFolderList = mRecentFolders.getSortedArray(mCurrentFolder);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        // All the accounts, plus one header, plus recent folders
        return mNumAccounts + 1 + mRecentFolderList.length;
    }

    @Override
    public Object getItem(int position) {
        switch (getType(position)){
            case TYPE_ACCOUNT:
                return getAccount(position);
            case TYPE_HEADER:
                return "account spinner header";
            default:
                // The first few positions have accounts, and then the header.
                final int offset = position - mNumAccounts - 1;
                // Return the name of the folder at this location.
                return mRecentFolderList[offset].name;
        }
    }

    @Override
    public long getItemId(int position) {
        // We use the position as the ID
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        // We have three different types of views: accounts, header and folders. The constants for
        // account and folder are arbitrary, and we choose numbers 0 and 1.
        switch (getType(position)) {
            case TYPE_ACCOUNT:
                return 0;
            case TYPE_HEADER:
                return AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER;
            default:
                return 1;
        }
    }

    private String getFolderLabel() {
        return mCurrentFolder != null ? mCurrentFolder.name : "";
    }

    private int getFolderUnreadCount() {
        return mCurrentFolder != null ? mCurrentFolder.unreadCount : 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String folderName = "";
        String accountName = "";
        int unreadCount = 0;
        switch (getType(position)) {
            case TYPE_ACCOUNT:
                // The default Inbox for the given account
                accountName = getAccountFolder(position);
                folderName = getFolderLabel();
                unreadCount = getFolderUnreadCount();
                break;
            case TYPE_HEADER:
                accountName = getAccountFolder(0);
                break;
            default:
                // Change the name of the current folder
                final int offset = position - mNumAccounts - 1;
                accountName = (mCurrentAccount == null) ? "" : mCurrentAccount.name;
                final Folder folder = mRecentFolderList[offset];
                folderName = folder.name;
                unreadCount = folder.unreadCount;
                break;
        }

        // Return a view with the folder on the first line and the account name on the second.
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.account_switch_spinner_item, null);
            holder = new ViewHolder();
            holder.account =
                    (TextView) convertView.findViewById(R.id.account_spinner_account_name);
            holder.folder =
                    (TextView) convertView.findViewById(R.id.account_spinner_folder);
            holder.unread_count =
                    (TextView) convertView.findViewById(R.id.unread);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.folder.setText(folderName);
        holder.account.setText(accountName);
        // Fake unread counts for now.
        holder.unread_count.setText(Utils.getUnreadCountString(mContext, unreadCount));
        // Keep the unread count logic here for the future.
        if (unreadCount == 0) {
            holder.unread_count.setVisibility(View.GONE);
        } else {
            holder.unread_count.setVisibility(View.VISIBLE);
        }
        return convertView;
    }

    @Override
    public int getViewTypeCount() {
        // Two views, and one header
        return 3;
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
                HeaderHolder header;
                if (convertView == null || !(convertView.getTag() instanceof HeaderHolder)) {
                    convertView = mInflater.inflate(
                            R.layout.account_switch_spinner_dropdown_header, null);
                    header = new HeaderHolder();
                    header.account = (TextView) convertView.findViewById(
                            R.id.account_spinner_header_account);
                    convertView.setTag(header);
                } else {
                    header = (HeaderHolder) convertView.getTag();
                }
                final String label = (mCurrentAccount == null) ? "" : mCurrentAccount.name;
                header.account.setText(label);
                return convertView;
            case TYPE_ACCOUNT:
                textLabel = getAccountFolder(position);
                break;
            case TYPE_FOLDER:
                final int offset = position - mNumAccounts - 1;
                final Folder folder = mRecentFolderList[offset];
                textLabel = folder.name;
                unreadCount = folder.unreadCount;
                break;
        }

        DropdownHolder dropdown;
        if (convertView == null || !(convertView.getTag() instanceof DropdownHolder)) {
            convertView = mInflater.inflate(R.layout.account_switch_spinner_dropdown_item, null);
            dropdown = new DropdownHolder();
            dropdown.folder = (TextView) convertView.findViewById(R.id.account_spinner_accountname);
            dropdown.unreadCount =
                    (TextView) convertView.findViewById(R.id.account_spinner_unread_count);
            convertView.setTag(dropdown);
        } else {
            dropdown = (DropdownHolder) convertView.getTag();
        }

        dropdown.folder.setText(textLabel);
        dropdown.unreadCount.setText(String.valueOf(unreadCount));
        if (unreadCount == 0) {
            dropdown.unreadCount.setVisibility(View.GONE);
        } else {
            dropdown.unreadCount.setVisibility(View.VISIBLE);
        }
        return convertView;

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
}
