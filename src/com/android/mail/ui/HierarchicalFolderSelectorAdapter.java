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

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.android.mail.R;
import com.android.mail.providers.Folder;
import com.google.common.annotations.VisibleForTesting;

import java.util.Set;

public class HierarchicalFolderSelectorAdapter extends FolderSelectorAdapter {

    private Context mContext;

    public HierarchicalFolderSelectorAdapter(Context context, Cursor folders,
            Set<String> initiallySelected, boolean single) {
        super(context, folders, initiallySelected, single);
        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        Folder folder = getItem(position).getFolder();
        CompoundButton checkBox = (CompoundButton) view.findViewById(R.id.checkbox);
        checkBox.setText(TextUtils.isEmpty(folder.hierarchicalDesc) ? folder.name
                : truncateHierarchy(folder.hierarchicalDesc));
        return view;
    }

    /**
     * Truncation of a hierarchy works as follows:
     * 1) If there is just a folder name, return that.
     * 2) If there is a single parent and a folder name, return parent/folder.
     * 3) If there is > 1 but < 3 ancestors, return ancestor/ancestor2/folder
     * 4) If there are > 3 ancestors, return the top most ancestor, and direct parent
     * of the folder, and the folder: ancestor/.../directParent/folder
     */
    @VisibleForTesting
    protected String truncateHierarchy(String hierarchy) {
        if (TextUtils.isEmpty(hierarchy)) {
            return "";
        }
        String[] splitHierarchy = hierarchy.split("/");
        // We want to keep the last part of the hierachy, as that is the name of the folder.
        String folderName = null;
        String topParentName = null;
        String directParentName = null;
        StringBuilder display = new StringBuilder();
        if (splitHierarchy != null && splitHierarchy.length > 0) {
            int length = splitHierarchy.length;
            if (length > 2) {
                topParentName = splitHierarchy[0];
                directParentName = splitHierarchy[length - 2];
                folderName = splitHierarchy[length - 1];
            } else if (length > 1) {
                topParentName = splitHierarchy[0];
                folderName = splitHierarchy[length - 1];
            } else {
                folderName = splitHierarchy[0];
            }
            if (!TextUtils.isEmpty(directParentName)) {
                final int formatString;
                if (length > 3) {
                    formatString = R.string.hierarchical_folder_parent_top_ellip;
                } else {
                    formatString = R.string.hierarchical_folder_parent_top;
                }
                display.append(mContext.getResources().getString(formatString, topParentName,
                        directParentName, folderName));
            } else if (!TextUtils.isEmpty(topParentName)) {
                display.append(mContext.getResources().getString(R.string.hierarchical_folder_top,
                        topParentName, directParentName, folderName));
            } else {
                display.append(folderName);
            }
        }
        return display.toString();
    }
}
