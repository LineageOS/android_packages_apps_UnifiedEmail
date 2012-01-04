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

package com.android.email;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

/**
 * An adapter to return the list of accounts and labels for the Account Spinner.
 * This class gets a merge cursor and returns views that are appropriate for the
 * various objects that the merged cursor returns.
 * @author viki@google.com (Vikram Aggarwal)
 *
 */
public class AccountSpinnerAdapter implements SpinnerAdapter,ListAdapter {
    private String mLabels[];
    private Integer mUnreadCounts[];
    private LayoutInflater mInflater;
    private String mCurrentAccount;

    /**
     * When the user selects the spinner, a dropdown list of objects is shown. Each item in the
     * dropdown list has two textviews.
     */
    private static class DropdownHolder {
        TextView label;
        TextView unread_count;
    }

    /**
     * The first dropdown item is a header.
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

    public AccountSpinnerAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
        // Fake data.
        mLabels = new String[3];
        mLabels[0] = "Inbox";
        mLabels[1] = "Outbox";
        mLabels[2] = "Drafts";
        mUnreadCounts = new Integer[3];
        mUnreadCounts[0] = 13;
        mUnreadCounts[1] = 1;
        mUnreadCounts[2] = 0;
        mCurrentAccount = "test@android.com";
    }

    @Override
    public int getCount() {
        // All the labels, plus one header.
        return mLabels.length + 1;
    }

    @Override
    public Object getItem(int position) {
        return mLabels[position - 1];
    }

    @Override
    public long getItemId(int position) {
        // We use the position as the ID
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER;
        }
        // If there are a variety of views, this returns the kind of view that the position
        // represents. So if there are two views, and you have view types 0, and 1, then this
        // method returns which view type the 'position' represents.

        // Since we have a single view type, we always return zero.
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0) {
            // We can never select the header, and we want the default view to be the Inbox.
            return getView(1, convertView, parent);
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
        holder.label.setText(mLabels[position - 1]);
        holder.account.setText(mCurrentAccount);
        holder.unread_count.setText(mUnreadCounts[position - 1].toString());
        if (mUnreadCounts[position - 1] == 0) {
            holder.unread_count.setVisibility(View.GONE);
        } else {
            holder.unread_count.setVisibility(View.VISIBLE);
        }
        return convertView;
    }

    @Override
    public int getViewTypeCount() {
        // One view, and one header
        return 2;
    }

    @Override
    public boolean hasStableIds() {
        // The data generated is static for now, the IDs are stable. However, in the future this
        // won't be the case
        return true;
    }

    @Override
    public boolean isEmpty() {
        // Will always contain something.
        return false;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        // Don't do anything for now, since the data is mocked.
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        // Don't do anything for now, since the data is mocked.
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        // At the top, we show a header. This is a special view.
        if (position == 0) {
            HeaderHolder holder;
            if (convertView == null ||
                    !(convertView.getTag() instanceof HeaderHolder)) {
                convertView =
                        mInflater.inflate(R.layout.account_switch_spinner_dropdown_header, null);
                holder = new HeaderHolder();
                holder.account =
                        (TextView) convertView.findViewById(R.id.account_spinner_header_account);
                convertView.setTag(holder);
            } else {
                holder = (HeaderHolder) convertView.getTag();
            }
            holder.account.setText(mCurrentAccount);
            return convertView;
        }

        DropdownHolder holder;
        if (convertView == null ||
                !(convertView.getTag() instanceof DropdownHolder)) {
            convertView = mInflater.inflate(R.layout.account_switch_spinner_dropdown_item, null);
            holder = new DropdownHolder();
            holder.label = (TextView) convertView.findViewById(R.id.account_spinner_accountname);
            holder.unread_count =
                    (TextView) convertView.findViewById(R.id.account_spinner_unread_count);
            convertView.setTag(holder);
        } else {
            holder = (DropdownHolder) convertView.getTag();
        }

        holder.label.setText(mLabels[position - 1]);
        holder.unread_count.setText(mUnreadCounts[position - 1].toString());
        if (mUnreadCounts[position - 1] == 0) {
            holder.unread_count.setVisibility(View.GONE);
        } else {
            holder.unread_count.setVisibility(View.VISIBLE);
        }
        return convertView;
    }

    @Override
    public boolean isEnabled(int position) {
        // Don't want the user selecting the header.
        return position != 0;
    }

    @Override
    public boolean areAllItemsEnabled() {
        // The header is the only non-enabled item.
        return false;
    }
}
