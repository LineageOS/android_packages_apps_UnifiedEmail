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

package com.android.mail.ui;

import com.android.mail.providers.Folder;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.Sets;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.SortedSet;

/**
 * Used to generate folder display information given a raw folders string.
 * (The raw folders string can be obtained from {@link ConversationCursor#getRawFolders()}.)
 *
 */
public class FolderDisplayer {
    public static final String LOG_TAG = new LogUtils().getLogTag();
    protected Context mContext;
    protected String mAccount;
    protected SortedSet<FolderValues> mFolderValuesSortedSet;

    // Reference to the map of folder canonical name to folder id for the folders needed for
    // the folder displayer.  This is set in loadConversationFolders
    protected static class FolderValues implements Comparable<FolderValues> {
        public final String colorId;

        public final String name;

        public final String folderId;

        public int backgroundColor;

        public int textColor;

        public FolderValues(String id, String color, String n, String bgColor, String fgColor,
                Context context) {
            folderId = id;
            colorId = color;
            name = n;
            final boolean showBgColor = !TextUtils.isEmpty(bgColor);
            if (showBgColor) {
                backgroundColor = Integer.parseInt(bgColor);
            } else {
                backgroundColor = Utils.getDefaultFolderBackgroundColor(context);
            }
            // TODO(mindyp): add default fg text color and text color from preference.
            textColor = Color.BLACK;
        }

        @Override
        public int compareTo(FolderValues another) {
            return name.compareToIgnoreCase(another.name);
        }
    }

    /**
     * Initialize the FolderDisplayer for the specified account.
     *
     * @param context Context to use for loading string resources
     * @param account
     */
    public void initialize(Context context, String account) {
        mContext = context;
        mAccount = account;
    }

    /**
     * Configure the FolderDisplayer object by parsing the rawFolders string.
     *
     * @param folder string containing serialized folders to display.
     */
    public void loadConversationFolders(String folderString) {
        SortedSet<FolderValues> folderValuesSet = Sets.newTreeSet();
        ArrayList<String> folderArray = new ArrayList<String>(Arrays.asList(TextUtils.split(
                folderString, Folder.FOLDER_SEPARATOR_PATTERN)));
        ArrayList<Folder> folders = new ArrayList<Folder>(folderArray.size());
        for (String folder : folderArray) {
            folders.add(new Folder(folder));
        }
        for (Folder folder : folders) {
            String folderId = folder.id;
            String colorId = folder.bgColor;
            String stringToDisplay = null;

            if (!Folder.isProviderFolder(folder)) {
                stringToDisplay = folder.name;
            }
            stringToDisplay = folder.name;

            if (stringToDisplay != null) {
                folderValuesSet.add(
                        new FolderValues(folderId, colorId, stringToDisplay,
                                folder.bgColor, folder.fgColor, mContext));
            }
        }

        mFolderValuesSortedSet = folderValuesSet;
    }
}