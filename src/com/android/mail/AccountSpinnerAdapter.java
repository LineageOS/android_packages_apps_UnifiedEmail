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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.android.mail.providers.AccountCacheProvider;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;

/**
 * An adapter to return the list of accounts and labels for the Account Spinner.
 * This class gets a merge cursor and returns views that are appropriate for the
 * various objects that the merged cursor returns.
 * @author viki@google.com (Vikram Aggarwal)
 *
 */
public class AccountSpinnerAdapter implements SpinnerAdapter,ListAdapter {
    private final LayoutInflater mInflater;
    /**
     * The current account being viewed
     */
    private String mCurrentAccount;
    private final ContentResolver mResolver;

    /**
     * Total number of accounts.
     */
    private int numAccounts;

    /**
     *  Cursor into the accounts database
     */
    private final Cursor mAccountCursor;

    /**
     *  The name of the account is the 2nd column in UIProvider.ACCOUNTS_PROJECTION
     */
    static final int NAME_COLUMN = 2;
    /**
     * Fake labels for now
     */
    private final String[] mFolders;
    /**
     *  Fake unread counts
     */
    private final int[] mUnreadCounts = {
            0, 2, 42
    };

    /**
     * When the user selects the spinner, a dropdown list of objects is shown. Each item in the
     * dropdown list has two textviews.
     */
    private static class DropdownHolder {
        TextView label;
        TextView unread_count;
    }

    /**
     * After the accounts, the dropdown item is a header.
     */
    private static class HeaderHolder {
        TextView account;
    }

    /**
     * The spinner shows the name of the label, the account name, and the unread count.
     */
    private static class ViewHolder {
        TextView label;
        TextView account;
        TextView unread_count;
    }

    private static final int TYPE_ACCOUNT = 0;
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_FOLDER = 2;

    private int getType(int position) {
        // First the accounts
        if (position < numAccounts) {
            return TYPE_ACCOUNT;
        }
        // Then the header
        if (position == numAccounts) {
            return TYPE_HEADER;
        }
        // Finally, the recent folders.
        return TYPE_FOLDER;
    }

    public AccountSpinnerAdapter(Context context) {
        mInflater = LayoutInflater.from(context);

        // Get the data from the system Accounts
        mResolver = context.getContentResolver();
        mAccountCursor = mResolver.query(AccountCacheProvider.getAccountsUri(),
                UIProvider.ACCOUNTS_PROJECTION, null, null, null);
        numAccounts = mAccountCursor.getCount();
        // Fake folder information
        mFolders = new String[3];
        mFolders[0] = "Drafts -fake";
        mFolders[1] = "Sent -fake";
        mFolders[1] = "Starred -fake";
    }

    @Override
    public int getCount() {
        // All the accounts, plus one header, and optionally some labels
        return numAccounts + 1 + mFolders.length;
    }

    @Override
    public Object getItem(int position) {
        switch (getType(position)){
            case TYPE_ACCOUNT:
                return getAccount(position);
            case TYPE_HEADER:
                return "account spinner header";
            default:
                return mFolders[position - numAccounts - 1];
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String folderName = "";
        String accountName = "";
        int unreadCount = 0;
        switch (getType(position)) {
            case TYPE_ACCOUNT:
                // The default Inbox for the given account
                accountName = getAccount(position);
                folderName = "Inbox";
                break;
            case TYPE_HEADER:
                // We can never select the header, and we want the default view to be the Inbox.
                accountName = getAccount(0);
                folderName = "Inbox";
                break;
            default:
                // Change the name of the current label
                final int offset = position - numAccounts - 1;
                accountName = mCurrentAccount;
                folderName = mFolders[offset];
                unreadCount = mUnreadCounts[offset];
                break;
        }

        // Return a view with the label on the first line and the account name on the second.
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.account_switch_spinner_item, null);
            holder = new ViewHolder();
            holder.account =
                    (TextView) convertView.findViewById(R.id.account_spinner_account_name);
            holder.label =
                    (TextView) convertView.findViewById(R.id.account_spinner_label);
            holder.unread_count =
                    (TextView) convertView.findViewById(R.id.unread);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        // TODO(viki): Fill with a real label here.
        // TODO(viki): Also rename label to folder.
        holder.label.setText(folderName);
        holder.account.setText(accountName);
        // Fake unread counts for now.
        holder.unread_count.setText(String.valueOf(unreadCount));
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
    public void registerDataSetObserver(DataSetObserver observer) {
        // Don't do anything for now. In the future, we will want to re-read the account
        // information. In particular: recalculating the total number of accounts.
    }


    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        // Don't do anything for now.
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
                header.account.setText(mCurrentAccount);
                return convertView;
            case TYPE_ACCOUNT:
                textLabel = getAccount(position);
                break;
            case TYPE_FOLDER:
                final int offset = position - numAccounts - 1;
                textLabel = mFolders[offset];
                unreadCount = mUnreadCounts[offset];
                break;
        }

        DropdownHolder dropdown;
        if (convertView == null || !(convertView.getTag() instanceof DropdownHolder)) {
            convertView = mInflater.inflate(R.layout.account_switch_spinner_dropdown_item, null);
            dropdown = new DropdownHolder();
            dropdown.label = (TextView) convertView.findViewById(R.id.account_spinner_accountname);
            dropdown.unread_count =
                    (TextView) convertView.findViewById(R.id.account_spinner_unread_count);
            convertView.setTag(dropdown);
        } else {
            dropdown = (DropdownHolder) convertView.getTag();
        }

        dropdown.label.setText(textLabel);
        dropdown.unread_count.setText(String.valueOf(unreadCount));
        if (unreadCount == 0) {
            dropdown.unread_count.setVisibility(View.GONE);
        } else {
            dropdown.unread_count.setVisibility(View.VISIBLE);
        }
        return convertView;

    }

    /**
     * Returns the name of the label at the given position in the spinner.
     * @param position
     * @return
     */
    private String getAccount(int position) {
        mAccountCursor.moveToPosition(position);
        final int accountNameCol = mAccountCursor.getColumnIndex(UIProvider.AccountColumns.NAME);
        return mAccountCursor.getString(accountNameCol);
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
