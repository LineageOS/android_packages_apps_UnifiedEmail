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
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import android.text.style.TextAppearanceSpan;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationItemViewModel.SenderFragment;
import com.android.mail.perf.Timer;
import com.android.mail.photomanager.ContactPhotoManager;
import com.android.mail.photomanager.ContactPhotoManager.ContactIdentifier;
import com.android.mail.photomanager.PhotoManager.PhotoIdentifier;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.providers.UIProvider.ConversationListIcon;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.ui.AnimatedAdapter;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.ConversationSelectionSet;
import com.android.mail.ui.DividedImageCanvas;
import com.android.mail.ui.DividedImageCanvas.InvalidateCallback;
import com.android.mail.ui.FolderDisplayer;
import com.android.mail.ui.SwipeableItemView;
import com.android.mail.ui.SwipeableListView;
import com.android.mail.ui.ViewMode;
import com.android.mail.utils.HardwareLayerEnabler;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;

public class ConversationItemView extends View implements SwipeableItemView, ToggleableItem,
        InvalidateCallback {
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
    private static Bitmap STATE_REPLIED;
    private static Bitmap STATE_FORWARDED;
    private static Bitmap STATE_REPLIED_AND_FORWARDED;
    private static Bitmap STATE_CALENDAR_INVITE;

    private static String sSendersSplitToken;
    private static String sElidedPaddingToken;

    // Static colors.
    private static int sActivatedTextColor;
    private static int sSendersTextColorRead;
    private static int sSendersTextColorUnread;
    private static int sDateTextColor;
    private static int sTouchSlop;
    @Deprecated
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
    /** The view mode at which we calculated mViewWidth previously. */
    private int mPreviousMode;
    private int mMode = -1;
    private int mDateX;
    private int mPaperclipX;
    private int mSendersWidth;

    /** Whether we're running under test mode. */
    private boolean mTesting = false;
    /** Whether we are on a tablet device or not */
    private final boolean mTabletDevice;

    @VisibleForTesting
    ConversationItemViewCoordinates mCoordinates;

    private ConversationItemViewCoordinates.Config mConfig;

    private final Context mContext;

    public ConversationItemViewModel mHeader;
    private boolean mDownEvent;
    private boolean mChecked = false;
    private ConversationSelectionSet mSelectedConversationSet;
    private Folder mDisplayedFolder;
    private boolean mCheckboxesEnabled;
    private boolean mStarEnabled;
    private boolean mSwipeEnabled;
    private int mLastTouchX;
    private int mLastTouchY;
    private AnimatedAdapter mAdapter;
    private float mAnimatedHeightFraction = 1.0f;
    private final String mAccount;
    private ControllableActivity mActivity;
    private final TextView mSubjectTextView;
    private final TextView mSendersTextView;
    private boolean mConvListPhotosEnabled;
    private final DividedImageCanvas mContactImagesHolder;
    private int mAttachmentPreviewMode;
    private final DividedImageCanvas mAttachmentPreviewsCanvas;

    private static int sFoldersLeftPadding;
    private static TextAppearanceSpan sSubjectTextUnreadSpan;
    private static TextAppearanceSpan sSubjectTextReadSpan;
    private static ForegroundColorSpan sSnippetTextUnreadSpan;
    private static ForegroundColorSpan sSnippetTextReadSpan;
    private static int sScrollSlop;
    private static CharacterStyle sActivatedTextSpan;
    private static ContactPhotoManager sContactPhotoManager;
    private static ContactPhotoManager sAttachmentPreviewsManager;
    private static final String EMPTY_SNIPPET = "";

    static {
        sPaint.setAntiAlias(true);
        sFoldersPaint.setAntiAlias(true);
    }

    /**
     * Handles displaying folders in a conversation header view.
     */
    static class ConversationItemFolderDisplayer extends FolderDisplayer {

        private int mFoldersCount;

        public ConversationItemFolderDisplayer(Context context) {
            super(context);
        }

        @Override
        public void loadConversationFolders(Conversation conv, final Uri ignoreFolderUri,
                final int ignoreFolderType) {
            super.loadConversationFolders(conv, ignoreFolderUri, ignoreFolderType);
            mFoldersCount = mFoldersSortedSet.size();
        }

        @Override
        public void reset() {
            super.reset();
            mFoldersCount = 0;
        }

        public boolean hasVisibleFolders() {
            return mFoldersCount > 0;
        }

        private int measureFolders(int mode, int availableSpace, int cellSize) {
            int totalWidth = 0;
            boolean firstTime = true;
            for (Folder f : mFoldersSortedSet) {
                final String folderString = f.name;
                int width = (int) sFoldersPaint.measureText(folderString) + cellSize;
                if (firstTime) {
                    firstTime = false;
                } else {
                    width += sFoldersLeftPadding;
                }
                totalWidth += width;
                if (totalWidth > availableSpace) {
                    break;
                }
            }

            return totalWidth;
        }

        public void drawFolders(Canvas canvas, ConversationItemViewCoordinates coordinates,
                int mode) {
            if (mFoldersCount == 0) {
                return;
            }
            final int xMinStart = coordinates.foldersX;
            final int xEnd = coordinates.foldersXEnd;
            final int y = coordinates.foldersY;
            final int height = coordinates.foldersHeight;
            final int ascent = coordinates.foldersAscent;
            int textBottomPadding = coordinates.foldersTextBottomPadding;

            sFoldersPaint.setTextSize(coordinates.foldersFontSize);
            sFoldersPaint.setTypeface(coordinates.foldersTypeface);

            // Initialize space and cell size based on the current mode.
            int availableSpace = xEnd - xMinStart;
            int averageWidth = availableSpace / mFoldersCount;
            int cellSize = ConversationItemViewCoordinates.getFolderCellWidth(mContext);

            // TODO(ath): sFoldersPaint.measureText() is done 3x in this method. stop that.
            // Extra credit: maybe cache results across items as long as font size doesn't change.

            final int totalWidth = measureFolders(mode, availableSpace, cellSize);
            int xStart = xEnd - Math.min(availableSpace, totalWidth);
            final boolean overflow = totalWidth > availableSpace;

            // Second pass to draw folders.
            int i = 0;
            for (Folder f : mFoldersSortedSet) {
                if (availableSpace <= 0) {
                    break;
                }
                final String folderString = f.name;
                final int fgColor = f.getForegroundColor(mDefaultFgColor);
                final int bgColor = f.getBackgroundColor(mDefaultBgColor);
                boolean labelTooLong = false;
                final int textW = (int) sFoldersPaint.measureText(folderString);
                int width = textW + cellSize + sFoldersLeftPadding;

                if (overflow && width > averageWidth) {
                    if (i < mFoldersCount - 1) {
                        width = averageWidth;
                    } else {
                        // allow the last label to take all remaining space
                        // (and don't let it make room for padding)
                        width = availableSpace + sFoldersLeftPadding;
                    }
                    labelTooLong = true;
                }

                // TODO (mindyp): how to we get this?
                final boolean isMuted = false;
                // labelValues.folderId ==
                // sGmail.getFolderMap(mAccount).getFolderIdIgnored();

                // Draw the box.
                sFoldersPaint.setColor(bgColor);
                sFoldersPaint.setStyle(Paint.Style.FILL);
                canvas.drawRect(xStart, y, xStart + width - sFoldersLeftPadding,
                        y + height, sFoldersPaint);

                // Draw the text.
                final int padding = cellSize / 2;
                sFoldersPaint.setColor(fgColor);
                sFoldersPaint.setStyle(Paint.Style.FILL);
                if (labelTooLong) {
                    final int rightBorder = xStart + width - sFoldersLeftPadding - padding;
                    final Shader shader = new LinearGradient(rightBorder - padding, y, rightBorder,
                            y, fgColor, Utils.getTransparentColor(fgColor), Shader.TileMode.CLAMP);
                    sFoldersPaint.setShader(shader);
                }
                canvas.drawText(folderString, xStart + padding, y + height - textBottomPadding,
                        sFoldersPaint);
                if (labelTooLong) {
                    sFoldersPaint.setShader(null);
                }

                availableSpace -= width;
                xStart += width;
                i++;
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
        final Resources res = mContext.getResources();
        mTabletDevice = Utils.useTabletUI(res);
        mAccount = account;

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
            ATTACHMENT = BitmapFactory.decodeResource(res, R.drawable.ic_attachment_holo_light);
            ONLY_TO_ME = BitmapFactory.decodeResource(res, R.drawable.ic_email_caret_double);
            TO_ME_AND_OTHERS = BitmapFactory.decodeResource(res, R.drawable.ic_email_caret_single);
            IMPORTANT_ONLY_TO_ME = BitmapFactory.decodeResource(res,
                    R.drawable.ic_email_caret_double_important_unread);
            IMPORTANT_TO_ME_AND_OTHERS = BitmapFactory.decodeResource(res,
                    R.drawable.ic_email_caret_single_important_unread);
            IMPORTANT_TO_OTHERS = BitmapFactory.decodeResource(res,
                    R.drawable.ic_email_caret_none_important_unread);
            STATE_REPLIED =
                    BitmapFactory.decodeResource(res, R.drawable.ic_badge_reply_holo_light);
            STATE_FORWARDED =
                    BitmapFactory.decodeResource(res, R.drawable.ic_badge_forward_holo_light);
            STATE_REPLIED_AND_FORWARDED =
                    BitmapFactory.decodeResource(res, R.drawable.ic_badge_reply_forward_holo_light);
            STATE_CALENDAR_INVITE =
                    BitmapFactory.decodeResource(res, R.drawable.ic_badge_invite_holo_light);

            // Initialize colors.
            sActivatedTextColor = res.getColor(android.R.color.white);
            sActivatedTextSpan = CharacterStyle.wrap(new ForegroundColorSpan(sActivatedTextColor));
            sSendersTextColorRead = res.getColor(R.color.senders_text_color_read);
            sSendersTextColorUnread = res.getColor(R.color.senders_text_color_unread);
            sSubjectTextUnreadSpan = new TextAppearanceSpan(mContext,
                    R.style.SubjectAppearanceUnreadStyle);
            sSubjectTextReadSpan = new TextAppearanceSpan(mContext,
                    R.style.SubjectAppearanceReadStyle);
            sSnippetTextUnreadSpan =
                    new ForegroundColorSpan(res.getColor(R.color.snippet_text_color_unread));
            sSnippetTextReadSpan =
                    new ForegroundColorSpan(res.getColor(R.color.snippet_text_color_read));
            sDateTextColor = res.getColor(R.color.date_text_color);
            sTouchSlop = res.getDimensionPixelSize(R.dimen.touch_slop);
            sStandardScaledDimen = res.getDimensionPixelSize(R.dimen.standard_scaled_dimen);
            sShrinkAnimationDuration = res.getInteger(R.integer.shrink_animation_duration);
            sSlideAnimationDuration = res.getInteger(R.integer.slide_animation_duration);
            // Initialize static color.
            sSendersSplitToken = res.getString(R.string.senders_split_token);
            sElidedPaddingToken = res.getString(R.string.elided_padding_token);
            sAnimatingBackgroundColor = res.getColor(R.color.animating_item_background_color);
            sScrollSlop = res.getInteger(R.integer.swipeScrollSlop);
            sFoldersLeftPadding = res.getDimensionPixelOffset(R.dimen.folders_left_padding);
            sContactPhotoManager = ContactPhotoManager.createContactPhotoManager(context);
            sAttachmentPreviewsManager = ContactPhotoManager.createContactPhotoManager(context);
        }

        mSendersTextView = new TextView(mContext);
        mSendersTextView.setEllipsize(TextUtils.TruncateAt.END);
        mSendersTextView.setIncludeFontPadding(false);

        mSubjectTextView = new TextView(mContext);
        mSubjectTextView.setEllipsize(TextUtils.TruncateAt.END);
        mSubjectTextView.setIncludeFontPadding(false);

        mContactImagesHolder = new DividedImageCanvas(context, new InvalidateCallback() {
            @Override
            public void invalidate() {
                if (mCoordinates == null) {
                    return;
                }
                ConversationItemView.this.invalidate(mCoordinates.contactImagesX,
                        mCoordinates.contactImagesY,
                        mCoordinates.contactImagesX + mCoordinates.contactImagesWidth,
                        mCoordinates.contactImagesY + mCoordinates.contactImagesHeight);
            }
        });
        mAttachmentPreviewsCanvas = new DividedImageCanvas(context, this);
    }

    public void bind(Cursor cursor, ControllableActivity activity, ConversationSelectionSet set,
            Folder folder, int checkboxOrSenderImage, boolean swipeEnabled,
            boolean priorityArrowEnabled, AnimatedAdapter adapter) {
        bind(ConversationItemViewModel.forCursor(mAccount, cursor), activity, set, folder,
                checkboxOrSenderImage, swipeEnabled, priorityArrowEnabled, adapter);
    }

    public void bind(Conversation conversation, ControllableActivity activity,
            ConversationSelectionSet set, Folder folder, int checkboxOrSenderImage,
            boolean swipeEnabled, boolean priorityArrowEnabled, AnimatedAdapter adapter) {
        bind(ConversationItemViewModel.forConversation(mAccount, conversation), activity, set,
                folder, checkboxOrSenderImage, swipeEnabled, priorityArrowEnabled, adapter);
    }

    private void bind(ConversationItemViewModel header, ControllableActivity activity,
            ConversationSelectionSet set, Folder folder, int checkboxOrSenderImage,
            boolean swipeEnabled, boolean priorityArrowEnabled, AnimatedAdapter adapter) {
        // If this was previously bound to a conversation, remove any contact
        // photo manager requests.
        // TODO:MARKWEI attachment previews
        if (mHeader != null) {
            final ArrayList<String> divisionIds = mContactImagesHolder.getDivisionIds();
            if (divisionIds != null) {
                mContactImagesHolder.reset();
                for (int pos = 0; pos < divisionIds.size(); pos++) {
                    sContactPhotoManager.removePhoto(DividedImageCanvas.generateHash(
                            mContactImagesHolder, pos, divisionIds.get(pos)));
                }
            }
        }
        mCoordinates = null;
        mHeader = header;
        mActivity = activity;
        mSelectedConversationSet = set;
        mDisplayedFolder = folder;
        mCheckboxesEnabled = (checkboxOrSenderImage == ConversationListIcon.CHECKBOX);
        mConvListPhotosEnabled = (checkboxOrSenderImage == ConversationListIcon.SENDER_IMAGE);
        mStarEnabled = folder != null && !folder.isTrash();
        mSwipeEnabled = swipeEnabled;
        mAdapter = adapter;
        if (mHeader.conversation.getAttachmentsCount() == 0) {
            mAttachmentPreviewMode = ConversationItemViewCoordinates.ATTACHMENT_PREVIEW_NONE;
        } else {
            mAttachmentPreviewMode = mHeader.conversation.read ?
                    ConversationItemViewCoordinates.ATTACHMENT_PREVIEW_SHORT
                    : ConversationItemViewCoordinates.ATTACHMENT_PREVIEW_TALL;
        }

        final int gadgetMode;
        if (mConvListPhotosEnabled) {
            gadgetMode = ConversationItemViewCoordinates.GADGET_CONTACT_PHOTO;
        } else if (mCheckboxesEnabled) {
            gadgetMode = ConversationItemViewCoordinates.GADGET_CHECKBOX;
        } else {
            gadgetMode = ConversationItemViewCoordinates.GADGET_NONE;
        }

        // Initialize folder displayer.
        if (mHeader.folderDisplayer == null) {
            mHeader.folderDisplayer = new ConversationItemFolderDisplayer(mContext);
        } else {
            mHeader.folderDisplayer.reset();
        }

        final int ignoreFolderType;
        if (mDisplayedFolder.isInbox()) {
            ignoreFolderType = FolderType.INBOX;
        } else {
            ignoreFolderType = -1;
        }

        mHeader.folderDisplayer.loadConversationFolders(mHeader.conversation, mDisplayedFolder.uri,
                ignoreFolderType);

        mHeader.dateText = DateUtils.getRelativeTimeSpanString(mContext,
                mHeader.conversation.dateMs);

        mConfig = new ConversationItemViewCoordinates.Config()
            .withGadget(gadgetMode)
            .withAttachmentPreviews(mAttachmentPreviewMode);
        if (header.folderDisplayer.hasVisibleFolders()) {
            mConfig.showFolders();
        }
        if (header.hasBeenForwarded || header.hasBeenRepliedTo || header.isInvite) {
            mConfig.showReplyState();
        }
        if (mHeader.conversation.color != 0) {
            mConfig.showColorBlock();
        }
        // Personal level.
        mHeader.personalLevelBitmap = null;
        if (true) { // TODO: hook this up to a setting
            final int personalLevel = mHeader.conversation.personalLevel;
            final boolean isImportant =
                    mHeader.conversation.priority == UIProvider.ConversationPriority.IMPORTANT;
            final boolean useImportantMarkers = isImportant && priorityArrowEnabled;

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
        if (mHeader.personalLevelBitmap != null) {
            mConfig.showPersonalIndicator();
        }

        setContentDescription();
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
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int wSize = MeasureSpec.getSize(widthMeasureSpec);

        final int currentMode = mActivity.getViewMode().getMode();
        if (wSize != mViewWidth || mPreviousMode != currentMode) {
            mViewWidth = wSize;
            mPreviousMode = currentMode;
            if (!mTesting) {
                mMode = ConversationItemViewCoordinates.getMode(mContext, mPreviousMode);
            }
        }
        mHeader.viewWidth = mViewWidth;

        mConfig.updateWidth(wSize).setMode(mMode);

        Resources res = getResources();
        mHeader.standardScaledDimen = res.getDimensionPixelOffset(R.dimen.standard_scaled_dimen);
        if (mHeader.standardScaledDimen != sStandardScaledDimen) {
            // Large Text has been toggle on/off. Update the static dimens.
            sStandardScaledDimen = mHeader.standardScaledDimen;
            ConversationItemViewCoordinates.refreshConversationDimens(mContext);
        }

        mCoordinates = ConversationItemViewCoordinates.forConfig(mContext, mConfig,
                mAdapter.getCoordinatesCache());

        final int h = (mAnimatedHeightFraction != 1.0f) ?
                Math.round(mAnimatedHeightFraction * mCoordinates.height) : mCoordinates.height;
        setMeasuredDimension(mConfig.getWidth(), h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        startTimer(PERF_TAG_LAYOUT);

        super.onLayout(changed, left, top, right, bottom);

        calculateTextsAndBitmaps();
        calculateCoordinates();

        // Subject.
        createSubject(mHeader.unread);

        if (!mHeader.isLayoutValid(mContext)) {
            setContentDescription();
        }
        mHeader.validate(mContext);

        pauseTimer(PERF_TAG_LAYOUT);
        if (sTimer != null && ++sLayoutCount >= PERF_LAYOUT_ITERATIONS) {
            sTimer.dumpResults();
            sTimer = new Timer();
            sLayoutCount = 0;
        }
    }

    private void setContentDescription() {
        if (mActivity.isAccessibilityEnabled()) {
            mHeader.resetContentDescription();
            setContentDescription(mHeader.getContentDescription(mContext));
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
        mHeader.checkboxVisible = mCheckboxesEnabled && !mConvListPhotosEnabled;

        final boolean isUnread = mHeader.unread;
        updateBackground(isUnread);

        mHeader.sendersDisplayText = new SpannableStringBuilder();
        mHeader.styledSendersString = new SpannableStringBuilder();

        // Parse senders fragments.
        if (mHeader.conversation.conversationInfo != null) {
            Context context = getContext();
            mHeader.messageInfoString = SendersView
                    .createMessageInfo(context, mHeader.conversation, true);
            int maxChars = ConversationItemViewCoordinates.getSendersLength(context,
                    ConversationItemViewCoordinates.getMode(context, mActivity.getViewMode()),
                    mHeader.conversation.hasAttachments);
            mHeader.displayableSenderEmails = new ArrayList<String>();
            mHeader.displayableSenderNames = new ArrayList<String>();
            mHeader.styledSenders = new ArrayList<SpannableString>();
            SendersView.format(context, mHeader.conversation.conversationInfo,
                    mHeader.messageInfoString.toString(), maxChars, mHeader.styledSenders,
                    mHeader.displayableSenderNames, mHeader.displayableSenderEmails, mAccount,
                    true);
            // If we have displayable senders, load their thumbnails
            loadSenderImages();
        } else {
            SendersView.formatSenders(mHeader, getContext(), true);
        }

        if (mAttachmentPreviewMode != ConversationItemViewCoordinates.ATTACHMENT_PREVIEW_NONE) {
            loadAttachmentPreviews();
        }

        if (mHeader.isLayoutValid(mContext)) {
            pauseTimer(PERF_TAG_CALCULATE_TEXTS_BITMAPS);
            return;
        }
        startTimer(PERF_TAG_CALCULATE_FOLDERS);


        pauseTimer(PERF_TAG_CALCULATE_FOLDERS);

        // Paper clip icon.
        mHeader.paperclip = null;
        if (mHeader.conversation.hasAttachments) {
            mHeader.paperclip = ATTACHMENT;
        }

        startTimer(PERF_TAG_CALCULATE_SENDER_SUBJECT);

        pauseTimer(PERF_TAG_CALCULATE_SENDER_SUBJECT);
        pauseTimer(PERF_TAG_CALCULATE_TEXTS_BITMAPS);
    }

    // FIXME(ath): maybe move this to bind(). the only dependency on layout is on tile W/H, which
    // is immutable.
    private void loadSenderImages() {
        if (mConvListPhotosEnabled && mHeader.displayableSenderEmails != null
                && mHeader.displayableSenderEmails.size() > 0) {
            if (mCoordinates.contactImagesWidth <= 0 || mCoordinates.contactImagesHeight <= 0) {
                LogUtils.w(LOG_TAG,
                        "Contact image width(%d) or height(%d) is 0 for mode: (%d).",
                        mCoordinates.contactImagesWidth, mCoordinates.contactImagesHeight, mMode);
                return;
            }
            mContactImagesHolder.setDimensions(mCoordinates.contactImagesWidth,
                    mCoordinates.contactImagesHeight);
            mContactImagesHolder.setDivisionIds(mHeader.displayableSenderEmails);
            int size = mHeader.displayableSenderEmails.size();
            String emailAddress;
            for (int i = 0; i < DividedImageCanvas.MAX_DIVISIONS && i < size; i++) {
                emailAddress = mHeader.displayableSenderEmails.get(i);
                PhotoIdentifier photoIdentifier = new ContactIdentifier(
                        mHeader.displayableSenderNames.get(i), emailAddress, i);
                sContactPhotoManager.loadThumbnail(photoIdentifier, mContactImagesHolder);
            }
        }
    }

    private void loadAttachmentPreviews() {
        if (mAttachmentPreviewMode != ConversationItemViewCoordinates.ATTACHMENT_PREVIEW_NONE) {
            final int attachmentPreviewsHeight = ConversationItemViewCoordinates
                    .getAttachmentPreviewsHeight(mContext, mAttachmentPreviewMode);
            if (mCoordinates.attachmentPreviewsWidth <= 0 || attachmentPreviewsHeight <= 0) {
                LogUtils.w(LOG_TAG,
                        "Attachment preview width(%d) or height(%d) is 0 for mode: (%d,%d).",
                        mCoordinates.attachmentPreviewsWidth, attachmentPreviewsHeight, mMode,
                        mAttachmentPreviewMode);
                return;
            }
            mAttachmentPreviewsCanvas.setDimensions(mCoordinates.attachmentPreviewsWidth,
                    attachmentPreviewsHeight);
            ArrayList<String> attachments = mHeader.conversation.getAttachments();
            mAttachmentPreviewsCanvas.setDivisionIds(attachments);
            int size = attachments.size();
            for (int i = 0; i < DividedImageCanvas.MAX_DIVISIONS && i < size; i++) {
                String attachment = attachments.get(i);
                PhotoIdentifier photoIdentifier = new ContactIdentifier(
                        attachment, attachment, i);
                sAttachmentPreviewsManager.loadThumbnail(
                        photoIdentifier, mAttachmentPreviewsCanvas);
            }
        }
    }

    private static int makeExactSpecForSize(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    private static void layoutViewExactly(View v, int w, int h) {
        v.measure(makeExactSpecForSize(w), makeExactSpecForSize(h));
        v.layout(0, 0, w, h);
    }

    private void layoutSenders() {
        if (mHeader.styledSendersString != null) {
            if (isActivated() && showActivatedText()) {
                mHeader.styledSendersString.setSpan(sActivatedTextSpan, 0,
                        mHeader.styledMessageInfoStringOffset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                mHeader.styledSendersString.removeSpan(sActivatedTextSpan);
            }

            final int w = mSendersWidth;
            final int h = mCoordinates.sendersHeight;
            mSendersTextView.setLayoutParams(new ViewGroup.LayoutParams(w, h));
            mSendersTextView.setMaxLines(mCoordinates.sendersLineCount);
            mSendersTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mCoordinates.sendersFontSize);
            layoutViewExactly(mSendersTextView, w, h);

            mSendersTextView.setText(mHeader.styledSendersString);
        }
    }

    private void createSubject(final boolean isUnread) {
        final String subject = filterTag(mHeader.conversation.subject);
        final String snippet = mHeader.conversation.getSnippet();
        final SpannableStringBuilder displayedStringBuilder = new SpannableStringBuilder(
                Conversation.getSubjectAndSnippetForDisplay(mContext, subject, snippet));

        // since spans affect text metrics, add spans to the string before measure/layout or fancy
        // ellipsizing
        final int subjectTextLength = (subject != null) ? subject.length() : 0;
        if (!TextUtils.isEmpty(subject)) {
            displayedStringBuilder.setSpan(TextAppearanceSpan.wrap(
                    isUnread ? sSubjectTextUnreadSpan : sSubjectTextReadSpan), 0, subjectTextLength,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (!TextUtils.isEmpty(snippet)) {
            final int startOffset = subjectTextLength;
            // Start after the end of the subject text; since the subject may be
            // "" or null, this could start at the 0th character in the subjectText string
            displayedStringBuilder.setSpan(ForegroundColorSpan.wrap(
                    isUnread ? sSnippetTextUnreadSpan : sSnippetTextReadSpan), startOffset,
                    displayedStringBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (isActivated() && showActivatedText()) {
            displayedStringBuilder.setSpan(sActivatedTextSpan, 0, displayedStringBuilder.length(),
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }

        final int subjectWidth = mCoordinates.subjectWidth;
        final int subjectHeight = mCoordinates.subjectHeight;
        mSubjectTextView.setLayoutParams(new ViewGroup.LayoutParams(subjectWidth, subjectHeight));
        mSubjectTextView.setMaxLines(mCoordinates.subjectLineCount);
        mSubjectTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mCoordinates.subjectFontSize);
        layoutViewExactly(mSubjectTextView, subjectWidth, subjectHeight);

        mSubjectTextView.setText(displayedStringBuilder);
    }

    /**
     * Returns the resource for the text color depending on whether the element is activated or not.
     * @param defaultColor
     */
    private int getFontColor(int defaultColor) {
        final boolean isBackGroundBlue = isActivated() && showActivatedText();
        return isBackGroundBlue ? sActivatedTextColor : defaultColor;
    }

    private boolean showActivatedText() {
        // For activated elements in tablet in conversation mode, we show an activated color, since
        // the background is dark blue for activated versus gray for non-activated.
        final boolean isListCollapsed = mContext.getResources().getBoolean(R.bool.list_collapsed);
        return mTabletDevice && !isListCollapsed;
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
        mDateX = mCoordinates.dateXEnd - (int) sPaint.measureText(
                mHeader.dateText != null ? mHeader.dateText.toString() : "");

        mPaperclipX = mDateX - ATTACHMENT.getWidth() - mCoordinates.datePaddingLeft;

        if (mConfig.isWide()) {
            // In wide mode, the end of the senders should align with
            // the start of the subject and is based on a max width.
            mSendersWidth = mCoordinates.sendersWidth;
        } else {
            // In normal mode, the width is based on where the date/attachment icon start.
            final int dateAttachmentStart;
            // Have this end near the paperclip or date, not the folders.
            if (mHeader.paperclip != null) {
                dateAttachmentStart = mPaperclipX - mCoordinates.paperclipPaddingLeft;
            } else {
                dateAttachmentStart = mDateX - mCoordinates.datePaddingLeft;
            }
            mSendersWidth = dateAttachmentStart - mCoordinates.sendersX;
        }

        // Second pass to layout each fragment.
        int sendersY = mCoordinates.sendersY - mCoordinates.sendersAscent;

        if (mHeader.styledSenders != null) {
            ellipsizeStyledSenders();
            layoutSenders();
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
            if (mSendersWidth < 0) {
                mSendersWidth = 0;
            }
            totalWidth = ellipsize(fixedWidth);
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

    private static SpannableString copyStyles(CharacterStyle[] spans, CharSequence newText) {
        SpannableString s = new SpannableString(newText);
        if (spans != null && spans.length > 0) {
            s.setSpan(spans[0], 0, s.length(), 0);
        }
        return s;
    }

    private int ellipsize(int fixedWidth) {
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
        if (mConvListPhotosEnabled) {
            canvas.save();
            drawContactImages(canvas);
            canvas.restore();
        } else if (mHeader.checkboxVisible) {
            Bitmap checkmark = mChecked ? CHECKMARK_ON : CHECKMARK_OFF;
            canvas.drawBitmap(checkmark, mCoordinates.checkmarkX, mCoordinates.checkmarkY, sPaint);
        }

        // Senders.
        boolean isUnread = mHeader.unread;
        // Old style senders; apply text colors/ sizes/ styling.
        canvas.save();
        if (mHeader.sendersDisplayLayout != null) {
            sPaint.setTextSize(mCoordinates.sendersFontSize);
            sPaint.setTypeface(SendersView.getTypeface(isUnread));
            sPaint.setColor(getFontColor(isUnread ?
                    sSendersTextColorUnread : sSendersTextColorRead));
            canvas.translate(mCoordinates.sendersX, mCoordinates.sendersY
                    + mHeader.sendersDisplayLayout.getTopPadding());
            mHeader.sendersDisplayLayout.draw(canvas);
        } else {
            drawSenders(canvas);
        }
        canvas.restore();


        // Subject.
        sPaint.setTypeface(Typeface.DEFAULT);
        canvas.save();
        drawSubject(canvas);
        canvas.restore();

        // Folders.
        if (mConfig.areFoldersVisible()) {
            mHeader.folderDisplayer.drawFolders(canvas, mCoordinates, mMode);
        }

        // If this folder has a color (combined view/Email), show it here
        if (mConfig.isColorBlockVisible()) {
            sFoldersPaint.setColor(mHeader.conversation.color);
            sFoldersPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(mCoordinates.colorBlockX, mCoordinates.colorBlockY,
                    mCoordinates.colorBlockX + mCoordinates.colorBlockWidth,
                    mCoordinates.colorBlockY + mCoordinates.colorBlockHeight, sFoldersPaint);
        }

        // Draw the reply state. Draw nothing if neither replied nor forwarded.
        if (mConfig.isReplyStateVisible()) {
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

        if (mConfig.isPersonalIndicatorVisible()) {
            canvas.drawBitmap(mHeader.personalLevelBitmap, mCoordinates.personalIndicatorX,
                    mCoordinates.personalIndicatorY, null);
        }

        // Date.
        sPaint.setTextSize(mCoordinates.dateFontSize);
        sPaint.setTypeface(Typeface.DEFAULT);
        sPaint.setColor(sDateTextColor);
        drawText(canvas, mHeader.dateText, mDateX, mCoordinates.dateYBaseline,
                sPaint);

        // Paper clip icon.
        if (mHeader.paperclip != null) {
            canvas.drawBitmap(mHeader.paperclip, mPaperclipX, mCoordinates.paperclipY, sPaint);
        }

        if (mStarEnabled) {
            // Star.
            canvas.drawBitmap(getStarBitmap(), mCoordinates.starX, mCoordinates.starY, sPaint);
        }

        // Attachment previews
        if (mAttachmentPreviewMode != ConversationItemViewCoordinates.ATTACHMENT_PREVIEW_NONE) {
            canvas.save();
            drawAttachmentPreviews(canvas);
            canvas.restore();
        }
    }

    private void drawContactImages(Canvas canvas) {
        canvas.translate(mCoordinates.contactImagesX, mCoordinates.contactImagesY);
        mContactImagesHolder.draw(canvas);
    }

    private void drawAttachmentPreviews(Canvas canvas) {
        canvas.translate(mCoordinates.attachmentPreviewsX, mCoordinates.attachmentPreviewsY);
        mAttachmentPreviewsCanvas.draw(canvas);
    }

    private void drawSubject(Canvas canvas) {
        canvas.translate(mCoordinates.subjectX, mCoordinates.subjectY);
        mSubjectTextView.draw(canvas);
    }

    private void drawSenders(Canvas canvas) {
        canvas.translate(mCoordinates.sendersX, mCoordinates.sendersY);
        mSendersTextView.draw(canvas);
    }

    private Bitmap getStarBitmap() {
        return mHeader.conversation.starred ? STAR_ON : STAR_OFF;
    }

    private static void drawText(Canvas canvas, CharSequence s, int x, int y, TextPaint paint) {
        canvas.drawText(s, 0, s.length(), x, y, paint);
    }

    /**
     * Set the background for this item based on:
     * 1. Read / Unread (unread messages have a lighter background)
     * 2. Tablet / Phone
     * 3. Checkbox checked / Unchecked (controls CAB color for item)
     * 4. Activated / Not activated (controls the blue highlight on tablet)
     * @param isUnread
     */
    private void updateBackground(boolean isUnread) {
        final boolean isListOnTablet = mTabletDevice && mActivity.getViewMode().isListMode();
        final int background;
        if (isUnread) {
            if (isListOnTablet) {
                if (mChecked) {
                    background = R.drawable.list_conversation_wide_unread_selected_holo;
                } else {
                    background = R.drawable.conversation_wide_unread_selector;
                }
            } else {
                if (mChecked) {
                    background = getCheckedActivatedBackground();
                } else {
                    background = R.drawable.conversation_unread_selector;
                }
            }
        } else {
            if (isListOnTablet) {
                if (mChecked) {
                    background = R.drawable.list_conversation_wide_read_selected_holo;
                } else {
                    background = R.drawable.conversation_wide_read_selector;
                }
            } else {
                if (mChecked) {
                    background = getCheckedActivatedBackground();
                } else {
                    background = R.drawable.conversation_read_selector;
                }
            }
        }
        setBackgroundResource(background);
    }

    private final int getCheckedActivatedBackground() {
        if (isActivated() && mTabletDevice) {
            return R.drawable.list_arrow_selected_holo;
        } else {
            return R.drawable.list_selected_holo;
        }
    }

    /**
     * Toggle the check mark on this view and update the conversation or begin
     * drag, if drag is enabled.
     */
    @Override
    public void toggleCheckMarkOrBeginDrag() {
        ViewMode mode = mActivity.getViewMode();
        if (!mTabletDevice || !mode.isListMode()) {
            toggleCheckMark();
        } else {
            beginDragMode();
        }
    }

    private void toggleCheckMark() {
        if (mHeader != null && mHeader.conversation != null) {
            mChecked = !mChecked;
            Conversation conv = mHeader.conversation;
            // Set the list position of this item in the conversation
            SwipeableListView listView = getListView();
            conv.position = mChecked && listView != null ? listView.getPositionForView(this)
                    : Conversation.NO_POSITION;
            if (mSelectedConversationSet != null) {
                mSelectedConversationSet.toggle(this, conv);
            }
            if (mSelectedConversationSet.isEmpty()) {
                listView.commitDestructiveActions(true);
            }
            // We update the background after the checked state has changed
            // now that we have a selected background asset. Setting the background
            // usually waits for a layout pass, but we don't need a full layout,
            // just an update to the background.
            requestLayout();
        }
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
        ConversationCursor cursor = (ConversationCursor) mAdapter.getCursor();
        if (cursor != null) {
            cursor.updateBoolean(mHeader.conversation, ConversationColumns.STARRED,
                    mHeader.conversation.starred);
        }
    }

    private boolean isTouchInCheckmark(float x, float y) {
        // Everything before senders and include a touch slop.
        return mHeader.checkboxVisible && x < mCoordinates.sendersX + sTouchSlop;
    }

    private boolean isTouchInStar(float x, float y) {
        // Everything after the star and include a touch slop.
        return mStarEnabled && x > mCoordinates.starX - sTouchSlop;
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

    private boolean onTouchEventNoSwipe(MotionEvent event) {
        boolean handled = false;

        int x = (int) event.getX();
        int y = (int) event.getY();
        mLastTouchX = x;
        mLastTouchY = y;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isTouchInCheckmark(x, y) || isTouchInStar(x, y)) {
                    mDownEvent = true;
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
        SwipeableListView list = getListView();
        if (list != null && list.getAdapter() != null) {
            int pos = list.findConversation(this, mHeader.conversation);
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

    /**
     * Reset any state associated with this conversation item view so that it
     * can be reused.
     */
    public void reset() {
        setAlpha(1f);
        setTranslationX(0f);
        mAnimatedHeightFraction = 1.0f;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setTranslationX(float translationX) {
        super.setTranslationX(translationX);

        final ViewParent vp = getParent();
        if (vp == null || !(vp instanceof SwipeableConversationItemView)) {
            LogUtils.w(LOG_TAG,
                    "CIV.setTranslationX unexpected ConversationItemView parent: %s x=%s",
                    vp, translationX);
        }

        // When a list item is being swiped or animated, ensure that the hosting view has a
        // background color set. We only enable the background during the X-translation effect to
        // reduce overdraw during normal list scrolling.
        final SwipeableConversationItemView parent = (SwipeableConversationItemView) vp;
        if (translationX != 0f) {
            parent.setBackgroundResource(R.color.swiped_bg_color);
        } else {
            parent.setBackgroundDrawable(null);
        }
    }

    /**
     * Grow the height of the item and fade it in when bringing a conversation
     * back from a destructive action.
     */
    public Animator createSwipeUndoAnimation() {
        ObjectAnimator undoAnimator = createTranslateXAnimation(true);
        return undoAnimator;
    }

    /**
     * Grow the height of the item and fade it in when bringing a conversation
     * back from a destructive action.
     */
    public Animator createUndoAnimation() {
        ObjectAnimator height = createHeightAnimation(true);
        Animator fade = ObjectAnimator.ofFloat(this, "alpha", 0, 1.0f);
        fade.setDuration(sShrinkAnimationDuration);
        fade.setInterpolator(new DecelerateInterpolator(2.0f));
        AnimatorSet transitionSet = new AnimatorSet();
        transitionSet.playTogether(height, fade);
        transitionSet.addListener(new HardwareLayerEnabler(this));
        return transitionSet;
    }

    /**
     * Grow the height of the item and fade it in when bringing a conversation
     * back from a destructive action.
     */
    public Animator createDestroyWithSwipeAnimation() {
        ObjectAnimator slide = createTranslateXAnimation(false);
        ObjectAnimator height = createHeightAnimation(false);
        AnimatorSet transitionSet = new AnimatorSet();
        transitionSet.playSequentially(slide, height);
        return transitionSet;
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

    public Animator createDestroyAnimation() {
        return createHeightAnimation(false);
    }

    private ObjectAnimator createHeightAnimation(boolean show) {
        final float start = show ? 0f : 1.0f;
        final float end = show ? 1.0f : 0f;
        ObjectAnimator height = ObjectAnimator.ofFloat(this, "animatedHeightFraction", start, end);
        height.setInterpolator(new DecelerateInterpolator(2.0f));
        height.setDuration(sShrinkAnimationDuration);
        return height;
    }

    // Used by animator
    public void setAnimatedHeightFraction(float height) {
        mAnimatedHeightFraction = height;
        requestLayout();
    }

    @Override
    public View getSwipeableView() {
        return this;
    }

    /**
     * Begin drag mode. Keep the conversation selected (NOT toggle selection) and start drag.
     */
    private void beginDragMode() {
        if (mLastTouchX < 0 || mLastTouchY < 0) {
            return;
        }
        // If this is already checked, don't bother unchecking it!
        if (!mChecked) {
            toggleCheckMark();
        }

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
            mDragDescY = getPadding(height, (int) mCoordinates.subjectFontSize)
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

    @Override
    public float getMinAllowScrollDistance() {
        return sScrollSlop;
    }
}
