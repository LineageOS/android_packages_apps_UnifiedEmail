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

package com.android.mail.browse;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextPaint;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewParent;
import android.widget.TextView;
import com.android.mail.R;
import com.android.mail.ui.ViewMode;

/**
 * Represents the coordinates of elements inside a CanvasConversationHeaderView
 * (eg, checkmark, star, subject, sender, folders, etc.) It will inflate a view,
 * and record the coordinates of each element after layout. This will allows us
 * to easily improve performance by creating custom view while still defining
 * layout in XML files.
 *
 * @author phamm
 */
public class ConversationItemViewCoordinates {
    // Modes.
    private static final int WIDE_MODE = 0;
    private static final int NORMAL_MODE = 1;

    // Static threshold.
    private static int TOTAL_FOLDER_WIDTH = -1;
    private static int TOTAL_FOLDER_WIDTH_WIDE = -1;
    private static int FOLDER_CELL_WIDTH = -1;
    private static int sConversationHeights[];

    // For combined views
    private static int COLOR_BLOCK_WIDTH = -1;
    private static int COLOR_BLOCK_HEIGHT = -1;

    // Checkmark.
    int checkmarkX;
    int checkmarkY;

    // Star.
    int starX;
    int starY;

    // Personal level.
    int personalLevelX;
    int personalLevelY;
    boolean showPersonalLevel;

    // Senders.
    int sendersX;
    int sendersY;
    int sendersWidth;
    int sendersLineCount;
    int sendersLineHeight;
    int sendersFontSize;
    int sendersAscent;

    // Subject.
    int subjectX;
    int subjectY;
    int subjectWidth;
    int subjectLineCount;
    int subjectFontSize;
    int subjectAscent;

    // Folders.
    int foldersXEnd;
    int foldersY;
    int foldersHeight;
    int foldersTopPadding;
    int foldersFontSize;
    int foldersAscent;
    boolean showFolders;
    boolean showColorBlock;

    // Date.
    int dateXEnd;
    int dateY;
    int dateFontSize;
    int dateAscent;

    // Paperclip.
    int paperclipY;

    // Reply state of a conversation.
    boolean showReplyState;
    int replyStateX;
    int replyStateY;

    // Minimum height of this view; used for animating.
    int minHeight;
    SendersView sendersView;


    // Cache to save Coordinates based on view width.
    private static SparseArray<ConversationItemViewCoordinates> mCache =
            new SparseArray<ConversationItemViewCoordinates>();

    private static TextPaint sPaint = new TextPaint();

    static {
        sPaint.setAntiAlias(true);
    }

    /**
     * Returns whether to show a background on the attachment icon.
     * Currently, we don't show a background in wide mode.
     */
    public static boolean showAttachmentBackground(int mode) {
        return mode != WIDE_MODE;
    }

    /**
     * Returns the mode of the header view (Wide/Normal/Narrow).
     */
    public static int getMode(Context context, ViewMode viewMode) {
        Resources res = context.getResources();
        return viewMode.isListMode() ?
                res.getInteger(R.integer.conversation_list_header_mode) :
                res.getInteger(R.integer.conversation_header_mode);
    }

    /**
     * Returns the layout id to be inflated in this mode.
     */
    private static int getLayoutId(int mode) {
        switch (mode) {
            case WIDE_MODE:
                return R.layout.conversation_item_view_wide;
            case NORMAL_MODE:
                return R.layout.conversation_item_view_normal;
            default:
                throw new IllegalArgumentException("Unknown conversation header view mode " + mode);
        }
    }

    /**
     * Returns a value array multiplied by the specified density.
     */
    public static int[] getDensityDependentArray(int[] values, float density) {
        int result[] = new int[values.length];
        for (int i = 0; i < values.length; ++i) {
            result[i] = (int) (values[i] * density);
        }
        return result;
    }

    /**
     * Returns the height of the view in this mode.
     */
    public static int getHeight(Context context, int mode) {
        Resources res = context.getResources();
        float density = res.getDisplayMetrics().scaledDensity;
        if (sConversationHeights == null) {
            sConversationHeights = getDensityDependentArray(
                    res.getIntArray(R.array.conversation_heights), density);
        }
        return sConversationHeights[mode];
    }

    /**
     * Refreshes the conversation heights array.
     */
    public static void refreshConversationHeights(Context context) {
        Resources res = context.getResources();
        float density = res.getDisplayMetrics().scaledDensity;
        sConversationHeights = getDensityDependentArray(
                res.getIntArray(R.array.conversation_heights), density);
    }

    /**
     * Returns the x coordinates of a view by tracing up its hierarchy.
     */
    private static int getX(View view) {
        int x = 0;
        while (view != null) {
            x += (int) view.getX();
            ViewParent parent = view.getParent();
            view = parent != null ? (View) parent : null;
        }
        return x;
    }

    /**
     * Returns the y coordinates of a view by tracing up its hierarchy.
     */
    private static int getY(View view) {
        int y = 0;
        while (view != null) {
            y += (int) view.getY();
            ViewParent parent = view.getParent();
            view = parent != null ? (View) parent : null;
        }
        return y;
    }

    /**
     * Returns the number of lines of this text view.
     */
    private static int getLineCount(TextView textView) {
        return Math.round(((float) textView.getHeight()) / textView.getLineHeight());
    }

    /**
     * Returns the length (maximum of characters) of subject in this mode.
     */
    public static int getSubjectLength(Context context, int mode, boolean hasVisibleFolders,
            boolean hasAttachments) {
        if (hasVisibleFolders) {
            if (hasAttachments) {
                return context.getResources().getIntArray(
                        R.array.senders_with_folders_and_attachment_lengths)[mode];
            } else {
                return context.getResources().getIntArray(
                        R.array.senders_with_folders_lengths)[mode];
            }
        } else {
            if (hasAttachments) {
                return context.getResources().getIntArray(
                        R.array.senders_with_attachment_lengths)[mode];
            } else {
                return context.getResources().getIntArray(R.array.senders_lengths)[mode];
            }
        }
    }

    public static int getColorBlockWidth(Context context) {
        Resources res = context.getResources();
        if (COLOR_BLOCK_WIDTH <= 0) {
            COLOR_BLOCK_WIDTH = res.getDimensionPixelSize(R.dimen.color_block_width);
        }
        return COLOR_BLOCK_WIDTH;
    }

    public static int getColorBlockHeight(Context context) {
        Resources res = context.getResources();
        if (COLOR_BLOCK_HEIGHT <= 0) {
            COLOR_BLOCK_HEIGHT = res.getDimensionPixelSize(R.dimen.color_block_height);
        }
        return COLOR_BLOCK_HEIGHT;
    }

    /**
     * Returns the width available to draw folders in this mode.
     */
    public static int getFoldersWidth(Context context, int mode) {
        Resources res = context.getResources();
        if (TOTAL_FOLDER_WIDTH <= 0) {
            TOTAL_FOLDER_WIDTH = res.getDimensionPixelSize(R.dimen.max_total_folder_width);
            TOTAL_FOLDER_WIDTH_WIDE = res.getDimensionPixelSize(R.dimen.max_total_folder_width_wide);
        }
        switch (mode) {
            case WIDE_MODE:
                return TOTAL_FOLDER_WIDTH_WIDE;
            case NORMAL_MODE:
                return TOTAL_FOLDER_WIDTH;
            default:
                throw new IllegalArgumentException("Unknown conversation header view mode " + mode);
        }
    }

    /**
     * Returns the width of a cell to draw folders.
     */
    public static int getLabelCellWidth(Context context, int mode, int foldersCount) {
        Resources res = context.getResources();
        if (FOLDER_CELL_WIDTH <= 0) {
            FOLDER_CELL_WIDTH = res.getDimensionPixelSize(R.dimen.folder_cell_width);
        }
        switch (mode) {
            case WIDE_MODE:
            case NORMAL_MODE:
                return FOLDER_CELL_WIDTH;
            default:
                throw new IllegalArgumentException("Unknown conversation header view mode " + mode);
        }
    }

    public static boolean displaySendersInline(int mode) {
        switch (mode) {
            case WIDE_MODE:
                return false;
            case NORMAL_MODE:
                return true;
            default:
                throw new IllegalArgumentException("Unknown conversation header view mode " + mode);
        }
    }

    /**
     * Returns coordinates for elements inside a conversation header view given
     * the view width.
     */
    public static ConversationItemViewCoordinates forWidth(Context context, int width, int mode,
            int standardScaledDimen) {
        ConversationItemViewCoordinates coordinates = mCache.get(width ^ standardScaledDimen);
        if (coordinates == null) {
            coordinates = new ConversationItemViewCoordinates();
            mCache.put(width ^ standardScaledDimen, coordinates);

            // Layout the appropriate view.
            int height = getHeight(context, mode);
            View view = LayoutInflater.from(context).inflate(getLayoutId(mode), null);
            int widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            view.measure(widthSpec, heightSpec);
            view.layout(0, 0, width, height);

            // Records coordinates.
            View checkmark = view.findViewById(R.id.checkmark);
            coordinates.checkmarkX = getX(checkmark);
            coordinates.checkmarkY = getY(checkmark);

            View star = view.findViewById(R.id.star);
            coordinates.starX = getX(star);
            coordinates.starY = getY(star);

            View personalLevel = view.findViewById(R.id.personal_level);
            if (personalLevel != null) {
                coordinates.showPersonalLevel = true;
                coordinates.personalLevelX = getX(personalLevel);
                coordinates.personalLevelY = getY(personalLevel);
            } else {
                coordinates.showPersonalLevel = false;
            }

            SendersView senders = (SendersView) view.findViewById(R.id.senders);
            coordinates.sendersView = senders;
            coordinates.sendersX = getX(senders);
            coordinates.sendersY = getY(senders);
            coordinates.sendersWidth = senders.getWidth();
            coordinates.sendersLineCount = getLineCount(senders);
            coordinates.sendersLineHeight = senders.getLineHeight();
            coordinates.sendersFontSize = (int) senders.getTextSize();
            sPaint.setTextSize(coordinates.sendersFontSize);
            coordinates.sendersAscent = (int) sPaint.ascent();

            TextView subject = (TextView) view.findViewById(R.id.subject);
            coordinates.subjectX = getX(subject);
            coordinates.subjectY = getY(subject);
            coordinates.subjectWidth = subject.getWidth();
            coordinates.subjectLineCount = getLineCount(subject);
            coordinates.subjectFontSize = (int) subject.getTextSize();
            sPaint.setTextSize(coordinates.subjectFontSize);
            coordinates.subjectAscent = (int) sPaint.ascent();

            View folders = view.findViewById(R.id.folders);
            if (folders != null) {
                coordinates.showFolders = true;
                coordinates.foldersXEnd = getX(folders) + folders.getWidth();
                coordinates.foldersY = getY(folders);
                coordinates.foldersHeight = folders.getHeight();
                coordinates.foldersTopPadding = folders.getPaddingTop();
                if (folders instanceof TextView) {
                    coordinates.foldersFontSize = (int) ((TextView) folders).getTextSize();
                    sPaint.setTextSize(coordinates.foldersFontSize);
                    coordinates.foldersAscent = (int) sPaint.ascent();
                }
            } else {
                coordinates.showFolders = false;
            }

            View colorBlock = view.findViewById(R.id.color_block);
            if (colorBlock != null) {
                coordinates.showColorBlock = true;
            }

            View replyState = view.findViewById(R.id.reply_state);
            if (replyState != null) {
                coordinates.showReplyState = true;
                coordinates.replyStateX = getX(replyState);
                coordinates.replyStateY = getY(replyState);
            } else {
                coordinates.showReplyState = false;
            }

            TextView date = (TextView) view.findViewById(R.id.date);
            coordinates.dateXEnd = getX(date) + date.getWidth();
            coordinates.dateY = getY(date);
            coordinates.dateFontSize = (int) date.getTextSize();
            sPaint.setTextSize(coordinates.dateFontSize);
            coordinates.dateAscent = (int) sPaint.ascent();

            View paperclip = view.findViewById(R.id.paperclip);
            coordinates.paperclipY = getY(paperclip);
        }
        return coordinates;
    }

    public static boolean displayFoldersAboveDate(int mode) {
        return mode == WIDE_MODE;
    }

    public static int getFolderCellWidth(Context context, int mode, int foldersCount) {
        Resources res = context.getResources();
        if (FOLDER_CELL_WIDTH <= 0) {
            FOLDER_CELL_WIDTH = res.getDimensionPixelSize(R.dimen.folder_cell_width);
        }
        switch (mode) {
            case WIDE_MODE:
            case NORMAL_MODE:
                return FOLDER_CELL_WIDTH;
            default:
                throw new IllegalArgumentException("Unknown conversation header view mode " + mode);
        }
    }

    public static boolean isWideMode(int mode) {
        return mode == WIDE_MODE;
    }

    public static int getMinHeight(Context context, ViewMode viewMode) {
        int mode = ConversationItemViewCoordinates.getMode(context, viewMode);
        return context.getResources().getDimensionPixelSize(
                mode == WIDE_MODE ? R.dimen.conversation_item_height
                        : R.dimen.conversation_item_height_wide);
    }
}
