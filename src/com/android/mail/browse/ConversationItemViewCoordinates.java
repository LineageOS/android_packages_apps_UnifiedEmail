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
 * (eg, checkmark, star, subject, sender, labels, etc.) It will inflate a view,
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
    private static int TOTAL_LABEL_WIDTH = -1;
    private static int TOTAL_LABEL_WIDTH_WIDE = -1;
    private static int LABEL_CELL_WIDTH = -1;
    private static int sConversationHeights[];

    // Checkmark.
    int checkmarkX;
    int checkmarkY;

    // Star.
    int starX;
    int starY;

    // Personal level.
    int personalLevelX;
    int personalLevelY;

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

    // Labels.
    int labelsXEnd;
    int labelsY;
    int labelsHeight;
    int labelsTopPadding;
    int labelsFontSize;
    int labelsAscent;

    // Date.
    int dateXEnd;
    int dateY;
    int dateFontSize;
    int dateAscent;

    // Paperclip.
    int paperclipY;

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
        return viewMode.getMode() == ViewMode.CONVERSATION_LIST ? res
                .getInteger(R.integer.conversation_list_header_mode) : res
                .getInteger(R.integer.conversation_header_mode);
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
    public static int getSubjectLength(Context context, int mode, boolean hasVisibleLabels,
            boolean hasAttachments) {
        if (hasVisibleLabels) {
            if (hasAttachments) {
                return context.getResources().getIntArray(
                        R.array.senders_with_labels_and_attachment_lengths)[mode];
            } else {
                return context.getResources().getIntArray(
                        R.array.senders_with_labels_lengths)[mode];
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

    /**
     * Returns the width available to draw labels in this mode.
     */
    public static int getLabelsWidth(Context context, int mode) {
        Resources res = context.getResources();
        if (TOTAL_LABEL_WIDTH <= 0) {
            TOTAL_LABEL_WIDTH = res.getDimensionPixelSize(R.dimen.max_total_label_width);
            TOTAL_LABEL_WIDTH_WIDE = res.getDimensionPixelSize(R.dimen.max_total_label_width_wide);
        }
        switch (mode) {
            case WIDE_MODE:
                return TOTAL_LABEL_WIDTH_WIDE;
            case NORMAL_MODE:
                return TOTAL_LABEL_WIDTH;
            default:
                throw new IllegalArgumentException("Unknown conversation header view mode " + mode);
        }
    }

    /**
     * Returns the width of a cell to draw labels.
     */
    public static int getLabelCellWidth(Context context, int mode, int labelsCount) {
        Resources res = context.getResources();
        if (LABEL_CELL_WIDTH <= 0) {
            LABEL_CELL_WIDTH = res.getDimensionPixelSize(R.dimen.label_cell_width);
        }
        switch (mode) {
            case WIDE_MODE:
            case NORMAL_MODE:
                return LABEL_CELL_WIDTH;
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
            coordinates.personalLevelX = getX(personalLevel);
            coordinates.personalLevelY = getY(personalLevel);

            TextView senders = (TextView) view.findViewById(R.id.senders);
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

            View labels = view.findViewById(R.id.labels);
            coordinates.labelsXEnd = getX(labels) + labels.getWidth();
            coordinates.labelsY = getY(labels);
            coordinates.labelsHeight = labels.getHeight();
            coordinates.labelsTopPadding = labels.getPaddingTop();
            if (labels instanceof TextView) {
                coordinates.labelsFontSize = (int) ((TextView) labels).getTextSize();
                sPaint.setTextSize(coordinates.labelsFontSize);
                coordinates.labelsAscent = (int) sPaint.ascent();
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

    public static boolean displayLabelsAboveDate(int mode) {
        return mode == WIDE_MODE;
    }
}
