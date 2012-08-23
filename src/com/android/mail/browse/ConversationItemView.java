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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.Context;
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
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.format.DateUtils;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.DecelerateInterpolator;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationItemViewModel.SenderFragment;
import com.android.mail.perf.Timer;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.ui.AnimatedAdapter;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.ConversationSelectionSet;
import com.android.mail.ui.FolderDisplayer;
import com.android.mail.ui.SwipeableItemView;
import com.android.mail.ui.SwipeableListView;
import com.android.mail.ui.ViewMode;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.annotations.VisibleForTesting;

public class ConversationItemView extends View implements SwipeableItemView {
    // Timer.
    private static int sLayoutCount = 0;
    private static Timer sTimer; // Create the sTimer here if you need to do
                                 // perf analysis.
    private static final int PERF_LAYOUT_ITERATIONS = 50;
    private static final String PERF_TAG_LAYOUT = "CCHV.layout";
    private static final String PERF_TAG_CALCULATE_TEXTS_BITMAPS = "CCHV.txtsbmps";
    private static final String PERF_TAG_CALCULATE_SENDER_SUBJECT = "CCHV.sendersubj";
    private static final String PERF_TAG_CALCULATE_FOLDERS = "CCHV.folders";
    private static final String PERF_TAG_CALCULATE_COORDINATES = "CCHV.coordinates";
    private static final String LOG_TAG = LogTag.getLogTag();

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

    private static String sSendersSplitToken;
    private static String sElidedPaddingToken;

    // Static colors.
    private static int sDefaultTextColor;
    private static int sActivatedTextColor;
    private static int sSubjectTextColorRead;
    private static int sSubjectTextColorUnead;
    private static int sSnippetTextColorRead;
    private static int sSnippetTextColorUnread;
    private static int sSendersTextColorRead;
    private static int sSendersTextColorUnread;
    private static int sDateTextColor;
    private static int sDateBackgroundPaddingLeft;
    private static int sTouchSlop;
    private static int sDateBackgroundHeight;
    private static int sStandardScaledDimen;
    private static int sShrinkAnimationDuration;
    private static int sSlideAnimationDuration;
    private static int sAnimatingBackgroundColor;

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
    private boolean mDownEvent;
    private boolean mChecked = false;
    private static int sFadedActivatedColor = -1;
    private ConversationSelectionSet mSelectedConversationSet;
    private Folder mDisplayedFolder;
    private boolean mPriorityMarkersEnabled;
    private boolean mCheckboxesEnabled;
    private boolean mSwipeEnabled;
    private int mLastTouchX;
    private int mLastTouchY;
    private AnimatedAdapter mAdapter;
    private int mAnimatedHeight = -1;
    private String mAccount;
    private ControllableActivity mActivity;
    private CharacterStyle mActivatedTextSpan;
    private int mBackgroundOverride = -1;
    private static int sSendersTextViewTopPadding;
    private static int sSendersTextViewHeight;
    private static ForegroundColorSpan sActivatedTextSpan;
    private static Bitmap sDateBackgroundAttachment;
    private static Bitmap sDateBackgroundNoAttachment;
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
        public void loadConversationFolders(Conversation conv, Folder ignoreFolder) {
            super.loadConversationFolders(conv, ignoreFolder);

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
                // labelValues.folderId ==
                // sGmail.getFolderMap(mAccount).getFolderIdIgnored();

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
        setClickable(true);
        setLongClickable(true);
        mContext = context.getApplicationContext();
        mTabletDevice = Utils.useTabletUI(mContext);
        mAccount = account;
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
            sDefaultTextColor = res.getColor(R.color.default_text_color);
            sActivatedTextColor = res.getColor(android.R.color.white);
            sActivatedTextSpan = new ForegroundColorSpan(sActivatedTextColor);
            sSubjectTextColorRead = res.getColor(R.color.subject_text_color_read);
            sSubjectTextColorUnead = res.getColor(R.color.subject_text_color_unread);
            sSnippetTextColorRead = res.getColor(R.color.snippet_text_color_read);
            sSnippetTextColorUnread = res.getColor(R.color.snippet_text_color_unread);
            sSendersTextColorRead = res.getColor(R.color.senders_text_color_read);
            sSendersTextColorUnread = res.getColor(R.color.senders_text_color_unread);
            sDateTextColor = res.getColor(R.color.date_text_color);
            sDateBackgroundPaddingLeft = res
                    .getDimensionPixelSize(R.dimen.date_background_padding_left);
            sTouchSlop = res.getDimensionPixelSize(R.dimen.touch_slop);
            sDateBackgroundHeight = res.getDimensionPixelSize(R.dimen.date_background_height);
            sStandardScaledDimen = res.getDimensionPixelSize(R.dimen.standard_scaled_dimen);
            sShrinkAnimationDuration = res.getInteger(R.integer.shrink_animation_duration);
            sSlideAnimationDuration = res.getInteger(R.integer.slide_animation_duration);
            // Initialize static color.
            sSendersSplitToken = res.getString(R.string.senders_split_token);
            sElidedPaddingToken = res.getString(R.string.elided_padding_token);
            sAnimatingBackgroundColor = res.getColor(R.color.animating_item_background_color);
            sSendersTextViewTopPadding = res.getDimensionPixelSize
                    (R.dimen.senders_textview_top_padding);
            sSendersTextViewHeight = res.getDimensionPixelSize
                    (R.dimen.senders_textview_height);
        }
    }

    public void bind(Cursor cursor, ControllableActivity activity, ConversationSelectionSet set,
            Folder folder, boolean checkboxesDisabled, boolean swipeEnabled,
            boolean priorityArrowEnabled, AnimatedAdapter adapter) {
        bind(ConversationItemViewModel.forCursor(mAccount, cursor), activity, set, folder,
                checkboxesDisabled, swipeEnabled, priorityArrowEnabled, adapter);
    }

    public void bind(Conversation conversation, ControllableActivity activity,
            ConversationSelectionSet set, Folder folder, boolean checkboxesDisabled,
            boolean swipeEnabled, boolean priorityArrowEnabled, AnimatedAdapter adapter) {
        bind(ConversationItemViewModel.forConversation(mAccount, conversation), activity, set,
                folder, checkboxesDisabled, swipeEnabled, priorityArrowEnabled, adapter);
    }

    private void bind(ConversationItemViewModel header, ControllableActivity activity,
            ConversationSelectionSet set, Folder folder, boolean checkboxesDisabled,
            boolean swipeEnabled, boolean priorityArrowEnabled, AnimatedAdapter adapter) {
        mHeader = header;
        mActivity = activity;
        mSelectedConversationSet = set;
        mDisplayedFolder = folder;
        mCheckboxesEnabled = !checkboxesDisabled;
        mSwipeEnabled = swipeEnabled;
        mPriorityMarkersEnabled = priorityArrowEnabled;
        mAdapter = adapter;
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
                mMode = ConversationItemViewCoordinates.getMode(mContext, mActivity.getViewMode());
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
        int fontColor = getFontColor(sDefaultTextColor);
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
                layoutSubjectSpans(isUnread);
                layoutSubject();
                layoutSenderSpans();
            }
            pauseTimer(PERF_TAG_CALCULATE_TEXTS_BITMAPS);
            return;
        }

        startTimer(PERF_TAG_CALCULATE_FOLDERS);

        // Initialize folder displayer.
        if (mCoordinates.showFolders) {
            mHeader.folderDisplayer = new ConversationItemFolderDisplayer(mContext);
            mHeader.folderDisplayer.loadConversationFolders(mHeader.conversation, mDisplayedFolder);
        }

        pauseTimer(PERF_TAG_CALCULATE_FOLDERS);

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
            final int personalLevel = mHeader.conversation.personalLevel;
            final boolean isImportant =
                    mHeader.conversation.priority == UIProvider.ConversationPriority.IMPORTANT;
            final boolean useImportantMarkers = isImportant && mPriorityMarkersEnabled;

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
        layoutSubjectSpans(isUnread);

        mHeader.sendersDisplayText = new SpannableStringBuilder();
        mHeader.styledSendersString = new SpannableStringBuilder();

        // Parse senders fragments.
        if (mHeader.conversation.conversationInfo != null) {
            Context context = getContext();
            mHeader.messageInfoString = SendersView
                    .createMessageInfo(context, mHeader.conversation);
            int maxChars = ConversationItemViewCoordinates.getSubjectLength(context,
                    ConversationItemViewCoordinates.getMode(context, mActivity.getViewMode()),
                    mHeader.folderDisplayer != null && mHeader.folderDisplayer.mFoldersCount > 0,
                    mHeader.conversation.hasAttachments);
            mHeader.styledSenders = SendersView.format(context,
                    mHeader.conversation.conversationInfo, mHeader.messageInfoString.toString(),
                    maxChars);
        } else {
            SendersView.formatSenders(mHeader, getContext());
        }

        pauseTimer(PERF_TAG_CALCULATE_SENDER_SUBJECT);
        pauseTimer(PERF_TAG_CALCULATE_TEXTS_BITMAPS);
    }

    private void layoutSenderSpans() {
        if (isActivated() && showActivatedText()) {
            if (mActivatedTextSpan == null) {
                mActivatedTextSpan = getActivatedTextSpan();
            }
            mHeader.styledSendersString.setSpan(mActivatedTextSpan, 0,
                    mHeader.styledMessageInfoStringOffset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            mHeader.styledSendersString.removeSpan(mActivatedTextSpan);
        }
        mHeader.sendersTextView = getSendersTextView();
    }

    private TextView getSendersTextView() {
        TextView sendersTextView = new TextView(mContext);
        sendersTextView.setMaxLines(1);
        sendersTextView.setEllipsize(TextUtils.TruncateAt.END);
        sendersTextView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        int length = (int) sPaint.measureText(mHeader.styledSendersString.toString());
        sendersTextView.setText(mHeader.styledSendersString, TextView.BufferType.SPANNABLE);
        sendersTextView.setWidth(length);
        return sendersTextView;
    }

    private CharacterStyle getActivatedTextSpan() {
        return CharacterStyle.wrap(sActivatedTextSpan);
    }

    private void layoutSubjectSpans(boolean isUnread) {
        if (showActivatedText()) {
            mHeader.subjectTextActivated = createSubject(isUnread, true);
        }
        mHeader.subjectText = createSubject(isUnread, false);
    }

    private SpannableStringBuilder createSubject(boolean isUnread, boolean activated) {
        final String subject = filterTag(mHeader.conversation.subject);
        final String snippet = mHeader.conversation.getSnippet();
        int subjectColor = activated ? sActivatedTextColor : isUnread ? sSubjectTextColorUnead
                : sSubjectTextColorRead;
        int snippetColor = activated ? sActivatedTextColor : isUnread ? sSnippetTextColorUnread
                : sSnippetTextColorRead;
        SpannableStringBuilder subjectText = Conversation.getSubjectAndSnippetForDisplay(mContext,
                subject, snippet);
        if (isUnread) {
            subjectText.setSpan(new StyleSpan(Typeface.BOLD), 0, subject.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        subjectText.setSpan(new ForegroundColorSpan(subjectColor), 0, subject.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!TextUtils.isEmpty(snippet)) {
            subjectText.setSpan(new ForegroundColorSpan(snippetColor), subject.length() + 1,
                    subjectText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return subjectText;
    }

    private int getFontColor(int defaultColor) {
        return isActivated() && mTabletDevice ? sActivatedTextColor
                : defaultColor;
    }

    private boolean showActivatedText() {
        return mTabletDevice;
    }

    private void layoutSubject() {
        if (showActivatedText()) {
            mHeader.subjectLayoutActivated =
                    createSubjectLayout(true, mHeader.subjectTextActivated);
        }
        mHeader.subjectLayout = createSubjectLayout(false, mHeader.subjectText);
    }

    private StaticLayout createSubjectLayout(boolean activated,
            SpannableStringBuilder subjectText) {
        sPaint.setTextSize(mCoordinates.subjectFontSize);
        sPaint.setColor(activated ? sActivatedTextColor
                : mHeader.unread ? sSubjectTextColorUnead : sSubjectTextColorRead);
        StaticLayout subjectLayout = new StaticLayout(subjectText, sPaint,
                mCoordinates.subjectWidth, Alignment.ALIGN_NORMAL, 1, 0, true);
        int lineCount = subjectLayout.getLineCount();
        if (mCoordinates.subjectLineCount < lineCount) {
            int end = subjectLayout.getLineEnd(mCoordinates.subjectLineCount - 1);
            subjectLayout = new StaticLayout(subjectText.subSequence(0, end), sPaint,
                    mCoordinates.subjectWidth, Alignment.ALIGN_NORMAL, 1, 0, true);
        }
        return subjectLayout;
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

        // Second pass to layout each fragment.
        int sendersY = mCoordinates.sendersY - mCoordinates.sendersAscent;

        if (mHeader.styledSenders != null) {
            ellipsizeStyledSenders();
            layoutSenderSpans();
        } else {
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

            if (!ConversationItemViewCoordinates.displaySendersInline(mMode)) {
                sendersY += totalWidth <= mSendersWidth ? mCoordinates.sendersLineHeight / 2 : 0;
            }
            totalWidth = ellipsize(fixedWidth, sendersY);
            mHeader.sendersDisplayLayout = new StaticLayout(mHeader.sendersDisplayText, sPaint,
                    mSendersWidth, Alignment.ALIGN_NORMAL, 1, 0, true);
        }

        sPaint.setTextSize(mCoordinates.sendersFontSize);
        sPaint.setTypeface(Typeface.DEFAULT);
        if (mSendersWidth < 0) {
            mSendersWidth = 0;
        }

        pauseTimer(PERF_TAG_CALCULATE_COORDINATES);
    }

    // The rules for displaying ellipsized senders are as follows:
    // 1) If there is message info (either a COUNT or DRAFT info to display), it MUST be shown
    // 2) If senders do not fit, ellipsize the last one that does fit, and stop
    // appending new senders
    private int ellipsizeStyledSenders() {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        float totalWidth = 0;
        boolean ellipsize = false;
        float width;
        SpannableStringBuilder messageInfoString =  mHeader.messageInfoString;
        if (messageInfoString.length() > 0) {
            CharacterStyle[] spans = messageInfoString.getSpans(0, messageInfoString.length(),
                    CharacterStyle.class);
            // There is only 1 character style span; make sure we apply all the
            // styles to the paint object before measuring.
            if (spans.length > 0) {
                spans[0].updateDrawState(sPaint);
            }
            // Paint the message info string to see if we lose space.
            float messageInfoWidth = sPaint.measureText(messageInfoString.toString());
            totalWidth += messageInfoWidth;
        }
       SpannableString prevSender = null;
       SpannableString ellipsizedText;
        for (SpannableString sender : mHeader.styledSenders) {
            // There may be null sender strings if there were dupes we had to remove.
            if (sender == null) {
                continue;
            }
            // No more width available, we'll only show fixed fragments.
            if (ellipsize) {
                break;
            }
            CharacterStyle[] spans = sender.getSpans(0, sender.length(), CharacterStyle.class);
            // There is only 1 character style span.
            if (spans.length > 0) {
                spans[0].updateDrawState(sPaint);
            }
            // If there are already senders present in this string, we need to
            // make sure we prepend the dividing token
            if (SendersView.sElidedString.equals(sender.toString())) {
                prevSender = sender;
                sender = copyStyles(spans, sElidedPaddingToken + sender + sElidedPaddingToken);
            } else if (builder.length() > 0
                    && (prevSender == null || !SendersView.sElidedString.equals(prevSender
                            .toString()))) {
                prevSender = sender;
                sender = copyStyles(spans, sSendersSplitToken + sender);
            } else {
                prevSender = sender;
            }
            if (spans.length > 0) {
                spans[0].updateDrawState(sPaint);
            }
            // Measure the width of the current sender and make sure we have space
            width = (int) sPaint.measureText(sender.toString());
            if (width + totalWidth > mSendersWidth) {
                // The text is too long, new line won't help. We have to
                // ellipsize text.
                ellipsize = true;
                width = mSendersWidth - totalWidth; // ellipsis width?
                ellipsizedText = copyStyles(spans,
                        TextUtils.ellipsize(sender, sPaint, width, TruncateAt.END));
                width = (int) sPaint.measureText(ellipsizedText.toString());
            } else {
                ellipsizedText = null;
            }
            totalWidth += width;

            final CharSequence fragmentDisplayText;
            if (ellipsizedText != null) {
                fragmentDisplayText = ellipsizedText;
            } else {
                fragmentDisplayText = sender;
            }
            builder.append(fragmentDisplayText);
        }
        mHeader.styledMessageInfoStringOffset = builder.length();
        if (messageInfoString != null) {
            builder.append(messageInfoString);
        }
        mHeader.styledSendersString = builder;
        return (int)totalWidth;
    }

    private SpannableString copyStyles(CharacterStyle[] spans, CharSequence newText) {
        SpannableString s = new SpannableString(newText);
        if (spans != null && spans.length > 0) {
            s.setSpan(spans[0], 0, s.length(), 0);
        }
        return s;
    }

    private int ellipsize(int fixedWidth, int sendersY) {
        int totalWidth = 0;
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
            senderFragment.shouldDisplay = true;
            totalWidth += width;

            final CharSequence fragmentDisplayText;
            if (senderFragment.ellipsizedText != null) {
                fragmentDisplayText = senderFragment.ellipsizedText;
            } else {
                fragmentDisplayText = mHeader.sendersText.substring(start, end);
            }
            final int spanStart = mHeader.sendersDisplayText.length();
            mHeader.sendersDisplayText.append(fragmentDisplayText);
            mHeader.sendersDisplayText.setSpan(senderFragment.style, spanStart,
                    mHeader.sendersDisplayText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return totalWidth;
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
        // Old style senders; apply text colors/ sizes/ styling.
        canvas.save();
        if (mHeader.sendersDisplayLayout != null) {
            sPaint.setTextSize(mCoordinates.sendersFontSize);
            sPaint.setTypeface(SendersView.getTypeface(isUnread));
            sPaint.setColor(getFontColor(isUnread ? sSendersTextColorUnread
                    : sSendersTextColorRead));
            canvas.translate(mCoordinates.sendersX,
                    mCoordinates.sendersY + mHeader.sendersDisplayLayout.getTopPadding());
            mHeader.sendersDisplayLayout.draw(canvas);
        } else {
            canvas.translate(mCoordinates.sendersX,
                    mCoordinates.sendersY + sSendersTextViewTopPadding);
            mHeader.sendersTextView.layout(0, 0, mSendersWidth, sSendersTextViewHeight);
            mHeader.sendersTextView.draw(canvas);
        }
        canvas.restore();


        // Subject.
        sPaint.setTextSize(mCoordinates.subjectFontSize);
        sPaint.setTypeface(Typeface.DEFAULT);
        canvas.save();
        if (isActivated() && showActivatedText()) {
            if (mHeader.subjectLayoutActivated != null) {
                canvas.translate(mCoordinates.subjectX, mCoordinates.subjectY
                        + mHeader.subjectLayoutActivated.getTopPadding());
                mHeader.subjectLayoutActivated.draw(canvas);
            }
        } else if (mHeader.subjectLayout != null) {
            canvas.translate(mCoordinates.subjectX,
                    mCoordinates.subjectY + mHeader.subjectLayout.getTopPadding());
            mHeader.subjectLayout.draw(canvas);
        }
        canvas.restore();

        // Folders.
        if (mCoordinates.showFolders) {
            mHeader.folderDisplayer.drawFolders(canvas, mCoordinates, mFoldersXEnd, mMode);
        }

        // If this folder has a color (combined view/Email), show it here
        if (mHeader.conversation.color != 0) {
            sFoldersPaint.setColor(mHeader.conversation.color);
            sFoldersPaint.setStyle(Paint.Style.FILL);
            int width = ConversationItemViewCoordinates.getColorBlockWidth(mContext);
            int height = ConversationItemViewCoordinates.getColorBlockHeight(mContext);
            canvas.drawRect(mCoordinates.dateXEnd - width, 0, mCoordinates.dateXEnd,
                    height, sFoldersPaint);
        }

        // Date background: shown when there is an attachment or a visible
        // folder.
        if (!isActivated()
                && (mHeader.conversation.hasAttachments ||
                        (mHeader.folderDisplayer != null
                            && mHeader.folderDisplayer.hasVisibleFolders()))
                && ConversationItemViewCoordinates.showAttachmentBackground(mMode)) {
            int leftOffset = (mHeader.conversation.hasAttachments ? mPaperclipX : mDateX)
                    - sDateBackgroundPaddingLeft;
            int top = mCoordinates.showFolders ? mCoordinates.foldersY : mCoordinates.dateY;
            mHeader.dateBackground = getDateBackground(mHeader.conversation.hasAttachments);
            canvas.drawBitmap(mHeader.dateBackground, leftOffset, top, sPaint);
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
        sPaint.setColor(sDateTextColor);
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
        canvas.drawBitmap(getStarBitmap(), mCoordinates.starX, mCoordinates.starY, sPaint);
    }

    private Bitmap getStarBitmap() {
        return mHeader.conversation.starred ? STAR_ON : STAR_OFF;
    }

    private Bitmap getDateBackground(boolean hasAttachments) {
        int leftOffset = (hasAttachments ? mPaperclipX : mDateX) - sDateBackgroundPaddingLeft;
        if (hasAttachments) {
            if (sDateBackgroundAttachment == null) {
                sDateBackgroundAttachment = Bitmap.createScaledBitmap(DATE_BACKGROUND, mViewWidth
                        - leftOffset, sDateBackgroundHeight, false);
            }
            return sDateBackgroundAttachment;
        } else {
            if (sDateBackgroundNoAttachment == null) {
                sDateBackgroundNoAttachment = Bitmap.createScaledBitmap(DATE_BACKGROUND, mViewWidth
                        - leftOffset, sDateBackgroundHeight, false);
            }
            return sDateBackgroundNoAttachment;
        }
    }

    private void drawText(Canvas canvas, CharSequence s, int x, int y, TextPaint paint) {
        canvas.drawText(s, 0, s.length(), x, y, paint);
    }

    private void updateBackground(boolean isUnread) {
        if (mBackgroundOverride != -1) {
            // If the item is animating, we use a color to avoid shrinking a 9-patch
            // and getting weird artifacts from the overlap.
            setBackgroundColor(mBackgroundOverride);
            return;
        }
        final boolean isListOnTablet = mTabletDevice && mActivity.getViewMode().isListMode();
        if (isUnread) {
            if (isListOnTablet) {
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
            if (isListOnTablet) {
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
        ViewMode mode = mActivity.getViewMode();
        if (!mTabletDevice || !mode.isListMode()) {
            if (mHeader != null && mHeader.conversation != null) {
                mChecked = !mChecked;
                Conversation conv = mHeader.conversation;
                // Set the list position of this item in the conversation
                ListView listView = getListView();
                conv.position = mChecked && listView != null ? listView.getPositionForView(this)
                        : Conversation.NO_POSITION;
                if (mSelectedConversationSet != null) {
                    mSelectedConversationSet.toggle(this, conv);
                }
                // We update the background after the checked state has changed
                // now that we have a selected background asset. Setting the background
                // usually waits for a layout pass, but we don't need a full layout,
                // just an update to the background.
                requestLayout();
            }
        } else {
            beginDragMode();
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
        mHeader.conversation.starred = !mHeader.conversation.starred;
        Bitmap starBitmap = getStarBitmap();
        postInvalidate(mCoordinates.starX, mCoordinates.starY, mCoordinates.starX
                + starBitmap.getWidth(),
                mCoordinates.starY + starBitmap.getHeight());
        ConversationCursor cursor = (ConversationCursor)mAdapter.getCursor();
        cursor.updateBoolean(mContext, mHeader.conversation, ConversationColumns.STARRED,
                mHeader.conversation.starred);
    }

    private boolean isTouchInCheckmark(float x, float y) {
        // Everything before senders and include a touch slop.
        return mHeader.checkboxVisible && x < mCoordinates.sendersX + sTouchSlop;
    }

    private boolean isTouchInStar(float x, float y) {
        // Everything after the star and include a touch slop.
        return x > mCoordinates.starX - sTouchSlop;
    }

    @Override
    public boolean canChildBeDismissed() {
        return true;
    }

    @Override
    public void dismiss() {
        SwipeableListView listView = getListView();
        if (listView != null) {
            getListView().dismissChild(this);
        }
    }

    /**
     * ConversationItemView is given the first chance to handle touch events.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        mLastTouchX = x;
        mLastTouchY = y;
        if (!mSwipeEnabled) {
            return onTouchEventNoSwipe(event);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isTouchInCheckmark(x, y) || isTouchInStar(x, y)) {
                    mDownEvent = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mDownEvent) {
                    if (isTouchInCheckmark(x, y)) {
                        // Touch on the check mark
                        mDownEvent = false;
                        toggleCheckMark();
                        return true;
                    } else if (isTouchInStar(x, y)) {
                        // Touch on the star
                        mDownEvent = false;
                        toggleStar();
                        return true;
                    }
                }
                break;
        }
        // Let View try to handle it as well.
        boolean handled = super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        return handled;
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        ListView list = getListView();
        if (list != null) {
            int pos = list.getPositionForView(this);
            list.performItemClick(this, pos, mHeader.conversation.id);
        }
        return handled;
    }

    private SwipeableListView getListView() {
        SwipeableListView v = (SwipeableListView) ((SwipeableConversationItemView) getParent())
                .getListView();
        if (v == null) {
            v = mAdapter.getListView();
        }
        return v;
    }

    private boolean onTouchEventNoSwipe(MotionEvent event) {
        boolean handled = false;

        int x = (int) event.getX();
        int y = (int) event.getY();
        mLastTouchX = x;
        mLastTouchY = y;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownEvent = true;
                if (isTouchInCheckmark(x, y) || isTouchInStar(x, y)) {
                    handled = true;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                mDownEvent = false;
                break;

            case MotionEvent.ACTION_UP:
                if (mDownEvent) {
                    if (isTouchInCheckmark(x, y)) {
                        // Touch on the check mark
                        toggleCheckMark();
                    } else if (isTouchInStar(x, y)) {
                        // Touch on the star
                        toggleStar();
                    }
                    handled = true;
                }
                break;
        }

        if (!handled) {
            handled = super.onTouchEvent(event);
        }

        return handled;
    }

    /**
     * Reset any state associated with this conversation item view so that it
     * can be reused.
     */
    public void reset() {
        mBackgroundOverride = -1;
        setAlpha(1);
        setTranslationX(0);
        setAnimatedHeight(-1);
    }

    /**
     * Grow the height of the item and fade it in when bringing a conversation
     * back from a destructive action.
     * @param listener
     */
    public void startSwipeUndoAnimation(ViewMode viewMode, final AnimatorListener listener) {
        ObjectAnimator undoAnimator = createTranslateXAnimation(true);
        undoAnimator.addListener(listener);
        undoAnimator.start();
    }

    /**
     * Grow the height of the item and fade it in when bringing a conversation
     * back from a destructive action.
     * @param listener
     */
    public void startUndoAnimation(ViewMode viewMode, final AnimatorListener listener) {
        int minHeight = ConversationItemViewCoordinates.getMinHeight(mContext, viewMode);
        setMinimumHeight(minHeight);
        mAnimatedHeight = 0;
        ObjectAnimator height = createHeightAnimation(true);
        Animator fade = ObjectAnimator.ofFloat(this, "itemAlpha", 0, 1.0f);
        fade.setDuration(sShrinkAnimationDuration);
        fade.setInterpolator(new DecelerateInterpolator(2.0f));
        AnimatorSet transitionSet = new AnimatorSet();
        transitionSet.playTogether(height, fade);
        transitionSet.addListener(listener);
        transitionSet.start();
    }

    /**
     * Grow the height of the item and fade it in when bringing a conversation
     * back from a destructive action.
     * @param listener
     */
    public void startDestroyWithSwipeAnimation(final AnimatorListener listener) {
        ObjectAnimator slide = createTranslateXAnimation(false);
        ObjectAnimator height = createHeightAnimation(false);
        AnimatorSet transitionSet = new AnimatorSet();
        transitionSet.playSequentially(slide, height);
        transitionSet.addListener(listener);
        transitionSet.start();
    }

    private ObjectAnimator createTranslateXAnimation(boolean show) {
        SwipeableListView parent = getListView();
        // If we can't get the parent...we have bigger problems.
        int width = parent != null ? parent.getMeasuredWidth() : 0;
        final float start = show ? width : 0f;
        final float end = show ? 0f : width;
        ObjectAnimator slide = ObjectAnimator.ofFloat(this, "translationX", start, end);
        slide.setInterpolator(new DecelerateInterpolator(2.0f));
        slide.setDuration(sSlideAnimationDuration);
        return slide;
    }

    public void startDestroyAnimation(final AnimatorListener listener) {
        ObjectAnimator height = createHeightAnimation(false);
        int minHeight = ConversationItemViewCoordinates.getMinHeight(mContext,
                mActivity.getViewMode());
        setMinimumHeight(0);
        mBackgroundOverride = sAnimatingBackgroundColor;
        setBackgroundColor(mBackgroundOverride);
        mAnimatedHeight = minHeight;
        height.addListener(listener);
        height.start();
    }

    private ObjectAnimator createHeightAnimation(boolean show) {
        int minHeight = ConversationItemViewCoordinates.getMinHeight(getContext(),
                mActivity.getViewMode());
        final int start = show ? 0 : minHeight;
        final int end = show ? minHeight : 0;
        ObjectAnimator height = ObjectAnimator.ofInt(this, "animatedHeight", start, end);
        height.setInterpolator(new DecelerateInterpolator(2.0f));
        height.setDuration(sShrinkAnimationDuration);
        return height;
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mAnimatedHeight == -1) {
            int height = measureHeight(heightMeasureSpec,
                    ConversationItemViewCoordinates.getMode(mContext, mActivity.getViewMode()));
            setMeasuredDimension(widthMeasureSpec, height);
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mAnimatedHeight);
        }
    }

    /**
     * Determine the height of this view.
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

    /**
     * Get the current position of this conversation item in the list.
     */
    public int getPosition() {
        return mHeader != null && mHeader.conversation != null ?
                mHeader.conversation.position : -1;
    }

    @Override
    public View getSwipeableView() {
        return this;
    }

    /**
     * Select the current conversation.
     */
    private void selectConversation() {
        if (!mSelectedConversationSet.containsKey(mHeader.conversation.id)) {
            mChecked = !mChecked;
            Conversation conv = mHeader.conversation;
            // Set the list position of this item in the conversation
            ListView listView = getListView();
            conv.position = mChecked && listView != null ? listView.getPositionForView(this)
                    : Conversation.NO_POSITION;
            if (mSelectedConversationSet != null) {
                mSelectedConversationSet.toggle(this, conv);
            }
        }
    }

    /**
     * Begin drag mode. Keep the conversation selected (NOT toggle selection) and start drag.
     */
    private void beginDragMode() {
        if (mLastTouchX < 0 || mLastTouchY < 0) {
            return;
        }
        selectConversation();

        // Clip data has form: [conversations_uri, conversationId1,
        // maxMessageId1, label1, conversationId2, maxMessageId2, label2, ...]
        final int count = mSelectedConversationSet.size();
        String description = Utils.formatPlural(mContext, R.plurals.move_conversation, count);

        final ClipData data = ClipData.newUri(mContext.getContentResolver(), description,
                Conversation.MOVE_CONVERSATIONS_URI);
        for (Conversation conversation : mSelectedConversationSet.values()) {
            data.addItem(new Item(String.valueOf(conversation.position)));
        }
        // Protect against non-existent views: only happens for monkeys
        final int width = this.getWidth();
        final int height = this.getHeight();
        final boolean isDimensionNegative = (width < 0) || (height < 0);
        if (isDimensionNegative) {
            LogUtils.e(LOG_TAG, "ConversationItemView: dimension is negative: "
                        + "width=%d, height=%d", width, height);
            return;
        }
        mActivity.startDragMode();
        // Start drag mode
        startDrag(data, new ShadowBuilder(this, count, mLastTouchX, mLastTouchY), null, 0);
    }

    /**
     * Handles the drag event.
     *
     * @param event the drag event to be handled
     */
    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENDED:
                mActivity.stopDragMode();
                return true;
        }
        return false;
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
            mBackground.setBounds(0, 0, mView.getWidth(), mView.getHeight());
            mBackground.draw(canvas);
            sPaint.setTextSize(mCoordinates.subjectFontSize);
            canvas.drawText(mDragDesc, mDragDescX, mDragDescY, sPaint);
        }
    }
}
