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

import com.google.common.annotations.VisibleForTesting;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.Animator.AnimatorListener;
import android.content.ClipData;
import android.content.Context;
import android.content.ClipData.Item;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout.Alignment;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.format.DateUtils;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.View.DragShadowBuilder;
import android.view.View.MeasureSpec;
import android.view.animation.DecelerateInterpolator;
import android.widget.Checkable;
import android.widget.ListView;

import com.android.mail.R;
import com.android.mail.browse.ConversationItemViewModel.SenderFragment;
import com.android.mail.perf.Timer;
import com.android.mail.providers.Address;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.ui.ConversationSelectionSet;
import com.android.mail.ui.DragListener;
import com.android.mail.ui.FolderDisplayer;
import com.android.mail.ui.ViewMode;
import com.android.mail.utils.Utils;

public class ConversationItemView extends View {
    // Timer.
    private static int sLayoutCount = 0;
    private static Timer sTimer; // Create the sTimer here if you need to do perf analysis.
    private static final int PERF_LAYOUT_ITERATIONS = 50;
    private static final String PERF_TAG_LAYOUT = "CCHV.layout";
    private static final String PERF_TAG_CALCULATE_TEXTS_BITMAPS = "CCHV.txtsbmps";
    private static final String PERF_TAG_CALCULATE_SENDER_SUBJECT = "CCHV.sendersubj";
    private static final String PERF_TAG_CALCULATE_FOLDERS = "CCHV.folders";
    private static final String PERF_TAG_CALCULATE_COORDINATES = "CCHV.coordinates";

    // Static bitmaps.
    private static Bitmap CHECKMARK_OFF;
    private static Bitmap CHECKMARK_ON;
    private static Bitmap STAR_OFF;
    private static Bitmap STAR_ON;
    private static Bitmap ATTACHMENT;
    private static Bitmap ONLY_TO_ME;
    private static Bitmap TO_ME_AND_OTHERS;
    private static Bitmap IMPORTANT_ONLY_TO_ME;
    private static Bitmap IMPORTANT_TO_ME_AND_OTHERS;
    private static Bitmap IMPORTANT_TO_OTHERS;
    private static Bitmap DATE_BACKGROUND;
    private static Bitmap STATE_REPLIED;
    private static Bitmap STATE_FORWARDED;
    private static Bitmap STATE_REPLIED_AND_FORWARDED;
    private static Bitmap STATE_CALENDAR_INVITE;

    // Static colors.
    private static int DEFAULT_TEXT_COLOR;
    private static int ACTIVATED_TEXT_COLOR;
    private static int LIGHT_TEXT_COLOR;
    private static int DRAFT_TEXT_COLOR;
    private static int SUBJECT_TEXT_COLOR_READ;
    private static int SUBJECT_TEXT_COLOR_UNREAD;
    private static int SNIPPET_TEXT_COLOR_READ;
    private static int SNIPPET_TEXT_COLOR_UNREAD;
    private static int SENDERS_TEXT_COLOR_READ;
    private static int SENDERS_TEXT_COLOR_UNREAD;
    private static int DATE_TEXT_COLOR_READ;
    private static int DATE_TEXT_COLOR_UNREAD;
    private static int DATE_BACKGROUND_PADDING_LEFT;
    private static int TOUCH_SLOP;
    private static int sDateBackgroundHeight;
    private static int sStandardScaledDimen;
    private static int sUndoAnimationDuration;
    private static CharacterStyle sLightTextStyle;
    private static CharacterStyle sNormalTextStyle;

    // Static paints.
    private static TextPaint sPaint = new TextPaint();
    private static TextPaint sFoldersPaint = new TextPaint();

    // Backgrounds for different states.
    private final SparseArray<Drawable> mBackgrounds = new SparseArray<Drawable>();

    // Dimensions and coordinates.
    private int mViewWidth = -1;
    private int mMode = -1;
    private int mDateX;
    private int mPaperclipX;
    private int mFoldersXEnd;
    private int mSendersWidth;

    /** Whether we're running under test mode. */
    private boolean mTesting = false;
    /** Whether we are on a tablet device or not */
    private final boolean mTabletDevice;

    @VisibleForTesting
    ConversationItemViewCoordinates mCoordinates;

    private final Context mContext;

    public ConversationItemViewModel mHeader;
    private ViewMode mViewMode;
    private boolean mDownEvent;
    private boolean mChecked = false;
    private static int sFadedActivatedColor = -1;
    private ConversationSelectionSet mSelectedConversationSet;
    private Folder mDisplayedFolder;
    private boolean mPriorityMarkersEnabled;
    private int mAnimatedHeight = -1;
    private boolean mCheckboxesEnabled;
    private CheckForTap mPendingCheckForTap;
    private CheckForLongPress mPendingCheckForLongPress;
    private boolean mSwipeEnabled;
    private int mLastTouchX;
    private int mLastTouchY;
    private DragListener mDragListener;
    private static Bitmap MORE_FOLDERS;

    static {
        sPaint.setAntiAlias(true);
        sFoldersPaint.setAntiAlias(true);
    }


    /**
     * Handles displaying folders in a conversation header view.
     */
    static class ConversationItemFolderDisplayer extends FolderDisplayer {
        // Maximum number of folders to be displayed.
        private static final int MAX_DISPLAYED_FOLDERS_COUNT = 4;

        private int mFoldersCount;
        private boolean mHasMoreFolders;

        public ConversationItemFolderDisplayer(Context context) {
            super(context);
        }

        @Override
        public void loadConversationFolders(String rawFolders, Folder ignoreFolder) {
            super.loadConversationFolders(rawFolders, ignoreFolder);

            mFoldersCount = mFoldersSortedSet.size();
            mHasMoreFolders = mFoldersCount > MAX_DISPLAYED_FOLDERS_COUNT;
            mFoldersCount = Math.min(mFoldersCount, MAX_DISPLAYED_FOLDERS_COUNT);
        }

        public boolean hasVisibleFolders() {
            return mFoldersCount > 0;
        }

        private int measureFolders(int mode) {
            int availableSpace = ConversationItemViewCoordinates.getFoldersWidth(mContext, mode);
            int cellSize = ConversationItemViewCoordinates.getFolderCellWidth(mContext, mode,
                    mFoldersCount);

            int totalWidth = 0;
            for (Folder f : mFoldersSortedSet) {
                final String folderString = f.name;
                int width = (int) sFoldersPaint.measureText(folderString) + cellSize;
                if (width % cellSize != 0) {
                    width += cellSize - (width % cellSize);
                }
                totalWidth += width;
                if (totalWidth > availableSpace) {
                    break;
                }
            }

            return totalWidth;
        }

        public void drawFolders(Canvas canvas, ConversationItemViewCoordinates coordinates,
                int foldersXEnd, int mode) {
            if (mFoldersCount == 0) {
                return;
            }

            int xEnd = foldersXEnd;
            int y = coordinates.foldersY - coordinates.foldersAscent;
            int height = coordinates.foldersHeight;
            int topPadding = coordinates.foldersTopPadding;
            int ascent = coordinates.foldersAscent;
            sFoldersPaint.setTextSize(coordinates.foldersFontSize);

            // Initialize space and cell size based on the current mode.
            int availableSpace = ConversationItemViewCoordinates.getFoldersWidth(mContext, mode);
            int averageWidth = availableSpace / mFoldersCount;
            int cellSize = ConversationItemViewCoordinates.getFolderCellWidth(mContext, mode,
                    mFoldersCount);

            // First pass to calculate the starting point.
            int totalWidth = measureFolders(mode);
            int xStart = xEnd - Math.min(availableSpace, totalWidth);

            // Second pass to draw folders.
            for (Folder f : mFoldersSortedSet) {
                final String folderString = f.name;
                final int fgColor = f.getForegroundColor(mDefaultFgColor);
                final int bgColor = f.getBackgroundColor(mDefaultBgColor);
                int width = cellSize;
                boolean labelTooLong = false;
                width = (int) sFoldersPaint.measureText(folderString) + cellSize;
                if (width % cellSize != 0) {
                    width += cellSize - (width % cellSize);
                }
                if (totalWidth > availableSpace && width > averageWidth) {
                    width = averageWidth;
                    labelTooLong = true;
                }

                // TODO (mindyp): how to we get this?
                final boolean isMuted = false;
                     //   labelValues.folderId == sGmail.getFolderMap(mAccount).getFolderIdIgnored();

                // Draw the box.
                sFoldersPaint.setColor(bgColor);
                sFoldersPaint.setStyle(isMuted ? Paint.Style.STROKE : Paint.Style.FILL_AND_STROKE);
                canvas.drawRect(xStart, y + ascent, xStart + width, y + ascent + height,
                        sFoldersPaint);

                // Draw the text.
                int padding = getPadding(width, (int) sFoldersPaint.measureText(folderString));
                if (labelTooLong) {
                    TextPaint shortPaint = new TextPaint();
                    shortPaint.setColor(fgColor);
                    shortPaint.setTextSize(coordinates.foldersFontSize);
                    padding = cellSize / 2;
                    int rightBorder = xStart + width - padding;
                    Shader shader = new LinearGradient(rightBorder - padding, y, rightBorder, y,
                            fgColor, Utils.getTransparentColor(fgColor), Shader.TileMode.CLAMP);
                    shortPaint.setShader(shader);
                    canvas.drawText(folderString, xStart + padding, y + topPadding, shortPaint);
                } else {
                    sFoldersPaint.setColor(fgColor);
                    canvas.drawText(folderString, xStart + padding, y + topPadding, sFoldersPaint);
                }

                availableSpace -= width;
                xStart += width;
                if (availableSpace <= 0 && mHasMoreFolders) {
                    canvas.drawBitmap(MORE_FOLDERS, xEnd, y + ascent, sFoldersPaint);
                    return;
                }
            }
        }
    }

    /**
     * Helpers function to align an element in the center of a space.
     */
    private static int getPadding(int space, int length) {
        return (space - length) / 2;
    }

    public ConversationItemView(Context context, String account) {
        super(context);
        mContext = context.getApplicationContext();
        mTabletDevice = Utils.useTabletUI(mContext);

        Resources res = mContext.getResources();

        if (CHECKMARK_OFF == null) {
            // Initialize static bitmaps.
            CHECKMARK_OFF = BitmapFactory.decodeResource(res,
                    R.drawable.btn_check_off_normal_holo_light);
            CHECKMARK_ON = BitmapFactory.decodeResource(res,
                    R.drawable.btn_check_on_normal_holo_light);
            STAR_OFF = BitmapFactory.decodeResource(res,
                    R.drawable.btn_star_off_normal_email_holo_light);
            STAR_ON = BitmapFactory.decodeResource(res,
                    R.drawable.btn_star_on_normal_email_holo_light);
            ONLY_TO_ME = BitmapFactory.decodeResource(res, R.drawable.ic_email_caret_double);
            TO_ME_AND_OTHERS = BitmapFactory.decodeResource(res, R.drawable.ic_email_caret_single);
            IMPORTANT_ONLY_TO_ME = BitmapFactory.decodeResource(res,
                    R.drawable.ic_email_caret_double_important_unread);
            IMPORTANT_TO_ME_AND_OTHERS = BitmapFactory.decodeResource(res,
                    R.drawable.ic_email_caret_single_important_unread);
            IMPORTANT_TO_OTHERS = BitmapFactory.decodeResource(res,
                    R.drawable.ic_email_caret_none_important_unread);
            ATTACHMENT = BitmapFactory.decodeResource(res, R.drawable.ic_attachment_holo_light);
            MORE_FOLDERS = BitmapFactory.decodeResource(res, R.drawable.ic_folders_more);
            DATE_BACKGROUND = BitmapFactory.decodeResource(res, R.drawable.folder_bg_holo_light);
            STATE_REPLIED =
                    BitmapFactory.decodeResource(res, R.drawable.ic_badge_reply_holo_light);
            STATE_FORWARDED =
                    BitmapFactory.decodeResource(res, R.drawable.ic_badge_forward_holo_light);
            STATE_REPLIED_AND_FORWARDED =
                    BitmapFactory.decodeResource(res, R.drawable.ic_badge_reply_forward_holo_light);
            STATE_CALENDAR_INVITE =
                    BitmapFactory.decodeResource(res, R.drawable.ic_badge_invite_holo_light);

            // Initialize colors.
            DEFAULT_TEXT_COLOR = res.getColor(R.color.default_text_color);
            ACTIVATED_TEXT_COLOR = res.getColor(android.R.color.white);
            LIGHT_TEXT_COLOR = res.getColor(R.color.light_text_color);
            DRAFT_TEXT_COLOR = res.getColor(R.color.drafts);
            SUBJECT_TEXT_COLOR_READ = res.getColor(R.color.subject_text_color_read);
            SUBJECT_TEXT_COLOR_UNREAD = res.getColor(R.color.subject_text_color_unread);
            SNIPPET_TEXT_COLOR_READ = res.getColor(R.color.snippet_text_color_read);
            SNIPPET_TEXT_COLOR_UNREAD = res.getColor(R.color.snippet_text_color_unread);
            SENDERS_TEXT_COLOR_READ = res.getColor(R.color.senders_text_color_read);
            SENDERS_TEXT_COLOR_UNREAD = res.getColor(R.color.senders_text_color_unread);
            DATE_TEXT_COLOR_READ = res.getColor(R.color.date_text_color_read);
            DATE_TEXT_COLOR_UNREAD = res.getColor(R.color.date_text_color_unread);
            DATE_BACKGROUND_PADDING_LEFT = res
                    .getDimensionPixelSize(R.dimen.date_background_padding_left);
            TOUCH_SLOP = res.getDimensionPixelSize(R.dimen.touch_slop);
            sDateBackgroundHeight = res.getDimensionPixelSize(R.dimen.date_background_height);
            sStandardScaledDimen = res.getDimensionPixelSize(R.dimen.standard_scaled_dimen);
            sUndoAnimationDuration = res.getInteger(R.integer.undo_animation_duration);

            // Initialize static color.
            sNormalTextStyle = new StyleSpan(Typeface.NORMAL);
            sLightTextStyle = new ForegroundColorSpan(LIGHT_TEXT_COLOR);
        }
    }

    public void bind(Cursor cursor, ViewMode viewMode, ConversationSelectionSet set, Folder folder,
            boolean checkboxesDisabled, boolean swipeEnabled, DragListener dragListener) {
        bind(ConversationItemViewModel.forCursor(cursor), viewMode, set, folder,
                checkboxesDisabled, swipeEnabled, dragListener);
    }

    public void bind(Conversation conversation, ViewMode viewMode, ConversationSelectionSet set,
            Folder folder, boolean checkboxesDisabled, boolean swipeEnabled,
            DragListener dragListener) {
        bind(ConversationItemViewModel.forConversation(conversation), viewMode, set, folder,
                checkboxesDisabled, swipeEnabled, dragListener);
    }

    private void bind(ConversationItemViewModel header, ViewMode viewMode,
            ConversationSelectionSet set, Folder folder, boolean checkboxesDisabled,
            boolean swipeEnabled, DragListener dragListener) {
        mViewMode = viewMode;
        mHeader = header;
        mSelectedConversationSet = set;
        mDisplayedFolder = folder;
        mCheckboxesEnabled = !checkboxesDisabled;
        mSwipeEnabled = swipeEnabled;
        mDragListener = dragListener;
        setContentDescription(mHeader.getContentDescription(mContext));
        requestLayout();
    }

    /**
     * Get the Conversation object associated with this view.
     */
    public Conversation getConversation() {
        return mHeader.conversation;
    }

    /**
     * Sets the mode. Only used for testing.
     */
    @VisibleForTesting
    void setMode(int mode) {
        mMode = mode;
        mTesting = true;
    }

    private static void startTimer(String tag) {
        if (sTimer != null) {
            sTimer.start(tag);
        }
    }

    private static void pauseTimer(String tag) {
        if (sTimer != null) {
            sTimer.pause(tag);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        startTimer(PERF_TAG_LAYOUT);

        super.onLayout(changed, left, top, right, bottom);

        int width = right - left;
        if (width != mViewWidth) {
            mViewWidth = width;
            if (!mTesting) {
                mMode = ConversationItemViewCoordinates.getMode(mContext, mViewMode);
            }
        }
        mHeader.viewWidth = mViewWidth;
        Resources res = getResources();
        mHeader.standardScaledDimen = res.getDimensionPixelOffset(R.dimen.standard_scaled_dimen);
        if (mHeader.standardScaledDimen != sStandardScaledDimen) {
            // Large Text has been toggle on/off. Update the static dimens.
            sStandardScaledDimen = mHeader.standardScaledDimen;
            ConversationItemViewCoordinates.refreshConversationHeights(mContext);
            sDateBackgroundHeight = res.getDimensionPixelSize(R.dimen.date_background_height);
        }
        mCoordinates = ConversationItemViewCoordinates.forWidth(mContext, mViewWidth, mMode,
                mHeader.standardScaledDimen);
        calculateTextsAndBitmaps();
        calculateCoordinates();
        mHeader.validate(mContext);

        pauseTimer(PERF_TAG_LAYOUT);
        if (sTimer != null && ++sLayoutCount >= PERF_LAYOUT_ITERATIONS) {
            sTimer.dumpResults();
            sTimer = new Timer();
            sLayoutCount = 0;
        }
    }

    @Override
    public void setBackgroundResource(int resourceId) {
        Drawable drawable = mBackgrounds.get(resourceId);
        if (drawable == null) {
            drawable = getResources().getDrawable(resourceId);
            mBackgrounds.put(resourceId, drawable);
        }
        if (getBackground() != drawable) {
            super.setBackgroundDrawable(drawable);
        }
    }

    private void calculateTextsAndBitmaps() {
        startTimer(PERF_TAG_CALCULATE_TEXTS_BITMAPS);
        if (mSelectedConversationSet != null) {
            mChecked = mSelectedConversationSet.contains(mHeader.conversation);
        }
        // Update font color.
        int fontColor = getFontColor(DEFAULT_TEXT_COLOR);
        boolean fontChanged = false;
        if (mHeader.fontColor != fontColor) {
            fontChanged = true;
            mHeader.fontColor = fontColor;
        }

        boolean isUnread = mHeader.unread;

        final boolean checkboxEnabled = mCheckboxesEnabled;
        if (mHeader.checkboxVisible != checkboxEnabled) {
            mHeader.checkboxVisible = checkboxEnabled;
        }

        // Update background.
        updateBackground(isUnread);

        if (mHeader.isLayoutValid(mContext)) {
            // Relayout subject if font color has changed.
            if (fontChanged) {
                createSubjectSpans(isUnread);
                layoutSubject();
            }
            pauseTimer(PERF_TAG_CALCULATE_TEXTS_BITMAPS);
            return;
        }

        startTimer(PERF_TAG_CALCULATE_FOLDERS);

        // Initialize folder displayer.
        if (mCoordinates.showFolders) {
            mHeader.folderDisplayer = new ConversationItemFolderDisplayer(mContext);
            mHeader.folderDisplayer.loadConversationFolders(mHeader.rawFolders, mDisplayedFolder);
        }

        pauseTimer(PERF_TAG_CALCULATE_FOLDERS);

        // Star.
        mHeader.starBitmap = mHeader.starred ? STAR_ON : STAR_OFF;

        // Date.
        mHeader.dateText = DateUtils.getRelativeTimeSpanString(mContext,
                mHeader.conversation.dateMs).toString();

        // Paper clip icon.
        mHeader.paperclip = null;
        if (mHeader.conversation.hasAttachments) {
            mHeader.paperclip = ATTACHMENT;
        }
        // Personal level.
        mHeader.personalLevelBitmap = null;
        if (mCoordinates.showPersonalLevel) {
            int personalLevel = mHeader.personalLevel;
            final boolean isImportant =
                    mHeader.priority == UIProvider.ConversationPriority.IMPORTANT;
            // TODO(mindyp): get whether importance indicators are enabled
            // mPriorityMarkersEnabled =
            // persistence.getPriorityInboxArrowsEnabled(mContext, mAccount);
            mPriorityMarkersEnabled = true;
            boolean useImportantMarkers = isImportant && mPriorityMarkersEnabled;

            if (personalLevel == UIProvider.ConversationPersonalLevel.ONLY_TO_ME) {
                mHeader.personalLevelBitmap = useImportantMarkers ? IMPORTANT_ONLY_TO_ME
                        : ONLY_TO_ME;
            } else if (personalLevel == UIProvider.ConversationPersonalLevel.TO_ME_AND_OTHERS) {
                mHeader.personalLevelBitmap = useImportantMarkers ? IMPORTANT_TO_ME_AND_OTHERS
                        : TO_ME_AND_OTHERS;
            } else if (useImportantMarkers) {
                mHeader.personalLevelBitmap = IMPORTANT_TO_OTHERS;
            }
        }

        startTimer(PERF_TAG_CALCULATE_SENDER_SUBJECT);

        // Subject.
        createSubjectSpans(isUnread);

        // Parse senders fragments.
        parseSendersFragments(isUnread);

        pauseTimer(PERF_TAG_CALCULATE_SENDER_SUBJECT);
        pauseTimer(PERF_TAG_CALCULATE_TEXTS_BITMAPS);
    }

    private void createSubjectSpans(boolean isUnread) {
        final String subject = filterTag(mHeader.conversation.subject);
        final String snippet = mHeader.conversation.snippet;
        int subjectColor = isUnread ? SUBJECT_TEXT_COLOR_UNREAD : SUBJECT_TEXT_COLOR_READ;
        int snippetColor = isUnread ? SNIPPET_TEXT_COLOR_UNREAD : SNIPPET_TEXT_COLOR_READ;
        mHeader.subjectText = new SpannableStringBuilder(mContext.getString(
                R.string.subject_and_snippet, subject, snippet));
        if (isUnread) {
            mHeader.subjectText.setSpan(new StyleSpan(Typeface.BOLD), 0, subject.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int fontColor = getFontColor(subjectColor);
        mHeader.subjectText.setSpan(new ForegroundColorSpan(fontColor), 0,
                subject.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        fontColor = getFontColor(snippetColor);
        mHeader.subjectText.setSpan(new ForegroundColorSpan(fontColor), subject.length() + 1,
                mHeader.subjectText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private int getFontColor(int defaultColor) {
        return isActivated() && mTabletDevice ? ACTIVATED_TEXT_COLOR
                : defaultColor;
    }

    private void layoutSubject() {
        sPaint.setTextSize(mCoordinates.subjectFontSize);
        sPaint.setColor(mHeader.fontColor);
        mHeader.subjectLayout = new StaticLayout(mHeader.subjectText, sPaint,
                mCoordinates.subjectWidth, Alignment.ALIGN_NORMAL, 1, 0, true);
        if (mCoordinates.subjectLineCount < mHeader.subjectLayout.getLineCount()) {
            int end = mHeader.subjectLayout.getLineEnd(mCoordinates.subjectLineCount - 1);
            mHeader.subjectLayout = new StaticLayout(mHeader.subjectText.subSequence(0, end),
                    sPaint, mCoordinates.subjectWidth, Alignment.ALIGN_NORMAL, 1, 0, true);
        }
    }

    /**
     * Parses senders text into small fragments.
     */
    private void parseSendersFragments(boolean isUnread) {
        if (TextUtils.isEmpty(mHeader.conversation.senders)) {
            return;
        }
        mHeader.sendersText = formatSenders(mHeader.conversation.senders);
        mHeader.addSenderFragment(0, mHeader.sendersText.length(), sNormalTextStyle, true);
    }

    private String formatSenders(String sendersString) {
        String[] senders = TextUtils.split(sendersString, Address.ADDRESS_DELIMETER);
        String[] namesOnly = new String[senders.length];
        Rfc822Token[] senderTokens;
        String display;
        for (int i = 0; i < senders.length; i++) {
            senderTokens = Rfc822Tokenizer.tokenize(senders[i]);
            if (senderTokens != null && senderTokens.length > 0) {
                display = senderTokens[0].getName();
                if (TextUtils.isEmpty(display)) {
                    display = senderTokens[0].getAddress();
                }
                namesOnly[i] = display;
            }
        }
        return TextUtils.join(Address.ADDRESS_DELIMETER + " ", namesOnly);
    }

    private boolean canFitFragment(int width, int line, int fixedWidth) {
        if (line == mCoordinates.sendersLineCount) {
            return width + fixedWidth <= mSendersWidth;
        } else {
            return width <= mSendersWidth;
        }
    }

    private void calculateCoordinates() {
        startTimer(PERF_TAG_CALCULATE_COORDINATES);

        sPaint.setTextSize(mCoordinates.dateFontSize);
        sPaint.setTypeface(Typeface.DEFAULT);
        mDateX = mCoordinates.dateXEnd - (int) sPaint.measureText(mHeader.dateText);

        mPaperclipX = mDateX - ATTACHMENT.getWidth();

        int cellWidth = mContext.getResources().getDimensionPixelSize(R.dimen.folder_cell_width);

        if (ConversationItemViewCoordinates.isWideMode(mMode)) {
            // Folders are displayed above the date.
            mFoldersXEnd = mCoordinates.dateXEnd;
            // In wide mode, the end of the senders should align with
            // the start of the subject and is based on a max width.
            mSendersWidth = mCoordinates.sendersWidth;
        } else {
            // In normal mode, the width is based on where the folders or date
            // (or attachment icon) start.
            if (mCoordinates.showFolders) {
                if (mHeader.paperclip != null) {
                    mFoldersXEnd = mPaperclipX;
                } else {
                    mFoldersXEnd = mDateX - cellWidth / 2;
                }
                mSendersWidth = mFoldersXEnd - mCoordinates.sendersX - 2 * cellWidth;
                if (mHeader.folderDisplayer.hasVisibleFolders()) {
                    mSendersWidth -= ConversationItemViewCoordinates.getFoldersWidth(mContext,
                            mMode);
                }
            } else {
                int dateAttachmentStart = 0;
                // Have this end near the paperclip or date, not the folders.
                if (mHeader.paperclip != null) {
                    dateAttachmentStart = mPaperclipX;
                } else {
                    dateAttachmentStart = mDateX;
                }
                mSendersWidth = dateAttachmentStart - mCoordinates.sendersX - cellWidth;
            }
        }

        if (mHeader.isLayoutValid(mContext)) {
            pauseTimer(PERF_TAG_CALCULATE_COORDINATES);
            return;
        }

        // Layout subject.
        layoutSubject();

        // First pass to calculate width of each fragment.
        int totalWidth = 0;
        int fixedWidth = 0;
        sPaint.setTextSize(mCoordinates.sendersFontSize);
        sPaint.setTypeface(Typeface.DEFAULT);
        for (SenderFragment senderFragment : mHeader.senderFragments) {
            CharacterStyle style = senderFragment.style;
            int start = senderFragment.start;
            int end = senderFragment.end;
            style.updateDrawState(sPaint);
            senderFragment.width = (int) sPaint.measureText(mHeader.sendersText, start, end);
            boolean isFixed = senderFragment.isFixed;
            if (isFixed) {
                fixedWidth += senderFragment.width;
            }
            totalWidth += senderFragment.width;
        }

        // Second pass to layout each fragment.
        int sendersY = mCoordinates.sendersY - mCoordinates.sendersAscent;
        if (!ConversationItemViewCoordinates.displaySendersInline(mMode)) {
            sendersY += totalWidth <= mSendersWidth ? mCoordinates.sendersLineHeight / 2 : 0;
        }
        totalWidth = 0;
        int currentLine = 1;
        boolean ellipsize = false;
        for (SenderFragment senderFragment : mHeader.senderFragments) {
            CharacterStyle style = senderFragment.style;
            int start = senderFragment.start;
            int end = senderFragment.end;
            int width = senderFragment.width;
            boolean isFixed = senderFragment.isFixed;
            style.updateDrawState(sPaint);

            // No more width available, we'll only show fixed fragments.
            if (ellipsize && !isFixed) {
                senderFragment.shouldDisplay = false;
                continue;
            }

            // New line and ellipsize text if needed.
            senderFragment.ellipsizedText = null;
            if (isFixed) {
                fixedWidth -= width;
            }
            if (!canFitFragment(totalWidth + width, currentLine, fixedWidth)) {
                // The text is too long, new line won't help. We have to
                // ellipsize text.
                if (totalWidth == 0) {
                    ellipsize = true;
                } else {
                    // New line.
                    if (currentLine < mCoordinates.sendersLineCount) {
                        currentLine++;
                        sendersY += mCoordinates.sendersLineHeight;
                        totalWidth = 0;
                        // The text is still too long, we have to ellipsize
                        // text.
                        if (totalWidth + width > mSendersWidth) {
                            ellipsize = true;
                        }
                    } else {
                        ellipsize = true;
                    }
                }

                if (ellipsize) {
                    width = mSendersWidth - totalWidth;
                    // No more new line, we have to reserve width for fixed
                    // fragments.
                    if (currentLine == mCoordinates.sendersLineCount) {
                        width -= fixedWidth;
                    }
                    senderFragment.ellipsizedText = TextUtils.ellipsize(
                            mHeader.sendersText.substring(start, end), sPaint, width,
                            TruncateAt.END).toString();
                    width = (int) sPaint.measureText(senderFragment.ellipsizedText);
                }
            }
            senderFragment.x = mCoordinates.sendersX + totalWidth;
            senderFragment.y = sendersY;
            senderFragment.shouldDisplay = true;
            totalWidth += width;
        }

        pauseTimer(PERF_TAG_CALCULATE_COORDINATES);
    }

    /**
     * If the subject contains the tag of a mailing-list (text surrounded with
     * []), return the subject with that tag ellipsized, e.g.
     * "[android-gmail-team] Hello" -> "[andr...] Hello"
     */
    private String filterTag(String subject) {
        String result = subject;
        String formatString = getContext().getResources().getString(R.string.filtered_tag);
        if (!TextUtils.isEmpty(subject) && subject.charAt(0) == '[') {
            int end = subject.indexOf(']');
            if (end > 0) {
                String tag = subject.substring(1, end);
                result = String.format(formatString, Utils.ellipsize(tag, 7),
                        subject.substring(end + 1));
            }
        }
        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mAnimatedHeight == -1) {
            int width = measureWidth(widthMeasureSpec);
            int height = measureHeight(heightMeasureSpec,
                    ConversationItemViewCoordinates.getMode(mContext, mViewMode));
            setMeasuredDimension(width, height);
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mAnimatedHeight);
        }
    }

    /**
     * Determine the width of this view.
     *
     * @param measureSpec A measureSpec packed into an int
     * @return The width of the view, honoring constraints from measureSpec
     */
    private int measureWidth(int measureSpec) {
        int result = 0;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            // We were told how big to be
            result = specSize;
        } else {
            // Measure the text
            result = mViewWidth;
            if (specMode == MeasureSpec.AT_MOST) {
                // Respect AT_MOST value if that was what is called for by
                // measureSpec
                result = Math.min(result, specSize);
            }
        }
        return result;
    }

    /**
     * Determine the height of this view.
     *
     * @param measureSpec A measureSpec packed into an int
     * @param mode The current mode of this view
     * @return The height of the view, honoring constraints from measureSpec
     */
    private int measureHeight(int measureSpec, int mode) {
        int result = 0;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            // We were told how big to be
            result = specSize;
        } else {
            // Measure the text
            result = ConversationItemViewCoordinates.getHeight(mContext, mode);
            if (specMode == MeasureSpec.AT_MOST) {
                // Respect AT_MOST value if that was what is called for by
                // measureSpec
                result = Math.min(result, specSize);
            }
        }
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Check mark.
        if (mHeader.checkboxVisible) {
            Bitmap checkmark = mChecked ? CHECKMARK_ON : CHECKMARK_OFF;
            canvas.drawBitmap(checkmark, mCoordinates.checkmarkX, mCoordinates.checkmarkY, sPaint);
        }

        // Personal Level.
        if (mCoordinates.showPersonalLevel && mHeader.personalLevelBitmap != null) {
            canvas.drawBitmap(mHeader.personalLevelBitmap, mCoordinates.personalLevelX,
                    mCoordinates.personalLevelY, sPaint);
        }

        // Senders.
        boolean isUnread = mHeader.unread;
        sPaint.setTextSize(mCoordinates.sendersFontSize);
        sPaint.setTypeface(isUnread ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        int sendersColor = getFontColor(isUnread ? SENDERS_TEXT_COLOR_UNREAD
                : SENDERS_TEXT_COLOR_READ);
        sPaint.setColor(sendersColor);
        for (SenderFragment fragment : mHeader.senderFragments) {
            if (fragment.shouldDisplay) {
                fragment.style.updateDrawState(sPaint);
                if (fragment.ellipsizedText != null) {
                    canvas.drawText(fragment.ellipsizedText, fragment.x, fragment.y, sPaint);
                } else {
                    canvas.drawText(mHeader.sendersText, fragment.start, fragment.end, fragment.x,
                            fragment.y, sPaint);
                }
            }
        }

        // Subject.
        sPaint.setTextSize(mCoordinates.subjectFontSize);
        sPaint.setTypeface(Typeface.DEFAULT);
        sPaint.setColor(mHeader.fontColor);
        canvas.save();
        canvas.translate(mCoordinates.subjectX,
                mCoordinates.subjectY + mHeader.subjectLayout.getTopPadding());
        mHeader.subjectLayout.draw(canvas);
        canvas.restore();

        // Folders.
        if (mCoordinates.showFolders) {
            mHeader.folderDisplayer.drawFolders(canvas, mCoordinates, mFoldersXEnd, mMode);
        }

        // Date background: shown when there is an attachment or a visible
        // folder.
        if (!isActivated()
                && mHeader.conversation.hasAttachments
                && ConversationItemViewCoordinates.showAttachmentBackground(mMode)) {
            mHeader.dateBackground = DATE_BACKGROUND;
            int leftOffset = (mHeader.conversation.hasAttachments ? mPaperclipX : mDateX)
                    - DATE_BACKGROUND_PADDING_LEFT;
            int top = mCoordinates.showFolders ? mCoordinates.foldersY : mCoordinates.dateY;
            Rect src = new Rect(0, 0, mHeader.dateBackground.getWidth(), mHeader.dateBackground
                    .getHeight());
            Rect dst = new Rect(leftOffset, top, mViewWidth, top + sDateBackgroundHeight);
            canvas.drawBitmap(mHeader.dateBackground, src, dst, sPaint);
        } else {
            mHeader.dateBackground = null;
        }

        // Draw the reply state. Draw nothing if neither replied nor forwarded.
        if (mCoordinates.showReplyState) {
            if (mHeader.hasBeenRepliedTo && mHeader.hasBeenForwarded) {
                canvas.drawBitmap(STATE_REPLIED_AND_FORWARDED, mCoordinates.replyStateX,
                        mCoordinates.replyStateY, null);
            } else if (mHeader.hasBeenRepliedTo) {
                canvas.drawBitmap(STATE_REPLIED, mCoordinates.replyStateX,
                        mCoordinates.replyStateY, null);
            } else if (mHeader.hasBeenForwarded) {
                canvas.drawBitmap(STATE_FORWARDED, mCoordinates.replyStateX,
                        mCoordinates.replyStateY, null);
            } else if (mHeader.isInvite) {
                canvas.drawBitmap(STATE_CALENDAR_INVITE, mCoordinates.replyStateX,
                        mCoordinates.replyStateY, null);
            }
        }

        // Date.
        sPaint.setTextSize(mCoordinates.dateFontSize);
        sPaint.setTypeface(Typeface.DEFAULT);
        sPaint.setColor(isUnread ? DATE_TEXT_COLOR_UNREAD : DATE_TEXT_COLOR_READ);
        drawText(canvas, mHeader.dateText, mDateX, mCoordinates.dateY - mCoordinates.dateAscent,
                sPaint);

        // Paper clip icon.
        if (mHeader.paperclip != null) {
            canvas.drawBitmap(mHeader.paperclip, mPaperclipX, mCoordinates.paperclipY, sPaint);
        }

        if (mHeader.faded) {
            int fadedColor = -1;
            if (sFadedActivatedColor == -1) {
                sFadedActivatedColor = mContext.getResources().getColor(
                        R.color.faded_activated_conversation_header);
            }
            fadedColor = sFadedActivatedColor;
            int restoreState = canvas.save();
            Rect bounds = canvas.getClipBounds();
            canvas.clipRect(bounds.left, bounds.top, bounds.right
                    - mContext.getResources().getDimensionPixelSize(R.dimen.triangle_width),
                    bounds.bottom);
            canvas.drawARGB(Color.alpha(fadedColor), Color.red(fadedColor),
                    Color.green(fadedColor), Color.blue(fadedColor));
            canvas.restoreToCount(restoreState);
        }

        // Star.
        canvas.drawBitmap(mHeader.starBitmap, mCoordinates.starX, mCoordinates.starY, sPaint);
    }

    private void drawText(Canvas canvas, CharSequence s, int x, int y, TextPaint paint) {
        canvas.drawText(s, 0, s.length(), x, y, paint);
    }

    private void updateBackground(boolean isUnread) {
        if (isUnread) {
            if (mTabletDevice && mViewMode.getMode() == ViewMode.CONVERSATION_LIST) {
                if (mChecked) {
                    setBackgroundResource(R.drawable.list_conversation_wide_unread_selected_holo);
                } else {
                    setBackgroundResource(R.drawable.conversation_wide_unread_selector);
                }
            } else {
                if (mChecked) {
                    setCheckedActivatedBackground();
                } else {
                    setBackgroundResource(R.drawable.conversation_unread_selector);
                }
            }
        } else {
            if (mTabletDevice && mViewMode.getMode() == ViewMode.CONVERSATION_LIST) {
                if (mChecked) {
                    setBackgroundResource(R.drawable.list_conversation_wide_read_selected_holo);
                } else {
                    setBackgroundResource(R.drawable.conversation_wide_read_selector);
                }
            } else {
                if (mChecked) {
                    setCheckedActivatedBackground();
                } else {
                    setBackgroundResource(R.drawable.conversation_read_selector);
                }
            }
        }
    }

    private void setCheckedActivatedBackground() {
        if (isActivated() && mTabletDevice) {
            setBackgroundResource(R.drawable.list_arrow_selected_holo);
        } else {
            setBackgroundResource(R.drawable.list_selected_holo);
        }
    }

    /**
     * Toggle the check mark on this view and update the conversation
     */
    public void toggleCheckMark() {
        if (mHeader != null && mHeader.conversation != null) {
            mChecked = !mChecked;
            Conversation conv = mHeader.conversation;
            // Set the list position of this item in the conversation
            ListView listView = (ListView)getParent();
            conv.position = mChecked && listView != null ? listView.getPositionForView(this)
                    : Conversation.NO_POSITION;
            if (mSelectedConversationSet != null) {
                mSelectedConversationSet.toggle(this, conv);
            }
            // We update the background after the checked state has changed now that
            // we have a selected background asset. Setting the background usually
            // waits for a layout pass, but we don't need a full layout, just an
            // update to the background.
            requestLayout();
        }
    }

    /**
     * Return if the checkbox for this item is checked.
     */
    public boolean isChecked() {
        return mChecked;
    }

    /**
     * Toggle the star on this view and update the conversation.
     */
    public void toggleStar() {
        mHeader.starred = !mHeader.starred;
        mHeader.starBitmap = mHeader.starred ? STAR_ON : STAR_OFF;
        postInvalidate(mCoordinates.starX, mCoordinates.starY, mCoordinates.starX
                + mHeader.starBitmap.getWidth(),
                mCoordinates.starY + mHeader.starBitmap.getHeight());
        // Generalize this...
        mHeader.conversation.updateBoolean(mContext, ConversationColumns.STARRED, mHeader.starred);
    }

    private boolean isTouchInCheckmark(float x, float y) {
        // Everything before senders and include a touch slop.
        return mHeader.checkboxVisible && x < mCoordinates.sendersX + TOUCH_SLOP;
    }

    private boolean isTouchInStar(float x, float y) {
        // Everything after the star and include a touch slop.
        return x > mCoordinates.starX - TOUCH_SLOP;
    }

    /**
     * Cancel any potential tap handling on this view.
     */
    public void cancelTap() {
        removeCallbacks(mPendingCheckForTap);
        removeCallbacks(mPendingCheckForLongPress);
    }

    /**
     * ConversationItemView is given the first chance to handle touch events.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mLastTouchX = (int) event.getX();
        mLastTouchY = (int) event.getY();
        if (!mSwipeEnabled) {
            return onTouchEventNoSwipe(event);
        }
        boolean handled = true;

        int x = mLastTouchX;
        int y = mLastTouchY;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownEvent = true;
                // In order to allow the down event and subsequent move events
                // to bubble to the swipe handler, we need to return that all
                // down events are handled.
                handled = true;
                // TODO (mindyp) Debounce
                if (mPendingCheckForTap == null) {
                    mPendingCheckForTap = new CheckForTap();
                }
                postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                break;
            case MotionEvent.ACTION_CANCEL:
                mDownEvent = false;
                break;
            case MotionEvent.ACTION_UP:
                cancelTap();
                if (mDownEvent) {
                    // ConversationItemView gets the first chance to handle up
                    // events if there was a down event and there was no move
                    // event in between. In this case, ConversationItemView
                    // received the down event, and then an up event in the
                    // same location (+/- slop). Treat this as a click on the
                    // view or on a specific part of the view.
                    if (isTouchInCheckmark(x, y)) {
                        // Touch on the check mark
                        toggleCheckMark();
                        handled = true;
                    } else if (isTouchInStar(x, y)) {
                        // Touch on the star
                        toggleStar();
                        handled = true;
                    } else {
                        ListView list = (ListView) getParent();
                        if (!isChecked()) {
                            int pos = list.getPositionForView(this);
                            list.performItemClick(this, pos, mHeader.conversation.id);
                        }
                    }
                    handled = true;
                } else {
                    // There was no down event that this view was made aware of,
                    // therefore it cannot handle it.
                    handled = false;
                }
                break;
        }

        if (!handled) {
            // Let View try to handle it as well.
            handled = super.onTouchEvent(event);
        }

        return handled;
    }

    private boolean onTouchEventNoSwipe(MotionEvent event) {
        boolean handled = true;

        int x = (int) event.getX();
        int y = (int) event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownEvent = true;
                // In order to allow the down event and subsequent move events
                // to bubble to the swipe handler, we need to return that all
                // down events are handled.
                handled = isTouchInCheckmark(x, y) || isTouchInStar(x, y);
                break;
            case MotionEvent.ACTION_CANCEL:
                mDownEvent = false;
                break;
            case MotionEvent.ACTION_UP:
                if (mDownEvent) {
                    // ConversationItemView gets the first chance to handle up
                    // events if there was a down event and there was no move
                    // event in between. In this case, ConversationItemView
                    // received the down event, and then an up event in the
                    // same location (+/- slop). Treat this as a click on the
                    // view or on a specific part of the view.
                    if (isTouchInCheckmark(x, y)) {
                        // Touch on the check mark
                        toggleCheckMark();
                    } else if (isTouchInStar(x, y)) {
                        // Touch on the star
                        toggleStar();
                    }
                    handled = true;
                } else {
                    // There was no down event that this view was made aware of,
                    // therefore it cannot handle it.
                    handled = false;
                }
                break;
        }

        if (!handled) {
            // Let View try to handle it as well.
            handled = super.onTouchEvent(event);
        }

        return handled;
    }

    /**
     * Return if this item should respond to long clicks.
     */
    @Override
    public boolean isLongClickable() {
        return true;
    }

    final class CheckForTap implements Runnable {
        public void run() {
            // refreshDrawableState();
            final int longPressTimeout = ViewConfiguration.getLongPressTimeout();
            final boolean longClickable = isLongClickable();

            if (longClickable) {
                if (mPendingCheckForLongPress == null) {
                    mPendingCheckForLongPress = new CheckForLongPress();
                }
                postDelayed(mPendingCheckForLongPress, longPressTimeout);
            }
        }
    }

    private class CheckForLongPress implements Runnable {
        public void run() {
            ConversationItemView.this.toggleSelectionOrBeginDrag();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    /**
     * Grow the height of the item and fade it in when bringing a conversation
     * back from a destructive action.
     *
     * @param listener
     */
    public void startUndoAnimation(ViewMode viewMode, final AnimatorListener listener) {
        int minHeight = ConversationItemViewCoordinates.getMinHeight(mContext, viewMode);
        setMinimumHeight(minHeight);
        final int start = 0 ;
        final int end = minHeight;
        ObjectAnimator undoAnimator = ObjectAnimator.ofInt(this, "animatedHeight", start, end);
        Animator fadeAnimator = ObjectAnimator.ofFloat(this, "itemAlpha", 0, 1.0f);
        mAnimatedHeight = start;
        undoAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        undoAnimator.setDuration(sUndoAnimationDuration);
        AnimatorSet transitionSet = new AnimatorSet();
        transitionSet.playTogether(undoAnimator, fadeAnimator);
        transitionSet.addListener(listener);
        transitionSet.start();
    }

    // Used by animator
    @SuppressWarnings("unused")
    public void setItemAlpha(float alpha) {
        setAlpha(alpha);
        invalidate();
    }

    // Used by animator
    @SuppressWarnings("unused")
    public void setAnimatedHeight(int height) {
        mAnimatedHeight = height;
        requestLayout();
    }

    /**
     * Get the current position of this conversation item in the list.
     */
    public int getPosition() {
        return mHeader != null && mHeader.conversation != null ?
                mHeader.conversation.position : -1;
    }

    /**
     * Select the current conversation.
     */
    private void selectConversation() {
        if (!mSelectedConversationSet.containsKey(mHeader.conversation.id)) {
            toggleCheckMark();
        }
    }

    /**
     * With two pane mode and mailboxes in one pane (tablet), add the conversation to the selected
     * set and start drag mode.
     * In two pane mode when viewing conversations (tablet), toggle selection.
     * In one pane mode (phone, and portrait mode on tablet), toggle selection.
     */
    public void toggleSelectionOrBeginDrag() {
        // If we are in one pane mode, or we are looking at conversations, drag and drop is
        // meaningless.  Toggle checkmark and return early.
        if (!Utils.useTabletUI(mContext) || mViewMode.getMode() != ViewMode.CONVERSATION_LIST) {
            toggleCheckMark();
            return;
        }

        // Begin drag mode. Keep the conversation selected (NOT toggle selection) and start drag.
        selectConversation();
        mDragListener.onStartDragMode();

        // Clip data has form: [conversations_uri, conversationId1,
        // maxMessageId1, label1, conversationId2, maxMessageId2, label2, ...]
        int count = mSelectedConversationSet.size();
        String description = Utils.formatPlural(mContext, R.plurals.move_conversation, count);

        ClipData data = ClipData.newUri(mContext.getContentResolver(), description,
                Conversation.MOVE_CONVERSATIONS_URI);
        for (Conversation conversation : mSelectedConversationSet.values()) {
            data.addItem(new Item(String.valueOf(conversation.position)));
        }

        // Start drag mode
        startDrag(data, new ShadowBuilder(this, count, mLastTouchX, mLastTouchY), null, 0);
    }

    private class ShadowBuilder extends DragShadowBuilder {
        private final Drawable mBackground;

        private final View mView;
        private final String mDragDesc;
        private final int mTouchX;
        private final int mTouchY;
        private int mDragDescX;
        private int mDragDescY;

        public ShadowBuilder(View view, int count, int touchX, int touchY) {
            super(view);
            mView = view;
            mBackground = mView.getResources().getDrawable(R.drawable.list_pressed_holo);
            mDragDesc = Utils.formatPlural(mView.getContext(), R.plurals.move_conversation, count);
            mTouchX = touchX;
            mTouchY = touchY;
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
            int width = mView.getWidth();
            int height = mView.getHeight();
            mDragDescX = mCoordinates.sendersX;
            mDragDescY = getPadding(height, mCoordinates.subjectFontSize)
                    - mCoordinates.subjectAscent;
            shadowSize.set(width, height);
            shadowTouchPoint.set(mTouchX, mTouchY);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            super.onDrawShadow(canvas);

            mBackground.setBounds(0, 0, mView.getWidth(), mView.getHeight());
            mBackground.draw(canvas);
            sPaint.setTextSize(mCoordinates.subjectFontSize);
            canvas.drawText(mDragDesc, mDragDescX, mDragDescY, sPaint);
        }
    }
}