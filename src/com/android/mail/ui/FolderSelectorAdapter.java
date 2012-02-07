/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui;

import com.android.mail.R;
import com.android.mail.providers.Folder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.PaintDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An adapter for translating a {@link LabelList} to a set of selectable views to be used for
 * applying labels to one or more conversations.
 */
public class FolderSelectorAdapter extends BaseAdapter {

    public static class FolderRow implements Comparable<FolderRow> {
        private final Folder mFolder;
        private boolean mIsPresent;

        public FolderRow(Folder folder, boolean isPresent) {
            mFolder = folder;
            mIsPresent = isPresent;
        }

        public Folder getFolder() {
            return mFolder;
        }

        public boolean isPresent() {
            return mIsPresent;
        }

        public void setIsPresent(boolean isPresent) {
            mIsPresent = isPresent;
        }

        @Override
        public int compareTo(FolderRow another) {
            if (equals(another)) {
                return 0;
            } else if (mIsPresent != another.mIsPresent) {
                return mIsPresent ? -1 : 1;
            } else {
                return mFolder.name.compareToIgnoreCase(another.mFolder.name);
            }
        }

    }

    private List<FolderRow> mFolderRows = Lists.newArrayList();
    private LayoutInflater mInflater;

    private final Map<Integer, PaintDrawable> mColorBlockCache = Maps.newHashMap();

    private static int DEFAULT_LABEL_BACKGROUND_COLOR = android.R.color.white;

    public FolderSelectorAdapter(Context context, Cursor folders,
            Set<String> initiallySelected) {
        mInflater = LayoutInflater.from(context);

        processLists(folders, initiallySelected);
    }

    private void processLists(Cursor folders, Set<String> initiallySelected) {
        while (folders.moveToNext()) {
            Folder folder = new Folder(folders);

            FolderRow row = new FolderRow(folder, initiallySelected.contains(folder.name));
            mFolderRows.add(row);
        }
        Collections.sort(mFolderRows);
    }

    @Override
    public int getCount() {
        return mFolderRows.size();
    }

    @Override
    public FolderRow getItem(int position) {
        return mFolderRows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        CheckBox checkBox;
        View colorBlock;

        if (view == null) {
            view = mInflater.inflate(R.layout.folders_view, parent, false);
            checkBox = (CheckBox) view.findViewById(R.id.checkbox);
            // Suppress the checkbox selection, and handle the toggling of the label
            // on the parent list item's click handler.
            checkBox.setClickable(false);
            colorBlock = view.findViewById(R.id.color_block);
            view.setTag(R.id.checkbox, checkBox);
            view.setTag(R.id.color_block, colorBlock);
        } else {
            checkBox = (CheckBox) view.getTag(R.id.checkbox);
            colorBlock = (View) view.getTag(R.id.color_block);
        }

        FolderRow row = getItem(position);
        Folder folder = row.getFolder();

        checkBox.setText(folder.name);
        checkBox.setChecked(row.isPresent());

        return view;
    }

}
