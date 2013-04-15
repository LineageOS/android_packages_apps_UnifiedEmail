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
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.ui.ViewMode;
import com.android.mail.utils.Utils;
import com.google.common.base.Objects;

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
    // Modes
    static final int MODE_COUNT = 2;
    static final int WIDE_MODE = 0;
    static final int NORMAL_MODE = 1;

    // Left-side gadget modes
    static final int GADGET_NONE = 0;
    static final int GADGET_CONTACT_PHOTO = 1;
    static final int GADGET_CHECKBOX = 2;

    // Attachment previews modes
    static final int ATTACHMENT_PREVIEW_MODE_COUNT = 3;
    static final int ATTACHMENT_PREVIEW_NONE = 0;
    static final int ATTACHMENT_PREVIEW_TALL = 1;
    static final int ATTACHMENT_PREVIEW_SHORT = 2;

    // Static threshold.
    private static int TOTAL_FOLDER_WIDTH = -1;
    private static int TOTAL_FOLDER_WIDTH_WIDE = -1;
    @Deprecated
    private static int sConversationHeights[];

    // For combined views
    private static int COLOR_BLOCK_WIDTH = -1;
    private static int COLOR_BLOCK_HEIGHT = -1;

    /**
     * Simple holder class for an item's abstract configuration state. ListView binding creates an
     * instance per item, and {@link #forConfig(Context, Config, SparseArray)} uses it to hide/show
     * optional views and determine the correct coordinates for that item configuration.
     */
    public static final class Config {
        private int mWidth;
        private int mMode = NORMAL_MODE;
        private int mGadgetMode = GADGET_NONE;
        private int mAttachmentPreviewMode = ATTACHMENT_PREVIEW_NONE;
        private boolean mShowFolders = false;
        private boolean mShowReplyState = false;
        private boolean mShowColorBlock = false;

        public Config setMode(int mode) {
            mMode = mode;
            return this;
        }

        public Config withGadget(int gadget) {
            mGadgetMode = gadget;
            return this;
        }

        public Config withAttachmentPreviews(int attachmentPreviewMode) {
            mAttachmentPreviewMode = attachmentPreviewMode;
            return this;
        }

        public Config showFolders() {
            mShowFolders = true;
            return this;
        }

        public Config showReplyState() {
            mShowReplyState = true;
            return this;
        }

        public Config showColorBlock() {
            mShowColorBlock = true;
            return this;
        }

        public Config updateWidth(int width) {
            mWidth = width;
            return this;
        }

        public int getWidth() {
            return mWidth;
        }

        public int getMode() {
            return mMode;
        }

        public boolean isWide() {
            return mMode == WIDE_MODE;
        }

        public int getGadgetMode() {
            return mGadgetMode;
        }

        public int getAttachmentPreviewMode() {
            return mAttachmentPreviewMode;
        }

        public boolean areFoldersVisible() {
            return mShowFolders;
        }

        public boolean isReplyStateVisible() {
            return mShowReplyState;
        }

        public boolean isColorBlockVisible() {
            return mShowColorBlock;
        }

        private int getCacheKey() {
            // hash the attributes that contribute to item height and child view geometry
            return Objects.hashCode(mWidth, mMode, mGadgetMode, mAttachmentPreviewMode,
                    mShowFolders, mShowReplyState);
        }

    }

    final int height;

    // Attachments view
    static int sAttachmentPreviewsHeights[];
    static int sAttachmentPreviewsMarginTops[];
    final int attachmentPreviewsX;
    final int attachmentPreviewsY;
    final int attachmentPreviewsWidth;

    // Checkmark.
    final int checkmarkX;
    final int checkmarkY;

    // Star.
    final int starX;
    final int starY;

    // Senders.
    final int sendersX;
    final int sendersY;
    final int sendersWidth;
    final int sendersHeight;
    final int sendersLineCount;
    final int sendersLineHeight;
    final float sendersFontSize;
    final int sendersAscent;

    // Subject.
    final int subjectX;
    final int subjectY;
    final int subjectWidth;
    final int subjectHeight;
    final int subjectLineCount;
    final float subjectFontSize;
    final int subjectAscent;

    // Folders.
    final int foldersX;
    final int foldersXEnd;
    final int foldersY;
    final int foldersHeight;
    final Typeface foldersTypeface;
    final float foldersFontSize;
    final int foldersAscent;
    final int foldersTextBottomPadding;

    // Date.
    final int dateXEnd;
    final int dateY;
    final int datePaddingLeft;
    final float dateFontSize;
    final int dateAscent;
    final int dateYBaseline;

    // Paperclip.
    final int paperclipY;
    final int paperclipPaddingLeft;

    // Color block.
    final int colorBlockX;
    final int colorBlockY;
    final int colorBlockWidth;
    final int colorBlockHeight;

    // Reply state of a conversation.
    final int replyStateX;
    final int replyStateY;

    final int contactImagesHeight;
    final int contactImagesWidth;
    final float contactImagesX;
    final float contactImagesY;

    private ConversationItemViewCoordinates(Context context, Config config) {
        final ViewGroup view = (ViewGroup) LayoutInflater.from(context).inflate(
                config.getMode() == WIDE_MODE ? R.layout.conversation_item_view_wide2 :
                    R.layout.conversation_item_view_normal2, null);

        final TextView folders = (TextView) view.findViewById(R.id.folders);
        folders.setVisibility(config.areFoldersVisible() ? View.VISIBLE : View.GONE);

        // Show/hide optional views before measure/layout call
        View attachmentPreviews = null;
        if (config.getAttachmentPreviewMode() != ATTACHMENT_PREVIEW_NONE) {
            attachmentPreviews = view.findViewById(R.id.attachment_previews);
            if (attachmentPreviews != null) {
                LayoutParams params = attachmentPreviews.getLayoutParams();
                attachmentPreviews.setVisibility(View.VISIBLE);
                params.height = getAttachmentPreviewsHeight(
                        context, config.getAttachmentPreviewMode());
                attachmentPreviews.setLayoutParams(params);
            }
        }

        View contactImagesView = view.findViewById(R.id.contact_image);
        View checkmark = view.findViewById(R.id.checkmark);

        switch (config.getGadgetMode()) {
            case GADGET_CONTACT_PHOTO:
                contactImagesView.setVisibility(View.VISIBLE);
                checkmark.setVisibility(View.GONE);
                checkmark = null;
                break;
            case GADGET_CHECKBOX:
                contactImagesView.setVisibility(View.GONE);
                checkmark.setVisibility(View.VISIBLE);
                contactImagesView = null;
                break;
            default:
                contactImagesView.setVisibility(View.GONE);
                checkmark.setVisibility(View.GONE);
                contactImagesView = null;
                checkmark = null;
                break;
        }

        final View replyState = view.findViewById(R.id.reply_state);
        replyState.setVisibility(config.isReplyStateVisible() ? View.VISIBLE : View.GONE);

        // Layout the appropriate view.
        final int widthSpec = MeasureSpec.makeMeasureSpec(config.getWidth(), MeasureSpec.EXACTLY);
        final int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        final Resources res = context.getResources();

        view.measure(widthSpec, heightSpec);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        height = view.getHeight();

        // Records coordinates.
        if (checkmark != null) {
            checkmarkX = getX(checkmark);
            checkmarkY = getY(checkmark);
        } else {
            checkmarkX = checkmarkY = 0;
        }

        // Contact images view
        if (contactImagesView != null) {
            contactImagesWidth = contactImagesView.getWidth();
            contactImagesHeight = contactImagesView.getHeight();
            contactImagesX = getX(contactImagesView);
            contactImagesY = getY(contactImagesView);
        } else {
            contactImagesX = contactImagesY = contactImagesWidth = contactImagesHeight = 0;
        }

        final View star = view.findViewById(R.id.star);
        starX = getX(star);
        starY = getY(star);

        final TextView senders = (TextView) view.findViewById(R.id.senders);
        sendersX = getX(senders);
        sendersY = getY(senders) + getLatinTopAdjustment(senders);
        sendersWidth = senders.getWidth();
        sendersHeight = senders.getHeight();
        sendersLineCount = getLineCount(senders);
        sendersLineHeight = senders.getLineHeight();
        sendersFontSize = senders.getTextSize();
        sendersAscent = (int) senders.getPaint().ascent();

        final TextView subject = (TextView) view.findViewById(R.id.subject);
        subjectX = getX(subject);
        if (config.isWide()) {
            subjectY = getY(subject) + getLatinTopAdjustment(subject);
        } else {
            subjectY = getY(subject);
        }
        subjectWidth = subject.getWidth();
        subjectHeight = subject.getHeight();
        subjectLineCount = getLineCount(subject);
        subjectFontSize = subject.getTextSize();
        subjectAscent = (int) subject.getPaint().ascent();

        if (config.areFoldersVisible()) {
            // vertically align folders min left edge with subject
            foldersX = subjectX;
            foldersXEnd = getX(folders) + folders.getWidth();
            foldersY = getY(folders);
            foldersHeight = folders.getHeight();
            foldersTypeface = folders.getTypeface();
            foldersTextBottomPadding = res
                    .getDimensionPixelSize(R.dimen.folders_text_bottom_padding);
            foldersFontSize = folders.getTextSize();
            foldersAscent = (int) folders.getPaint().ascent();
        } else {
            foldersX = 0;
            foldersXEnd = 0;
            foldersY = 0;
            foldersHeight = 0;
            foldersTypeface = null;
            foldersTextBottomPadding = 0;
            foldersFontSize = 0;
            foldersAscent = 0;
        }

        final View colorBlock = view.findViewById(R.id.color_block);
        if (config.isColorBlockVisible() && colorBlock != null) {
            colorBlockX = getX(colorBlock);
            colorBlockY = getY(colorBlock);
            colorBlockWidth = colorBlock.getWidth();
            colorBlockHeight = colorBlock.getHeight();
        } else {
            colorBlockX = colorBlockY = colorBlockWidth = colorBlockHeight = 0;
        }

        if (config.isReplyStateVisible()) {
            replyStateX = getX(replyState);
            replyStateY = getY(replyState);
        } else {
            replyStateX = replyStateY = 0;
        }

        final TextView date = (TextView) view.findViewById(R.id.date);
        dateXEnd = getX(date) + date.getWidth();
        dateY = getY(date);
        datePaddingLeft = date.getPaddingLeft();
        dateFontSize = date.getTextSize();
        dateYBaseline = dateY + getLatinTopAdjustment(date) + date.getBaseline();
        dateAscent = (int) date.getPaint().ascent();

        final View paperclip = view.findViewById(R.id.paperclip);
        paperclipY = getY(paperclip);
        paperclipPaddingLeft = paperclip.getPaddingLeft();

        if (attachmentPreviews != null) {
            attachmentPreviewsX = getX(attachmentPreviews);
            attachmentPreviewsY = getY(attachmentPreviews);
            attachmentPreviewsWidth = attachmentPreviews.getWidth();
        } else {
            attachmentPreviewsX = 0;
            attachmentPreviewsY = 0;
            attachmentPreviewsWidth = 0;
        }
    }

    /**
     * Returns a negative corrective value that you can apply to a TextView's vertical dimensions
     * that will nudge the first line of text upwards such that uppercase Latin characters are
     * truly top-aligned.
     * <p>
     * N.B. this will cause other characters to draw above the top! only use this if you have
     * adequate top margin.
     *
     */
    private static int getLatinTopAdjustment(TextView t) {
        final FontMetricsInt fmi = t.getPaint().getFontMetricsInt();
        return (fmi.top - fmi.ascent);
    }

    /**
     * Returns the mode of the header view (Wide/Normal/Narrow).
     */
    public static int getMode(Context context, int viewMode) {
        final Resources res = context.getResources();
        switch (viewMode) {
            case ViewMode.CONVERSATION_LIST:
                return res.getInteger(R.integer.conversation_list_header_mode);

            case ViewMode.SEARCH_RESULTS_LIST:
                return res.getInteger(R.integer.conversation_list_search_header_mode);

            default:
                return res.getInteger(R.integer.conversation_header_mode);
        }
    }

    /**
     * Returns the mode of the header view (Wide/Normal/Narrow).
     */
    public static int getMode(Context context, ViewMode viewMode) {
        return getMode(context, viewMode.getMode());
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
    @Deprecated
    public static int getHeight(Context context, int mode, int attachmentPreviewMode) {
        if (sConversationHeights == null) {
            refreshConversationDimens(context);
        }

        // Base height
        int result = sConversationHeights[mode];

        // Attachment previews margin top
        if (attachmentPreviewMode != ATTACHMENT_PREVIEW_NONE) {
            result += sAttachmentPreviewsMarginTops[mode];
        }

        // Attachment previews height
        result += getAttachmentPreviewsHeight(
                context, attachmentPreviewMode);

        return result;
    }

    /**
     * Refreshes the conversation heights array.
     */
    @Deprecated
    // TODO: heights are now dynamic and should be members of this class. the fixed attachment
    // heights can still be stored in a dimensional array, but should only be used as input into
    // forConfig's measure/layout
    public static void refreshConversationDimens(Context context) {
        Resources res = context.getResources();
        float density = res.getDisplayMetrics().scaledDensity;

        // Height without attachment previews
        sConversationHeights = getDensityDependentArray(
                res.getIntArray(R.array.conversation_heights_without_attachment_previews), density);

        // Attachment previews height
        sAttachmentPreviewsHeights = new int[ATTACHMENT_PREVIEW_MODE_COUNT];
        sAttachmentPreviewsHeights[ATTACHMENT_PREVIEW_TALL] = 0;
        sAttachmentPreviewsHeights[ATTACHMENT_PREVIEW_TALL] = (int) res.getDimension(
                R.dimen.attachment_preview_height_tall);
        sAttachmentPreviewsHeights[ATTACHMENT_PREVIEW_SHORT] = (int) res.getDimension(
                R.dimen.attachment_preview_height_short);

        // Attachment previews margin top
        sAttachmentPreviewsMarginTops = new int[MODE_COUNT];
        sAttachmentPreviewsMarginTops[NORMAL_MODE] = (int) res.getDimension(
                R.dimen.attachment_preview_margin_top);
        sAttachmentPreviewsMarginTops[WIDE_MODE] = (int) res.getDimension(
                R.dimen.attachment_preview_margin_top_wide);
    }

    public static int getAttachmentPreviewsHeight(Context context, int attachmentPreviewMode) {
        if (sConversationHeights == null) {
            refreshConversationDimens(context);
        }
        return sAttachmentPreviewsHeights[attachmentPreviewMode];
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
     * Returns the number of lines of this text view. Delegates to built-in TextView logic on JB+.
     */
    private static int getLineCount(TextView textView) {
        if (Utils.isRunningJellybeanOrLater()) {
            return textView.getMaxLines();
        } else {
            return Math.round(((float) textView.getHeight()) / textView.getLineHeight());
        }
    }

    /**
     * Returns the length (maximum of characters) of subject in this mode.
     */
    public static int getSendersLength(Context context, int mode, boolean hasAttachments) {
        final Resources res = context.getResources();
        if (hasAttachments) {
            return res.getIntArray(R.array.senders_with_attachment_lengths)[mode];
        } else {
            return res.getIntArray(R.array.senders_lengths)[mode];
        }
    }

    @Deprecated
    public static int getColorBlockWidth(Context context) {
        Resources res = context.getResources();
        if (COLOR_BLOCK_WIDTH <= 0) {
            COLOR_BLOCK_WIDTH = res.getDimensionPixelSize(R.dimen.color_block_width);
        }
        return COLOR_BLOCK_WIDTH;
    }

    @Deprecated
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
    @Deprecated
    public static int getFoldersWidth(Context context, int mode) {
        Resources res = context.getResources();
        if (TOTAL_FOLDER_WIDTH <= 0) {
            TOTAL_FOLDER_WIDTH = res.getDimensionPixelSize(R.dimen.max_total_folder_width);
            TOTAL_FOLDER_WIDTH_WIDE = res.getDimensionPixelSize(
                    R.dimen.max_total_folder_width_wide);
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
    public static ConversationItemViewCoordinates forConfig(Context context, Config config,
            SparseArray<ConversationItemViewCoordinates> cache) {
        final int cacheKey = config.getCacheKey();
        ConversationItemViewCoordinates coordinates = cache.get(cacheKey);
        if (coordinates != null) {
            return coordinates;
        }

        coordinates = new ConversationItemViewCoordinates(context, config);
        cache.put(cacheKey, coordinates);
        return coordinates;
    }

    /**
     * Return the minimum width of a folder cell with no text. Essentially this is the left+right
     * intra-cell margin within cells.
     *
     */
    public static int getFolderCellWidth(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.folder_cell_width);
    }

    public static boolean isWideMode(int mode) {
        return mode == WIDE_MODE;
    }

}
