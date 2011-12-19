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
import android.widget.SpinnerAdapter;
import android.widget.TextView;


/**
 * An adapter to return the list of accounts and labels for the Account Spinner.
 * This class gets a merge cursor and returns views that are appropriate for the
 * various objects that the merged cursor returns.
 * @author viki@google.com (Vikram Aggarwal)
 *
 */
public class AccountSpinnerAdapter implements SpinnerAdapter {
    private String labels[];
    private Integer unread_counts[];
    private LayoutInflater mInflater;

    /**
     * A single view object consists of a text label and an unread count.
     * @author viki@google.com (Vikram Aggarwal)
     */
    private static class ViewHolder {
        TextView label;
        TextView unread_count;
    }

    public AccountSpinnerAdapter(Context context){
        mInflater = LayoutInflater.from(context);
        labels = new String[3];
        labels[0] = "Inbox";
        labels[1] = "Outbox";
        labels[2] = "Drafts";
        unread_counts = new Integer[3];
        unread_counts[0] = 13;
        unread_counts[1] = 1;
        unread_counts[2] = 0;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getCount()
     */
    @Override
    public int getCount() {
        return labels.length;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItem(int)
     */
    @Override
    public Object getItem(int position) {
        // The item data is the name of the label.
        return labels[position];
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItemId(int)
     */
    @Override
    public long getItemId(int position) {
        // We use the position as the ID
        return position;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getItemViewType(int)
     */
    @Override
    public int getItemViewType(int position) {
        // If there are a variety of views, this returns the kind of view that the position
        // represents. So if there are two views, and you have view types 0, and 1, then this
        // method returns which view type the 'position' represents.

        // Since we have a single view type, we always return zero.
        return 0;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null){
            convertView = mInflater.inflate(R.layout.account_switch_spinner_dropdown_item, null);
            holder = new ViewHolder();
            holder.label = (TextView) convertView.findViewById(R.id.account_spinner_accountname);
            holder.unread_count =
                    (TextView) convertView.findViewById(R.id.account_spinner_unread_count);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.label.setText(labels[position]);
        holder.unread_count.setText(unread_counts[position].toString());
        return convertView;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#getViewTypeCount()
     */
    @Override
    public int getViewTypeCount() {
        // We always show the same view
        return 1;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#hasStableIds()
     */
    @Override
    public boolean hasStableIds() {
        // The data generated is static for now, the IDs are stable. However, in the future this
        // won't be the case
        return true;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#isEmpty()
     */
    @Override
    public boolean isEmpty() {
        // Will always contain something.
        return false;
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#registerDataSetObserver(android.database.DataSetObserver)
     */
    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        // Don't do anything for now, since the data is mocked.
    }

    /* (non-Javadoc)
     * @see android.widget.Adapter#unregisterDataSetObserver(android.database.DataSetObserver)
     */
    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        // Don't do anything for now, since the data is mocked.
    }

    /* (non-Javadoc)
     * @see
     * android.widget.SpinnerAdapter#getDropDownView(int, android.view.View, android.view.ViewGroup)
     */
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        // Return the same view as the items for now.
        return getView(position, convertView, parent);
    }
}
