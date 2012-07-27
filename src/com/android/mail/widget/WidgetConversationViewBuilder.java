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

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.ui.FolderDisplayer;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;

public class WidgetConversationViewBuilder {
    // Static font sizes
    private static int SENDERS_FONT_SIZE;
    private static int DATE_FONT_SIZE;
    private static int SUBJECT_FONT_SIZE;

    // Static colors
    private static int SUBJECT_TEXT_COLOR_READ;
    private static int SUBJECT_TEXT_COLOR_UNREAD;
    private static int SENDERS_TEXT_COLOR_READ;
    private static int SENDERS_TEXT_COLOR_UNREAD;
    private static int DATE_TEXT_COLOR_READ;
    private static int DATE_TEXT_COLOR_UNREAD;
    private static int DRAFT_TEXT_COLOR;

    private static String SENDERS_SPLIT_TOKEN;

    // Static bitmap
    private static Bitmap ATTACHMENT;

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
        public void loadConversationFolders(ArrayList<Folder> rawFolders, Folder ignoreFolder) {
            super.loadConversationFolders(rawFolders, ignoreFolder);
        }

        private int getFolderViewId(int position) {
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
    public WidgetConversationViewBuilder(Context context, Account account) {
        mContext = context;
        Resources res = context.getResources();

        // Initialize font sizes
        SENDERS_FONT_SIZE = res.getDimensionPixelSize(R.dimen.widget_senders_font_size);
        DATE_FONT_SIZE = res.getDimensionPixelSize(R.dimen.widget_date_font_size);
        SUBJECT_FONT_SIZE = res.getDimensionPixelSize(R.dimen.widget_subject_font_size);

        // Initialize colors
        SUBJECT_TEXT_COLOR_READ = res.getColor(R.color.subject_text_color_read);
        SUBJECT_TEXT_COLOR_UNREAD = res.getColor(R.color.subject_text_color_unread);
        SENDERS_TEXT_COLOR_READ = res.getColor(R.color.senders_text_color_read);
        SENDERS_TEXT_COLOR_UNREAD = res.getColor(R.color.senders_text_color_unread);
        DATE_TEXT_COLOR_READ = res.getColor(R.color.date_text_color_read);
        DATE_TEXT_COLOR_UNREAD = res.getColor(R.color.date_text_color_unread);
        DRAFT_TEXT_COLOR = res.getColor(R.color.drafts);

        SENDERS_SPLIT_TOKEN = res.getString(R.string.senders_split_token);

        // Initialize Bitmap
        ATTACHMENT = BitmapFactory.decodeResource(res, R.drawable.ic_attachment_holo_light);
    }

    /*
     * Add size, color and style to a given text
     */
    private CharSequence addStyle(CharSequence text, int size, int color) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        builder.setSpan(
                new AbsoluteSizeSpan(size), 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (color != 0) {
            builder.setSpan(new ForegroundColorSpan(color), 0, text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    /*
     * Return the full View
     */
    public RemoteViews getStyledView(CharSequence senders, CharSequence status, CharSequence date,
            CharSequence subject, CharSequence snippet, ArrayList<Folder> folders,
            boolean hasAttachments, boolean read, Folder currentFolder) {

        final boolean isUnread = !read;

        // Add style to senders
        CharSequence styledSenders = addStyle(senders, SENDERS_FONT_SIZE,
                isUnread ? SENDERS_TEXT_COLOR_UNREAD : SENDERS_TEXT_COLOR_READ);

        // Add the status indicator
        if (status.length() > 0) {
            final SpannableStringBuilder builder = new SpannableStringBuilder(styledSenders);

            if (senders.length() > 0) {
                // TODO(pwestbro) sender formatting should use resources.  Bug 5354473
                builder.append(addStyle(SENDERS_SPLIT_TOKEN, SENDERS_FONT_SIZE,
                        isUnread ? SENDERS_TEXT_COLOR_UNREAD : SENDERS_TEXT_COLOR_READ));
            }

            final CharSequence styledStatus = addStyle(status, SENDERS_FONT_SIZE, DRAFT_TEXT_COLOR);
            styledSenders = builder.append(styledStatus);
        }

        // Add style to date
        CharSequence styledDate = addStyle(date, DATE_FONT_SIZE, isUnread ? DATE_TEXT_COLOR_UNREAD
                : DATE_TEXT_COLOR_READ);

        // Add style to subject
        int subjectColor = isUnread ? SUBJECT_TEXT_COLOR_UNREAD : SUBJECT_TEXT_COLOR_READ;
        SpannableStringBuilder subjectAndSnippet = new SpannableStringBuilder(mContext.getString(
                R.string.subject_and_snippet, subject, snippet));
        if (isUnread) {
            subjectAndSnippet.setSpan(new StyleSpan(Typeface.BOLD), 0, subject.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        subjectAndSnippet.setSpan(new ForegroundColorSpan(subjectColor), 0, subjectAndSnippet
                .length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        CharSequence styledSubject = addStyle(subjectAndSnippet, SUBJECT_FONT_SIZE, 0);

        // Paper clip for attachment
        Bitmap paperclipBitmap = null;
        if (hasAttachments) {
            paperclipBitmap = ATTACHMENT;
        }

        // Inflate and fill out the remote view
        RemoteViews remoteViews = new RemoteViews(
                mContext.getPackageName(), R.layout.widget_conversation);
        remoteViews.setTextViewText(R.id.widget_senders, styledSenders);
        remoteViews.setTextViewText(R.id.widget_date, styledDate);
        remoteViews.setTextViewText(R.id.widget_subject, styledSubject);
        if (paperclipBitmap != null) {
            remoteViews.setViewVisibility(R.id.widget_attachment, View.VISIBLE);
            remoteViews.setImageViewBitmap(R.id.widget_attachment, paperclipBitmap);
        } else {
            remoteViews.setViewVisibility(R.id.widget_attachment, View.GONE);
        }
        if (isUnread) {
            remoteViews.setViewVisibility(R.id.widget_unread_background, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.widget_read_background, View.GONE);
        } else {
            remoteViews.setViewVisibility(R.id.widget_unread_background, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_read_background, View.VISIBLE);
        }
        if (mContext.getResources().getBoolean(R.bool.display_folder_colors_in_widget)) {
            mFolderDisplayer = new WidgetFolderDisplayer(mContext);
            mFolderDisplayer.loadConversationFolders(folders, currentFolder);
            mFolderDisplayer.displayFolders(remoteViews);
        }

        return remoteViews;
    }
}
