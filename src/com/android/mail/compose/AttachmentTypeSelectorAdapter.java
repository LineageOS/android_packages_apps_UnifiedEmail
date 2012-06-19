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

package com.android.mail.compose;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.mail.R;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class AttachmentTypeSelectorAdapter extends BaseAdapter {

    /**
     * Backing list for the {@link AlertDialog}'s adapter.
     */
    public static final List<AttachmentType> ITEMS = ImmutableList.of(
            new AttachmentType(
                    R.string.attach_image, R.drawable.ic_attach_picture_holo_light, "image/*"),
            new AttachmentType(
                    R.string.attach_video, R.drawable.ic_attach_video_holo_light, "video/*"));

    private final LayoutInflater mInflater;

    public AttachmentTypeSelectorAdapter(Context context) {
        super();
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return ITEMS.size();
    }

    @Override
    public Object getItem(int position) {
        return ITEMS.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Make a view to hold each row.
     *
     * @see android.widget.ListAdapter#getView(int, android.view.View,
     *      android.view.ViewGroup)
     */
    public View getView(int position, View convertView, ViewGroup parent) {
        // A ViewHolder keeps references to children views to avoid unneccessary calls
        // to findViewById() on each row.
        ViewHolder holder;

        // When convertView is not null, we can reuse it directly, there is no need
        // to reinflate it. We only inflate a new View when the convertView supplied
        // by ListView is null.
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.icon_list_item, null);

            // Creates a ViewHolder and store references to the two children views
            // we want to bind data to.
            holder = new ViewHolder();
            holder.text = (TextView) convertView.findViewById(R.id.text);

            convertView.setTag(holder);
        } else {
            // Get the ViewHolder back to get fast access to the TextView
            // and the ImageView.
            holder = (ViewHolder) convertView.getTag();
        }

        // Bind the data efficiently with the holder.
        final AttachmentType attachmentType = ITEMS.get(position);

        holder.text.setText(attachmentType.mTextId);
        holder.text.setCompoundDrawablesWithIntrinsicBounds(attachmentType.mIconId, 0, 0, 0);

        return convertView;
    }

    static class ViewHolder {
        TextView text;
        ImageView icon;
    }

    public static class AttachmentType {
        public int mIconId;
        public int mTextId;
        public String mMimeType;

        public AttachmentType(int textId, int iconId, String mimeType) {
            mTextId = textId;
            mIconId = iconId;
            mMimeType = mimeType;
        }
    }
}
