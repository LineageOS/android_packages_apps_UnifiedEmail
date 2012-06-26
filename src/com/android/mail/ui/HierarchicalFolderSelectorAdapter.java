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

import java.util.Set;

public class HierarchicalFolderSelectorAdapter extends FolderSelectorAdapter {

    public HierarchicalFolderSelectorAdapter(Context context, Cursor folders,
            Set<String> initiallySelected, boolean single) {
        super(context, folders, initiallySelected, single);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        Folder folder = getItem(position).getFolder();
        CompoundButton checkBox = (CompoundButton) view.getTag(R.id.checkbox);
        checkBox.setText(TextUtils.isEmpty(folder.hierarchicalDesc) ? folder.name
                : truncateHierarchy(folder.hierarchicalDesc));
        return view;
    }

    // TODO: make this properly truncate a hierarchy string; write test cases to
    // prove it works.
    private String truncateHierarchy(String hierarchy) {
        return hierarchy;
    }
}
