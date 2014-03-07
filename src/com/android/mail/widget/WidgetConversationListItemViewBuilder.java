/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.mail.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.support.v4.text.BidiFormatter;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;

import com.android.mail.R;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.ui.FolderDisplayer;
import com.android.mail.utils.FolderUri;

public class WidgetConversationListItemViewBuilder {

    private final Context mContext;

    private WidgetFolderDisplayer mFolderDisplayer;

    /**
     * Label Displayer for Widget
     */
    protected static class WidgetFolderDisplayer extends FolderDisplayer {
        public WidgetFolderDisplayer(Context context) {
            super(context);
        }

        // Maximum number of folders we want to display
        private static final int MAX_DISPLAYED_FOLDERS_COUNT = 3;

        /*
         * Load Conversation Labels
         */
        @Override
        public void loadConversationFolders(Conversation conv, final FolderUri ignoreFolderUri,
                final int ignoreFolderType) {
            super.loadConversationFolders(conv, ignoreFolderUri, ignoreFolderType);
        }

        private static int getFolderViewId(int position) {
            switch (position) {
                case 0:
                    return R.id.widget_folder_0;
                case 1:
                    return R.id.widget_folder_1;
                case 2:
                    return R.id.widget_folder_2;
            }
            return 0;
        }

        /**
         * Display folders
         */
        public void displayFolders(RemoteViews remoteViews) {
            int displayedFolder = 0;
            for (Folder folderValues : mFoldersSortedSet) {
                int viewId = getFolderViewId(displayedFolder);
                if (viewId == 0) {
                    continue;
                }
                remoteViews.setViewVisibility(viewId, View.VISIBLE);
                int color[] = new int[] {folderValues.getBackgroundColor(mDefaultBgColor)};
                Bitmap bitmap = Bitmap.createBitmap(color, 1, 1, Bitmap.Config.RGB_565);
                remoteViews.setImageViewBitmap(viewId, bitmap);

                if (++displayedFolder == MAX_DISPLAYED_FOLDERS_COUNT) {
                    break;
                }
            }

            for (int i = displayedFolder; i < MAX_DISPLAYED_FOLDERS_COUNT; i++) {
                remoteViews.setViewVisibility(getFolderViewId(i), View.GONE);
            }
        }
    }

    /*
     * Get font sizes and bitmaps from Resources
     */
    public WidgetConversationListItemViewBuilder(Context context) {
        mContext = context;
    }

    /*
     * Return the full View
     */
    public RemoteViews getStyledView(final CharSequence date, final Conversation conversation,
            final FolderUri folderUri, final int ignoreFolderType,
            final SpannableStringBuilder senders, final String subject) {

        final boolean isUnread = !conversation.read;
        final String snippet = conversation.getSnippet();
        final boolean hasAttachments = conversation.hasAttachments;

        // Add style to subject
        final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        final String filteredSubject =
                TextUtils.isEmpty(subject) ? "" : bidiFormatter.unicodeWrap(subject);
        final SpannableStringBuilder subjectAndSnippet = new SpannableStringBuilder(
                Conversation.getSubjectAndSnippetForDisplay(
                        mContext, null /* badgeText */, filteredSubject,
                        bidiFormatter.unicodeWrap(snippet)));
        if (isUnread) {
            subjectAndSnippet.setSpan(new StyleSpan(Typeface.BOLD), 0, filteredSubject.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Inflate and fill out the remote view
        final RemoteViews remoteViews = new RemoteViews(
                mContext.getPackageName(), R.layout.widget_conversation_list_item);
        remoteViews.setTextViewText(R.id.widget_senders, senders);
        remoteViews.setTextViewText(R.id.widget_date, date);
        remoteViews.setTextViewText(R.id.widget_subject, subjectAndSnippet);
        remoteViews.setViewVisibility(R.id.widget_attachment,
                hasAttachments ? View.VISIBLE : View.GONE);
        if (isUnread) {
            remoteViews.setViewVisibility(R.id.widget_unread_background, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.widget_read_background, View.GONE);
        } else {
            remoteViews.setViewVisibility(R.id.widget_unread_background, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_read_background, View.VISIBLE);
        }
        if (mContext.getResources().getBoolean(R.bool.display_folder_colors_in_widget)) {
            mFolderDisplayer = new WidgetFolderDisplayer(mContext);
            mFolderDisplayer.loadConversationFolders(conversation, folderUri, ignoreFolderType);
            mFolderDisplayer.displayFolders(remoteViews);
        }

        return remoteViews;
    }
}
