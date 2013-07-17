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
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
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
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.AbsListView.OnScrollListener;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.R.color;
import com.android.mail.R.drawable;
import com.android.mail.R.integer;
import com.android.mail.R.string;
import com.android.mail.browse.ConversationItemViewModel.SenderFragment;
import com.android.mail.perf.Timer;
import com.android.mail.photo.MailPhotoViewActivity;
import com.android.mail.photomanager.AttachmentPreviewsManager;
import com.android.mail.photomanager.AttachmentPreviewsManager.AttachmentPreviewsDividedImageCanvas;
import com.android.mail.photomanager.AttachmentPreviewsManager.AttachmentPreviewsManagerCallback;
import com.android.mail.photomanager.ContactPhotoManager;
import com.android.mail.photomanager.ContactPhotoManager.ContactIdentifier;
import com.android.mail.photomanager.AttachmentPreviewsManager.AttachmentPreviewIdentifier;
import com.android.mail.photomanager.PhotoManager;
import com.android.mail.photomanager.PhotoManager.PhotoIdentifier;
import com.android.mail.providers.Address;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AttachmentRendition;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.providers.UIProvider.ConversationListIcon;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.ui.AnimatedAdapter;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.ConversationSelectionSet;
import com.android.mail.ui.DividedImageCanvas;
import com.android.mail.ui.DividedImageCanvas.InvalidateCallback;
import com.android.mail.ui.FolderDisplayer;
import com.android.mail.ui.ImageCanvas;
import com.android.mail.ui.SwipeableItemView;
import com.android.mail.ui.SwipeableListView;
import com.android.mail.ui.ViewMode;
import com.android.mail.utils.FolderUri;
import com.android.mail.utils.HardwareLayerEnabler;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class ConversationItemView extends View implements SwipeableItemView, ToggleableItem,
        InvalidateCallback, AttachmentPreviewsManagerCallback {

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
    private static Bitmap VISIBLE_CONVERSATION_CARET;
    private static Drawable RIGHT_EDGE_TABLET;
    private static Bitmap PLACEHOLDER;
    private static Drawable PROGRESS_BAR;

    private static String sSendersSplitToken;
    private static String sElidedPaddingToken;
    private static String sOverflowCountFormat;

    // Static colors.
    private static int sActivatedTextColor;
    private static int sSendersTextColorRead;
    private static int sSendersTextColorUnread;
    private static int sDateTextColor;
    private static int sAttachmentPreviewsBackgroundColor;
    private static int sOverflowBadgeColor;
    private static int sOverflowTextColor;
    private static int sStarTouchSlop;
    private static int sSenderImageTouchSlop;
    @Deprecated
    private static int sStandardScaledDimen;
    private static int sShrinkAnimationDuration;
    private static int sSlideAnimationDuration;
    private static int sAnimatingBackgroundColor;
    private static int sProgressAnimationDuration;
    private static int sFadeAnimationDuration;
    private static float sPlaceholderAnimationDurationRatio;
    private static int sProgressAnimationDelay;
    private static Interpolator sPulseAnimationInterpolator;
    private static int sOverflowCountMax;

    // Static paints.
    private static TextPaint sPaint = new TextPaint();
    private static TextPaint sFoldersPaint = new TextPaint();

    private static Rect sRect = new Rect();

    // Backgrounds for different states.
    private final SparseArray<Drawable> mBackgrounds = new SparseArray<Drawable>();

    // Dimensions and coordinates.
    private int mViewWidth = -1;
    /** The view mode at which we calculated mViewWidth previously. */
    private int mPreviousMode;

    private int mDateX;
    private int mPaperclipX;
    private int mSendersWidth;
    private int mOverflowX;
    private int mOverflowY;

    /** Whether we are on a tablet device or not */
    private final boolean mTabletDevice;
    /** Whether we are on an expansive tablet */
    private final boolean mIsExpansiveTablet;
    /** When in conversation mode, true if the list is hidden */
    private final boolean mListCollapsible;

    @VisibleForTesting
    ConversationItemViewCoordinates mCoordinates;

    private ConversationItemViewCoordinates.Config mConfig;

    private final Context mContext;

    public ConversationItemViewModel mHeader;
    private boolean mDownEvent;
    private boolean mSelected = false;
    private ConversationSelectionSet mSelectedConversationSet;
    private Folder mDisplayedFolder;
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
    private int mGadgetMode;
    private final DividedImageCanvas mContactImagesHolder;
    private static ContactPhotoManager sContactPhotoManager;


    private static int sFoldersLeftPadding;
    private static TextAppearanceSpan sSubjectTextUnreadSpan;
    private static TextAppearanceSpan sSubjectTextReadSpan;
    private static ForegroundColorSpan sSnippetTextUnreadSpan;
    private static ForegroundColorSpan sSnippetTextReadSpan;
    private static int sScrollSlop;
    private static CharacterStyle sActivatedTextSpan;

    private final AttachmentPreviewsDividedImageCanvas mAttachmentPreviewsCanvas;
    private static AttachmentPreviewsManager sAttachmentPreviewsManager;
    /**
     * Animates the mAnimatedProgressFraction field to make the progress bars spin. Cancelling
     * this animator does not remove the progress bars.
     */
    private final ObjectAnimator mProgressAnimator;
    private final ObjectAnimator mFadeAnimator0;
    private final ObjectAnimator mFadeAnimator1;
    private long mProgressAnimatorCancelledTime;
    /** Range from 0.0f to 1.0f. */
    private float mAnimatedProgressFraction;
    private float mAnimatedFadeFraction0;
    private float mAnimatedFadeFraction1;
    private int[] mImageLoadStatuses = new int[0];
    private boolean mShowProgressBar;
    private final Runnable mCancelProgressAnimatorRunnable;
    private final Runnable mSetShowProgressBarRunnable0;
    private final Runnable mSetShowProgressBarRunnable1;
    private static final boolean CONVLIST_ATTACHMENT_PREVIEWS_ENABLED = true;

    static {
        sPaint.setAntiAlias(true);
        sFoldersPaint.setAntiAlias(true);
    }

    public static void setScrollStateChanged(final int scrollState) {
        if (sContactPhotoManager == null) {
            return;
        }
        final boolean scrolling = scrollState != OnScrollListener.SCROLL_STATE_IDLE;
        final boolean flinging = scrollState == OnScrollListener.SCROLL_STATE_FLING;

        if (scrolling) {
            sAttachmentPreviewsManager.pause();
        } else {
            sAttachmentPreviewsManager.resume();
        }

        if (flinging) {
            sContactPhotoManager.pause();
        } else {
            sContactPhotoManager.resume();
        }
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
        public void loadConversationFolders(Conversation conv, final FolderUri ignoreFolderUri,
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

        private int measureFolders(int availableSpace, int cellSize) {
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

        public void drawFolders(Canvas canvas, ConversationItemViewCoordinates coordinates) {
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
            int maxFoldersCount = availableSpace / coordinates.getFolderMinimumWidth();
            int foldersCount = Math.min(mFoldersCount, maxFoldersCount);
            int averageWidth = availableSpace / foldersCount;
            int cellSize = coordinates.getFolderCellWidth();

            // TODO(ath): sFoldersPaint.measureText() is done 3x in this method. stop that.
            // Extra credit: maybe cache results across items as long as font size doesn't change.

            final int totalWidth = measureFolders(availableSpace, cellSize);
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
                    if (i < foldersCount - 1) {
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
        Utils.traceBeginSection("CIVC constructor");
        setClickable(true);
        setLongClickable(true);
        mContext = context.getApplicationContext();
        final Resources res = mContext.getResources();
        mTabletDevice = Utils.useTabletUI(res);
        mIsExpansiveTablet =
                mTabletDevice ? res.getBoolean(R.bool.use_expansive_tablet_ui) : false;
        mListCollapsible = res.getBoolean(R.bool.list_collapsible);
        mAccount = account;

        if (STAR_OFF == null) {
            // Initialize static bitmaps.
            STAR_OFF = BitmapFactory.decodeResource(res, R.drawable.ic_star_off);
            STAR_ON = BitmapFactory.decodeResource(res, R.drawable.ic_star_on);
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
            VISIBLE_CONVERSATION_CARET = BitmapFactory.decodeResource(res,
                    R.drawable.ic_carrot_holo);
            RIGHT_EDGE_TABLET = res.getDrawable(R.drawable.list_edge_tablet);
            PLACEHOLDER = BitmapFactory.decodeResource(res, drawable.ic_attachment_load);
            PROGRESS_BAR = res.getDrawable(drawable.progress_holo);

            // Initialize colors.
            sActivatedTextColor = res.getColor(R.color.senders_text_color_read);
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
            sAttachmentPreviewsBackgroundColor = res.getColor(color.ap_background_color);
            sOverflowBadgeColor = res.getColor(color.ap_overflow_badge_color);
            sOverflowTextColor = res.getColor(color.ap_overflow_text_color);
            sStarTouchSlop = res.getDimensionPixelSize(R.dimen.star_touch_slop);
            sSenderImageTouchSlop = res.getDimensionPixelSize(R.dimen.sender_image_touch_slop);
            sStandardScaledDimen = res.getDimensionPixelSize(R.dimen.standard_scaled_dimen);
            sShrinkAnimationDuration = res.getInteger(R.integer.shrink_animation_duration);
            sSlideAnimationDuration = res.getInteger(R.integer.slide_animation_duration);
            // Initialize static color.
            sSendersSplitToken = res.getString(R.string.senders_split_token);
            sElidedPaddingToken = res.getString(R.string.elided_padding_token);
            sOverflowCountFormat = res.getString(string.ap_overflow_format);
            sAnimatingBackgroundColor = res.getColor(R.color.animating_item_background_color);
            sScrollSlop = res.getInteger(R.integer.swipeScrollSlop);
            sFoldersLeftPadding = res.getDimensionPixelOffset(R.dimen.folders_left_padding);
            sContactPhotoManager = ContactPhotoManager.createContactPhotoManager(context);
            sAttachmentPreviewsManager = new AttachmentPreviewsManager(context);
            sProgressAnimationDuration = res.getInteger(integer.ap_progress_animation_duration);
            sFadeAnimationDuration = res.getInteger(integer.ap_fade_animation_duration);
            final int placeholderAnimationDuration = res
                    .getInteger(integer.ap_placeholder_animation_duration);
            sPlaceholderAnimationDurationRatio = sProgressAnimationDuration
                    / placeholderAnimationDuration;
            sProgressAnimationDelay = res.getInteger(integer.ap_progress_animation_delay);
            sPulseAnimationInterpolator = new AccelerateDecelerateInterpolator();
            sOverflowCountMax = res.getInteger(integer.ap_overflow_max_count);
        }

        mSendersTextView = new TextView(mContext);
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
        mAttachmentPreviewsCanvas = new AttachmentPreviewsDividedImageCanvas(context,
                new InvalidateCallback() {
                    @Override
                    public void invalidate() {
                        if (mCoordinates == null) {
                            return;
                        }
                        ConversationItemView.this.invalidate(
                                mCoordinates.attachmentPreviewsX, mCoordinates.attachmentPreviewsY,
                                mCoordinates.attachmentPreviewsX
                                        + mCoordinates.attachmentPreviewsWidth,
                                mCoordinates.attachmentPreviewsY
                                        + mCoordinates.attachmentPreviewsHeight);
                    }
                });

        mProgressAnimator = createProgressAnimator();
        mFadeAnimator0 = createFadeAnimator(0);
        mFadeAnimator1 = createFadeAnimator(1);
        mCancelProgressAnimatorRunnable = new Runnable() {
            @Override
            public void run() {
                if (mProgressAnimator.isStarted() && areAllImagesLoaded()) {
                    LogUtils.v(LOG_TAG, "progress animator: << stopped");
                    mProgressAnimator.cancel();
                }
            }
        };
        mSetShowProgressBarRunnable0 = new Runnable() {
            @Override
            public void run() {
                if (mImageLoadStatuses.length <= 0
                        || mImageLoadStatuses[0] != PhotoManager.STATUS_LOADING) {
                    return;
                }
                LogUtils.v(LOG_TAG, "progress bar 0: >>> set to true");
                mShowProgressBar = true;
                if (mFadeAnimator0.isStarted()) {
                    mFadeAnimator0.cancel();
                }
                mFadeAnimator0.start();
            }
        };
        mSetShowProgressBarRunnable1 = new Runnable() {
            @Override
            public void run() {
                if (mImageLoadStatuses.length <= 1
                        || mImageLoadStatuses[1] != PhotoManager.STATUS_LOADING) {
                    return;
                }
                LogUtils.v(LOG_TAG, "progress bar 1: >>> set to true");
                mShowProgressBar = true;
                if (mFadeAnimator1.isStarted()) {
                    mFadeAnimator1.cancel();
                }
                mFadeAnimator1.start();
            }
        };
        Utils.traceEndSection();
    }

    public void bind(Conversation conversation, ControllableActivity activity,
            ConversationSelectionSet set, Folder folder, int checkboxOrSenderImage,
            boolean swipeEnabled, boolean priorityArrowEnabled, AnimatedAdapter adapter) {
        Utils.traceBeginSection("CIVC.bind");
        bind(ConversationItemViewModel.forConversation(mAccount, conversation), activity, set,
                folder, checkboxOrSenderImage, swipeEnabled, priorityArrowEnabled, adapter);
        Utils.traceEndSection();
    }

    private void bind(ConversationItemViewModel header, ControllableActivity activity,
            ConversationSelectionSet set, Folder folder, int checkboxOrSenderImage,
            boolean swipeEnabled, boolean priorityArrowEnabled, AnimatedAdapter adapter) {
        boolean attachmentPreviewsChanged = false;
        if (mHeader != null) {
            // If this was previously bound to a different conversation, remove any contact photo
            // manager requests.
            if (header.conversation.id != mHeader.conversation.id ||
                    (mHeader.displayableSenderNames != null && !mHeader.displayableSenderNames
                    .equals(header.displayableSenderNames))) {
                ArrayList<String> divisionIds = mContactImagesHolder.getDivisionIds();
                if (divisionIds != null) {
                    mContactImagesHolder.reset();
                    for (int pos = 0; pos < divisionIds.size(); pos++) {
                        sContactPhotoManager.removePhoto(ContactPhotoManager.generateHash(
                                mContactImagesHolder, pos, divisionIds.get(pos)));
                    }
                }
            }

            // If this was previously bound to a different conversation,
            // remove any attachment preview manager requests.
            if (header.conversation.id != mHeader.conversation.id
                    || header.conversation.attachmentPreviewsCount
                            != mHeader.conversation.attachmentPreviewsCount
                    || !header.conversation.getAttachmentPreviewUris()
                            .equals(mHeader.conversation.getAttachmentPreviewUris())) {
                attachmentPreviewsChanged = true;
                ArrayList<String> divisionIds = mAttachmentPreviewsCanvas.getDivisionIds();
                if (divisionIds != null) {
                    mAttachmentPreviewsCanvas.reset();
                    for (int pos = 0; pos < divisionIds.size(); pos++) {
                        String uri = divisionIds.get(pos);
                        for (int rendition : AttachmentRendition.PREFERRED_RENDITIONS) {
                            AttachmentPreviewIdentifier id = new AttachmentPreviewIdentifier(uri,
                                    rendition, 0, 0);
                            sAttachmentPreviewsManager
                                    .removePhoto(AttachmentPreviewsManager.generateHash(
                                            mAttachmentPreviewsCanvas, id.getKey()));
                        }
                    }
                }
            }
        }
        mCoordinates = null;
        mHeader = header;
        mActivity = activity;
        mSelectedConversationSet = set;
        mDisplayedFolder = folder;
        mStarEnabled = folder != null && !folder.isTrash();
        mSwipeEnabled = swipeEnabled;
        mAdapter = adapter;
        final int attachmentPreviewsSize = mHeader.conversation.getAttachmentPreviewUris().size();
        if (attachmentPreviewsChanged || mImageLoadStatuses.length != attachmentPreviewsSize) {
            mImageLoadStatuses = new int[attachmentPreviewsSize];
        }

        if (checkboxOrSenderImage == ConversationListIcon.SENDER_IMAGE) {
            mGadgetMode = ConversationItemViewCoordinates.GADGET_CONTACT_PHOTO;
        } else {
            mGadgetMode = ConversationItemViewCoordinates.GADGET_NONE;
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

        mHeader.folderDisplayer.loadConversationFolders(mHeader.conversation,
                mDisplayedFolder.folderUri, ignoreFolderType);

        mHeader.dateText = DateUtils.getRelativeTimeSpanString(mContext,
                mHeader.conversation.dateMs);

        mConfig = new ConversationItemViewCoordinates.Config()
            .withGadget(mGadgetMode)
            .withAttachmentPreviews(getAttachmentPreviewsMode());
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

        int overflowCount = Math.min(getOverflowCount(), sOverflowCountMax);
        mHeader.overflowText = String.format(sOverflowCountFormat, overflowCount);

        setContentDescription();
        requestLayout();
    }

    /**
     * Get the Conversation object associated with this view.
     */
    public Conversation getConversation() {
        return mHeader.conversation;
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
        Utils.traceBeginSection("CIVC.measure");
        final int wSize = MeasureSpec.getSize(widthMeasureSpec);

        final int currentMode = mActivity.getViewMode().getMode();
        if (wSize != mViewWidth || mPreviousMode != currentMode) {
            mViewWidth = wSize;
            mPreviousMode = currentMode;
        }
        mHeader.viewWidth = mViewWidth;

        mConfig.updateWidth(wSize).setViewMode(currentMode);

        Resources res = getResources();
        mHeader.standardScaledDimen = res.getDimensionPixelOffset(R.dimen.standard_scaled_dimen);

        mCoordinates = ConversationItemViewCoordinates.forConfig(mContext, mConfig,
                mAdapter.getCoordinatesCache());

        final int h = (mAnimatedHeightFraction != 1.0f) ?
                Math.round(mAnimatedHeightFraction * mCoordinates.height) : mCoordinates.height;
        setMeasuredDimension(mConfig.getWidth(), h);
        Utils.traceEndSection();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        startTimer(PERF_TAG_LAYOUT);
        Utils.traceBeginSection("CIVC.layout");

        super.onLayout(changed, left, top, right, bottom);

        Utils.traceBeginSection("text and bitmaps");
        calculateTextsAndBitmaps();
        Utils.traceEndSection();

        Utils.traceBeginSection("coordinates");
        calculateCoordinates();
        Utils.traceEndSection();

        // Subject.
        createSubject(mHeader.unread);

        if (!mHeader.isLayoutValid()) {
            setContentDescription();
        }
        mHeader.validate();

        pauseTimer(PERF_TAG_LAYOUT);
        if (sTimer != null && ++sLayoutCount >= PERF_LAYOUT_ITERATIONS) {
            sTimer.dumpResults();
            sTimer = new Timer();
            sLayoutCount = 0;
        }
        Utils.traceEndSection();
    }

    private void setContentDescription() {
        if (mActivity.isAccessibilityEnabled()) {
            mHeader.resetContentDescription();
            setContentDescription(mHeader.getContentDescription(mContext));
        }
    }

    @Override
    public void setBackgroundResource(int resourceId) {
        Utils.traceBeginSection("set background resource");
        Drawable drawable = mBackgrounds.get(resourceId);
        if (drawable == null) {
            drawable = getResources().getDrawable(resourceId);
            mBackgrounds.put(resourceId, drawable);
        }
        if (getBackground() != drawable) {
            super.setBackgroundDrawable(drawable);
        }
        Utils.traceEndSection();
    }

    private void calculateTextsAndBitmaps() {
        startTimer(PERF_TAG_CALCULATE_TEXTS_BITMAPS);

        if (mSelectedConversationSet != null) {
            mSelected = mSelectedConversationSet.contains(mHeader.conversation);
        }
        setSelected(mSelected);
        mHeader.gadgetMode = mGadgetMode;

        final boolean isUnread = mHeader.unread;
        updateBackground(isUnread);

        mHeader.sendersDisplayText = new SpannableStringBuilder();
        mHeader.styledSendersString = null;

        // Parse senders fragments.
        if (mHeader.conversation.conversationInfo != null) {
            // This is Gmail
            Context context = getContext();
            mHeader.messageInfoString = SendersView
                    .createMessageInfo(context, mHeader.conversation, true);
            int maxChars = ConversationItemViewCoordinates.getSendersLength(context,
                    mCoordinates.getMode(), mHeader.conversation.hasAttachments);
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
            // This is Email
            SendersView.formatSenders(mHeader, getContext(), true);
            if (!TextUtils.isEmpty(mHeader.conversation.senders)) {
                mHeader.displayableSenderEmails = new ArrayList<String>();
                mHeader.displayableSenderNames = new ArrayList<String>();

                final Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(mHeader.conversation.senders);
                for (int i = 0; i < tokens.length;i++) {
                    final Rfc822Token token = tokens[i];
                    final String senderName = Address.decodeAddressName(token.getName());
                    final String senderAddress = token.getAddress();
                    mHeader.displayableSenderEmails.add(senderAddress);
                    mHeader.displayableSenderNames.add(
                            !TextUtils.isEmpty(senderName) ? senderName : senderAddress);
                }
                loadSenderImages();
            }
        }

        if (isAttachmentPreviewsEnabled()) {
            loadAttachmentPreviews();
        }

        if (mHeader.isLayoutValid()) {
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

    private boolean isAttachmentPreviewsEnabled() {
        return CONVLIST_ATTACHMENT_PREVIEWS_ENABLED
                && !mHeader.conversation.getAttachmentPreviewUris().isEmpty();
    }

    private boolean getOverflowCountVisible() {
        return isAttachmentPreviewsEnabled() && getOverflowCount() > 0;
    }

    private int getOverflowCount() {
        return mHeader.conversation.attachmentPreviewsCount - mHeader.conversation
                .getAttachmentPreviewUris().size();
    }

    private int getAttachmentPreviewsMode() {
        if (isAttachmentPreviewsEnabled()) {
            return mHeader.conversation.read
                    ? ConversationItemViewCoordinates.ATTACHMENT_PREVIEW_READ
                    : ConversationItemViewCoordinates.ATTACHMENT_PREVIEW_UNREAD;
        } else {
            return ConversationItemViewCoordinates.ATTACHMENT_PREVIEW_NONE;
        }
    }

    // FIXME(ath): maybe move this to bind(). the only dependency on layout is on tile W/H, which
    // is immutable.
    private void loadSenderImages() {
        if (mGadgetMode == ConversationItemViewCoordinates.GADGET_CONTACT_PHOTO
                && mHeader.displayableSenderEmails != null
                && mHeader.displayableSenderEmails.size() > 0) {
            if (mCoordinates.contactImagesWidth <= 0 || mCoordinates.contactImagesHeight <= 0) {
                LogUtils.w(LOG_TAG,
                        "Contact image width(%d) or height(%d) is 0 for mode: (%d).",
                        mCoordinates.contactImagesWidth, mCoordinates.contactImagesHeight,
                        mCoordinates.getMode());
                return;
            }

            int size = mHeader.displayableSenderEmails.size();
            final List<Object> keys = Lists.newArrayListWithCapacity(size);
            for (int i = 0; i < DividedImageCanvas.MAX_DIVISIONS && i < size; i++) {
                keys.add(mHeader.displayableSenderEmails.get(i));
            }

            mContactImagesHolder.setDimensions(mCoordinates.contactImagesWidth,
                    mCoordinates.contactImagesHeight);
            mContactImagesHolder.setDivisionIds(keys);
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
        if (!isAttachmentPreviewsEnabled()) {
            return;
        }
        if (mCoordinates.attachmentPreviewsWidth <= 0
                || mCoordinates.attachmentPreviewsHeight <= 0) {
            LogUtils.w(LOG_TAG,
                    "Attachment preview width(%d) or height(%d) is 0 for mode: (%d,%d).",
                    mCoordinates.attachmentPreviewsWidth, mCoordinates.attachmentPreviewsHeight,
                    mCoordinates.getMode(), getAttachmentPreviewsMode());
            return;
        }
        Utils.traceBeginSection("attachment previews");

        Utils.traceBeginSection("Setup load attachment previews");

        LogUtils.d(LOG_TAG,
                "loadAttachmentPreviews: Loading attachment previews for conversation %s",
                mHeader.conversation);

        // Get list of attachments and states from conversation
        final ArrayList<String> attachmentUris = mHeader.conversation.getAttachmentPreviewUris();
        final int previewStates = mHeader.conversation.attachmentPreviewStates;
        final int displayCount = Math.min(attachmentUris.size(), DividedImageCanvas.MAX_DIVISIONS);
        Utils.traceEndSection();

        final List<AttachmentPreviewIdentifier> ids = Lists.newArrayListWithCapacity(displayCount);
        final List<Object> keys = Lists.newArrayListWithCapacity(displayCount);
        // First pass: Create and set the rendition on each load request
        for (int i = 0; i < displayCount; i++) {
            Utils.traceBeginSection("finding rendition of attachment preview");
            final String uri = attachmentUris.get(i);

            // Find the rendition to load based on availability.
            LogUtils.v(LOG_TAG, "loadAttachmentPreviews: state [BEST, SIMPLE] is [%s, %s] for %s ",
                    Attachment.getPreviewState(previewStates, i, AttachmentRendition.BEST),
                    Attachment.getPreviewState(previewStates, i, AttachmentRendition.SIMPLE),
                    uri);
            int bestAvailableRendition = -1;
            // BEST first, else use less preferred renditions
            for (final int rendition : AttachmentRendition.PREFERRED_RENDITIONS) {
                if (Attachment.getPreviewState(previewStates, i, rendition)) {
                    bestAvailableRendition = rendition;
                    break;
                }
            }

            final AttachmentPreviewIdentifier photoIdentifier = new AttachmentPreviewIdentifier(uri,
                    bestAvailableRendition, mHeader.conversation.id, i);
            ids.add(photoIdentifier);
            keys.add(photoIdentifier.getKey());
            Utils.traceEndSection();
        }

        Utils.traceBeginSection("preparing divided image canvas");
        // Prepare the canvas.
        mAttachmentPreviewsCanvas.setDimensions(mCoordinates.attachmentPreviewsWidth,
                mCoordinates.attachmentPreviewsHeight);
        mAttachmentPreviewsCanvas.setDivisionIds(keys);
        Utils.traceEndSection();

        // Second pass: Find the dimensions to load and start the load request
        final ImageCanvas.Dimensions canvasDimens = new ImageCanvas.Dimensions();
        for (int i = 0; i < displayCount; i++) {
            Utils.traceBeginSection("finding dimensions");
            final PhotoIdentifier photoIdentifier = ids.get(i);
            final Object key = keys.get(i);
            mAttachmentPreviewsCanvas.getDesiredDimensions(key, canvasDimens);
            Utils.traceEndSection();

            Utils.traceBeginSection("start animator");
            if (!mProgressAnimator.isStarted()) {
                LogUtils.v(LOG_TAG, "progress animator: >> started");
                // Reduce progress bar stutter caused by reset()/bind() being called multiple
                // times.
                final long time = SystemClock.uptimeMillis();
                final long dt = time - mProgressAnimatorCancelledTime;
                float passedFraction = 0;
                if (mProgressAnimatorCancelledTime != 0 && dt > 0) {
                    mProgressAnimatorCancelledTime = 0;
                    passedFraction = (float) dt / sProgressAnimationDuration % 1.0f;
                    LogUtils.v(LOG_TAG, "progress animator: correction for dt %d, fraction %f",
                            dt, passedFraction);
                }
                removeCallbacks(mCancelProgressAnimatorRunnable);
                mProgressAnimator.start();
                // Wow.. this must be called after start().
                mProgressAnimator.setCurrentPlayTime((long) (sProgressAnimationDuration * (
                        (mAnimatedProgressFraction + passedFraction) % 1.0f)));
            }
            Utils.traceEndSection();

            Utils.traceBeginSection("start load");
            LogUtils.d(LOG_TAG, "loadAttachmentPreviews: start loading %s", photoIdentifier);
            sAttachmentPreviewsManager
                    .loadThumbnail(photoIdentifier, mAttachmentPreviewsCanvas, canvasDimens, this);
            Utils.traceEndSection();
        }

        Utils.traceEndSection();
    }

    @Override
    public void onImageDrawn(final Object key, final boolean success) {
        if (mHeader == null || mHeader.conversation == null) {
            return;
        }
        Utils.traceBeginSection("on image drawn");
        final String uri = AttachmentPreviewsManager.transformKeyToUri(key);
        final int index = mHeader.conversation.getAttachmentPreviewUris().indexOf(uri);

        LogUtils.v(LOG_TAG,
                "loadAttachmentPreviews: <= onImageDrawn callback [%b] on index %d for %s", success,
                index, key);
        // We want to hide the spinning progress bar when we draw something.
        onImageLoadStatusChanged(index,
                success ? PhotoManager.STATUS_LOADED : PhotoManager.STATUS_NOT_LOADED);

        if (mProgressAnimator.isStarted() && areAllImagesLoaded()) {
            removeCallbacks(mCancelProgressAnimatorRunnable);
            postDelayed(mCancelProgressAnimatorRunnable, sFadeAnimationDuration);
        }
        Utils.traceEndSection();
    }

    @Override
    public void onImageLoadStarted(final Object key) {
        if (mHeader == null || mHeader.conversation == null) {
            return;
        }
        final String uri = AttachmentPreviewsManager.transformKeyToUri(key);
        final int index = mHeader.conversation.getAttachmentPreviewUris().indexOf(uri);

        LogUtils.v(LOG_TAG,
                "loadAttachmentPreviews: <= onImageLoadStarted callback on index %d for %s", index,
                key);
        onImageLoadStatusChanged(index, PhotoManager.STATUS_LOADING);
    }

    private boolean areAllImagesLoaded() {
        for (int i = 0; i < mImageLoadStatuses.length; i++) {
            if (mImageLoadStatuses[i] != PhotoManager.STATUS_LOADED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Update the #mImageLoadStatuses state array with special logic.
     * @param index Which attachment preview's state to update.
     * @param status What the new state is.
     */
    private void onImageLoadStatusChanged(final int index, final int status) {
        if (index < 0 || index >= mImageLoadStatuses.length) {
            return;
        }
        final int prevStatus = mImageLoadStatuses[index];
        if (prevStatus == status) {
            return;
        }

        boolean changed = false;
        switch (status) {
            case PhotoManager.STATUS_NOT_LOADED:
                LogUtils.v(LOG_TAG, "progress bar: <<< set to false");
                mShowProgressBar = false;
                // Cannot transition directly from LOADING to NOT_LOADED.
                if (prevStatus != PhotoManager.STATUS_LOADING) {
                    mImageLoadStatuses[index] = status;
                    changed = true;
                }
                break;
            case PhotoManager.STATUS_LOADING:
                // All other statuses must be set to not loading.
                for (int i = 0; i < mImageLoadStatuses.length; i++) {
                    if (i != index && mImageLoadStatuses[i] == PhotoManager.STATUS_LOADING) {
                        mImageLoadStatuses[i] = PhotoManager.STATUS_NOT_LOADED;
                    }
                }
                mImageLoadStatuses[index] = status;

                // Progress bar should only be shown after a delay
                LogUtils.v(LOG_TAG, "progress bar: <<< set to false");
                mShowProgressBar = false;
                LogUtils.v(LOG_TAG, "progress bar: === start delay");
                final Runnable setShowProgressBarRunnable = index == 0
                        ? mSetShowProgressBarRunnable0 : mSetShowProgressBarRunnable1;
                removeCallbacks(setShowProgressBarRunnable);
                postDelayed(setShowProgressBarRunnable, sProgressAnimationDelay);
                changed = true;
                break;
            case PhotoManager.STATUS_LOADED:
                mImageLoadStatuses[index] = status;
                changed = true;
                break;
        }
        if (changed) {
            final ObjectAnimator fadeAnimator = index == 0 ? mFadeAnimator0 : mFadeAnimator1;
            if (!fadeAnimator.isStarted()) {
                fadeAnimator.start();
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
        final Spannable displayedStringBuilder = new SpannableString(
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

    private boolean showActivatedText() {
        // For activated elements in tablet in conversation mode, we show an activated color, since
        // the background is dark blue for activated versus gray for non-activated.
        return mTabletDevice && !mListCollapsible;
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

        if (mCoordinates.isWide()) {
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

        sPaint.setTextSize(mCoordinates.sendersFontSize);
        sPaint.setTypeface(Typeface.DEFAULT);

        if (mHeader.styledSenders != null) {
            ellipsizeStyledSenders();
            layoutSenders();
        } else {
            // First pass to calculate width of each fragment.
            int totalWidth = 0;
            int fixedWidth = 0;
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

            if (!ConversationItemViewCoordinates.displaySendersInline(mCoordinates.getMode())) {
                sendersY += totalWidth <= mSendersWidth ? mCoordinates.sendersLineHeight / 2 : 0;
            }
            if (mSendersWidth < 0) {
                mSendersWidth = 0;
            }
            totalWidth = ellipsize(fixedWidth);
            mHeader.sendersDisplayLayout = new StaticLayout(mHeader.sendersDisplayText, sPaint,
                    mSendersWidth, Alignment.ALIGN_NORMAL, 1, 0, true);
        }

        if (mSendersWidth < 0) {
            mSendersWidth = 0;
        }

        String overflowText = mHeader.overflowText != null ? mHeader.overflowText : "";
        sPaint.setTextSize(mCoordinates.overflowFontSize);
        sPaint.setTypeface(mCoordinates.overflowTypeface);

        sPaint.getTextBounds(overflowText, 0, overflowText.length(), sRect);

        final int overflowWidth = (int) sPaint.measureText(overflowText);
        final int overflowHeight = sRect.height();
        mOverflowX = mCoordinates.overflowXEnd - mCoordinates.overflowDiameter / 2
                - overflowWidth / 2;
        mOverflowY = mCoordinates.overflowYEnd - mCoordinates.overflowDiameter / 2
                + overflowHeight / 2;

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
        builder.append(messageInfoString);
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
        Utils.traceBeginSection("CIVC.draw");

        // Contact photo
        if (mGadgetMode == ConversationItemViewCoordinates.GADGET_CONTACT_PHOTO) {
            canvas.save();
            drawContactImages(canvas);
            canvas.restore();
        }

        // Senders.
        boolean isUnread = mHeader.unread;
        // Old style senders; apply text colors/ sizes/ styling.
        canvas.save();
        if (mHeader.sendersDisplayLayout != null) {
            sPaint.setTextSize(mCoordinates.sendersFontSize);
            sPaint.setTypeface(SendersView.getTypeface(isUnread));
            sPaint.setColor(isUnread ? sSendersTextColorUnread : sSendersTextColorRead);
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
            mHeader.folderDisplayer.drawFolders(canvas, mCoordinates);
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
        if (isAttachmentPreviewsEnabled()) {
            canvas.save();
            drawAttachmentPreviews(canvas);
            canvas.restore();

            // Overflow badge and count
            if (getOverflowCountVisible() && areAllImagesLoaded()) {
                final float radius = mCoordinates.overflowDiameter / 2;
                sPaint.setColor(sOverflowBadgeColor);
                canvas.drawCircle(mCoordinates.overflowXEnd - radius,
                        mCoordinates.overflowYEnd - radius, radius, sPaint);

                sPaint.setTextSize(mCoordinates.overflowFontSize);
                sPaint.setTypeface(mCoordinates.overflowTypeface);
                sPaint.setColor(sOverflowTextColor);
                drawText(canvas, mHeader.overflowText, mOverflowX, mOverflowY, sPaint);
            }

            // Placeholders and progress bars

            // Fade from 55 -> 255 -> 55. Each cycle lasts for #sProgressAnimationDuration secs.
            final int maxAlpha = 255, minAlpha = 55;
            final int range = maxAlpha - minAlpha;
            // We want the placeholder to pulse at a different rate from the progressbar to
            // spin.
            final float placeholderAnimFraction = mAnimatedProgressFraction
                    * sPlaceholderAnimationDurationRatio;
            // During the time that placeholderAnimFraction takes to go from 0 to 1, we
            // want to go all the way to #maxAlpha and back down to #minAlpha. So from 0 to 0.5,
            // we increase #modifiedProgress from 0 to 1, while from 0.5 to 1 we decrease
            // accordingly from 1 to 0. Math.
            final float modifiedProgress = -2 * Math.abs(placeholderAnimFraction - 0.5f) + 1;
            // Make it feel like a heart beat.
            final float interpolatedProgress = sPulseAnimationInterpolator
                    .getInterpolation(modifiedProgress);
            // More math.
            final int pulseAlpha = (int) (interpolatedProgress * range + minAlpha);

            final int count = mImageLoadStatuses.length;
            for (int i = 0; i < count; i++) {
                drawPlaceholder(canvas, i, count, pulseAlpha);
                drawProgressBar(canvas, i, count);
            }
        }

        // right-side edge effect when in tablet conversation mode and the list is not collapsed
        if (mTabletDevice && !mListCollapsible &&
                ViewMode.isConversationMode(mConfig.getViewMode())) {
            RIGHT_EDGE_TABLET.setBounds(getWidth() - RIGHT_EDGE_TABLET.getIntrinsicWidth(), 0,
                    getWidth(), getHeight());
            RIGHT_EDGE_TABLET.draw(canvas);

            if (isActivated()) {
                // draw caret on the right, centered vertically
                final int x = getWidth() - VISIBLE_CONVERSATION_CARET.getWidth();
                final int y = (getHeight() - VISIBLE_CONVERSATION_CARET.getHeight()) / 2;
                canvas.drawBitmap(VISIBLE_CONVERSATION_CARET, x, y, null);
            }
        }
        Utils.traceEndSection();
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

    /**
     * Draws the specified placeholder on the canvas.
     *
     * @param canvas The canvas to draw on.
     * @param index  If drawing multiple placeholders, this determines which one we are drawing.
     * @param total  Whether we are drawing multiple placeholders.
     * @param pulseAlpha The alpha to draw this at.
     */
    private void drawPlaceholder(final Canvas canvas, final int index, final int total,
            final int pulseAlpha) {
        final int placeholderX = getAttachmentPreviewXCenter(index, total)
                - mCoordinates.placeholderWidth / 2;
        if (placeholderX == -1) {
            return;
        }

        // Set alpha for crossfading effect.
        final ObjectAnimator fadeAnimator = index == 0 ? mFadeAnimator0 : mFadeAnimator1;
        final float animatedFadeFraction = index == 0 ? mAnimatedFadeFraction0
                : mAnimatedFadeFraction1;
        final boolean notLoaded = mImageLoadStatuses[index] == PhotoManager.STATUS_NOT_LOADED;
        final boolean loading = mImageLoadStatuses[index] == PhotoManager.STATUS_LOADING;
        final boolean loaded = mImageLoadStatuses[index] == PhotoManager.STATUS_LOADED;
        final float fadeAlphaFraction;
        if (notLoaded) {
            fadeAlphaFraction = 1.0f;
        } else if (loading && !mShowProgressBar) {
            fadeAlphaFraction = 1.0f;
        } else if (fadeAnimator.isStarted() && (loading && mShowProgressBar
                || loaded && !mShowProgressBar)) {
            // Fade to progress bar or fade to image from placeholder
            fadeAlphaFraction = 1.0f - animatedFadeFraction;
        } else {
            fadeAlphaFraction = 0.0f;
        }

        if (fadeAlphaFraction == 0.0f) {
            return;
        }
        // Draw background
        drawAttachmentPreviewBackground(canvas, index, total, (int) (255 * fadeAlphaFraction));

        sPaint.setAlpha((int) (pulseAlpha * fadeAlphaFraction));
        canvas.drawBitmap(PLACEHOLDER, placeholderX, mCoordinates.placeholderY, sPaint);
    }

    /**
     * Draws the specified progress bar on the canvas.
     * @param canvas The canvas to draw on.
     * @param index If drawing multiple progress bars, this determines which one we are drawing.
     * @param total Whether we are drawing multiple progress bars.
     */
    private void drawProgressBar(final Canvas canvas, final int index, final int total) {
        final int progressBarX = getAttachmentPreviewXCenter(index, total)
                - mCoordinates.progressBarWidth / 2;
        if (progressBarX == -1) {
            return;
        }

        // Set alpha for crossfading effect.
        final ObjectAnimator fadeAnimator = index == 0 ? mFadeAnimator0 : mFadeAnimator1;
        final float animatedFadeFraction = index == 0 ? mAnimatedFadeFraction0
                : mAnimatedFadeFraction1;
        final boolean loading = mImageLoadStatuses[index] == PhotoManager.STATUS_LOADING;
        final boolean loaded = mImageLoadStatuses[index] == PhotoManager.STATUS_LOADED;
        final int fadeAlpha;
        if (loading && mShowProgressBar) {
            fadeAlpha = (int) (255 * animatedFadeFraction);
        } else if (fadeAnimator.isStarted() && (loaded && mShowProgressBar)) {
            // Fade to image from progress bar
            fadeAlpha = (int) (255 * (1.0f - animatedFadeFraction));
        } else {
            fadeAlpha = 0;
        }

        if (fadeAlpha == 0) {
            return;
        }

        // Draw background
        drawAttachmentPreviewBackground(canvas, index, total, fadeAlpha);

        // Set the level from 0 to 10000 to animate the Drawable.
        PROGRESS_BAR.setLevel((int) (mAnimatedProgressFraction * 10000));
        // canvas.translate() for Bitmaps, setBounds() for Drawables.
        PROGRESS_BAR.setBounds(progressBarX, mCoordinates.progressBarY,
                progressBarX + mCoordinates.progressBarWidth,
                mCoordinates.progressBarY + mCoordinates.progressBarHeight);
        PROGRESS_BAR.setAlpha(fadeAlpha);
        PROGRESS_BAR.draw(canvas);
    }
    /**
     * Draws the specified attachment previews background on the canvas.
     * @param canvas The canvas to draw on.
     * @param index If drawing for multiple attachment previews, this determines for which one's
     *              background we are drawing.
     * @param total Whether we are drawing for multiple attachment previews.
     * @param fadeAlpha The alpha to draw this at.
     */
    private void drawAttachmentPreviewBackground(final Canvas canvas, final int index,
            final int total, final int fadeAlpha) {
        if (total == 0) {
            return;
        }
        final int sectionWidth = mCoordinates.attachmentPreviewsWidth / total;
        final int sectionX = getAttachmentPreviewX(index, total);
        sPaint.setColor(sAttachmentPreviewsBackgroundColor);
        sPaint.setAlpha(fadeAlpha);
        canvas.drawRect(sectionX, mCoordinates.attachmentPreviewsY, sectionX + sectionWidth,
                mCoordinates.attachmentPreviewsY + mCoordinates.attachmentPreviewsHeight, sPaint);
    }

    private void invalidateAttachmentPreviews() {
        final int total = mImageLoadStatuses.length;
        for (int index = 0; index < total; index++) {
            invalidateAttachmentPreview(index, total);
        }
    }

    private void invalidatePlaceholdersAndProgressBars() {
        final int total = mImageLoadStatuses.length;
        for (int index = 0; index < total; index++) {
            invalidatePlaceholderAndProgressBar(index, total);
        }
    }

    private void invalidateAttachmentPreview(final int index, final int total) {
        invalidate(getAttachmentPreviewX(index, total), mCoordinates.attachmentPreviewsY,
                getAttachmentPreviewX(index + 1, total),
                mCoordinates.attachmentPreviewsY + mCoordinates.attachmentPreviewsHeight);
    }

    private void invalidatePlaceholderAndProgressBar(final int index, final int total) {
        final int width = Math.max(mCoordinates.placeholderWidth, mCoordinates.progressBarWidth);
        final int height = Math.max(mCoordinates.placeholderHeight, mCoordinates.progressBarHeight);
        final int x = getAttachmentPreviewXCenter(index, total) - width / 2;
        final int xEnd = getAttachmentPreviewXCenter(index, total) + width / 2;
        final int yCenter = mCoordinates.attachmentPreviewsY
                + mCoordinates.attachmentPreviewsHeight / 2;
        final int y = yCenter - height / 2;
        final int yEnd = yCenter + height / 2;

        invalidate(x, y, xEnd, yEnd);
    }

    private int getAttachmentPreviewX(final int index, final int total) {
        if (mCoordinates == null || total == 0) {
            return -1;
        }
        final int sectionWidth = mCoordinates.attachmentPreviewsWidth / total;
        final int sectionOffset = index * sectionWidth;
        return mCoordinates.attachmentPreviewsX + sectionOffset;
    }

    private int getAttachmentPreviewXCenter(final int index, final int total) {
        if (total == 0) {
            return -1;
        }
        final int sectionWidth = mCoordinates.attachmentPreviewsWidth / total;
        return getAttachmentPreviewX(index, total) + sectionWidth / 2;
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
        final int background;
        if (isUnread) {
            background = R.drawable.conversation_unread_selector;
        } else {
            background = R.drawable.conversation_read_selector;
        }
        setBackgroundResource(background);
    }

    /**
     * Toggle the check mark on this view and update the conversation or begin
     * drag, if drag is enabled.
     */
    @Override
    public void toggleSelectedStateOrBeginDrag() {
        ViewMode mode = mActivity.getViewMode();
        if (mIsExpansiveTablet && mode.isListMode()) {
            beginDragMode();
        } else {
            toggleSelectedState();
        }
    }

    @Override
    public void toggleSelectedState() {
        if (mHeader != null && mHeader.conversation != null) {
            mSelected = !mSelected;
            setSelected(mSelected);
            Conversation conv = mHeader.conversation;
            // Set the list position of this item in the conversation
            SwipeableListView listView = getListView();
            conv.position = mSelected && listView != null ? listView.getPositionForView(this)
                    : Conversation.NO_POSITION;
            if (mSelectedConversationSet != null) {
                mSelectedConversationSet.toggle(conv);
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

    public void viewAttachmentPreview(final int index) {
        final String attachmentUri = mHeader.conversation.getAttachmentPreviewUris().get(index);
        final Uri imageListUri = mHeader.conversation.attachmentPreviewsListUri;
        LogUtils.d(LOG_TAG,
                "ConversationItemView: tapped on attachment preview %d, "
                        + "opening photoviewer for image list uri %s",
                index, imageListUri);
        MailPhotoViewActivity
                .startMailPhotoViewActivity(mActivity.getActivityContext(), imageListUri,
                        attachmentUri);
    }

    private boolean isTouchInContactPhoto(float x, float y) {
        // Everything before the right edge of contact photo
        return mHeader.gadgetMode == ConversationItemViewCoordinates.GADGET_CONTACT_PHOTO
                && x < mCoordinates.contactImagesX + mCoordinates.contactImagesWidth
                        + sSenderImageTouchSlop
                && (!isAttachmentPreviewsEnabled() || y < mCoordinates.attachmentPreviewsY);
    }

    private boolean isTouchInStar(float x, float y) {
        // Everything after the star and include a touch slop.
        return mStarEnabled
                && x > mCoordinates.starX - sStarTouchSlop
                && (!isAttachmentPreviewsEnabled() || y < mCoordinates.attachmentPreviewsY);
    }

    /**
     * If the touch is in the attachment previews, return the index of the attachment under that
     * point (for multiple previews). Return -1 if the touch is outside of the previews.
     *
     * @return The index corresponding to where the attachment appears on the screen. This index
     * may not be the same as the attachment's actual index in the message.
     */
    private int getAttachmentPreviewsIndexForTouch(float x, float y) {
        if (!isAttachmentPreviewsEnabled()) {
            return -1;
        }
        if (y > mCoordinates.attachmentPreviewsY
                && y < mCoordinates.attachmentPreviewsY + mCoordinates.attachmentPreviewsHeight
                && x > mCoordinates.attachmentPreviewsX
                && x < mCoordinates.attachmentPreviewsX + mCoordinates.attachmentPreviewsWidth) {
            final int total = mHeader.conversation.getAttachmentPreviewUris().size();
            if (mCoordinates.attachmentPreviewsWidth == 0 || total == 0) {
                return -1;
            }
            final int eachWidth = mCoordinates.attachmentPreviewsWidth / total;
            final int offset = (int) (x - mCoordinates.attachmentPreviewsX);
            return offset / eachWidth;
        }
        return -1;
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
        Utils.traceBeginSection("on touch event no swipe");
        boolean handled = false;

        int x = (int) event.getX();
        int y = (int) event.getY();
        mLastTouchX = x;
        mLastTouchY = y;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isTouchInContactPhoto(x, y) || isTouchInStar(x, y)
                        || getAttachmentPreviewsIndexForTouch(x, y) > -1) {
                    mDownEvent = true;
                    handled = true;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                mDownEvent = false;
                break;

            case MotionEvent.ACTION_UP:
                if (mDownEvent) {
                    int index;
                    if (isTouchInContactPhoto(x, y)) {
                        // Touch on the check mark
                        toggleSelectedState();
                    } else if (isTouchInStar(x, y)) {
                        // Touch on the star
                        toggleStar();
                    } else if ((index = getAttachmentPreviewsIndexForTouch(x, y)) > -1) {
                        // Touch on an attachment preview
                        viewAttachmentPreview(index);
                    }
                    handled = true;
                }
                break;
        }

        if (!handled) {
            handled = super.onTouchEvent(event);
        }

        Utils.traceEndSection();
        return handled;
    }

    /**
     * ConversationItemView is given the first chance to handle touch events.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Utils.traceBeginSection("on touch event");
        int x = (int) event.getX();
        int y = (int) event.getY();
        mLastTouchX = x;
        mLastTouchY = y;
        if (!mSwipeEnabled) {
            Utils.traceEndSection();
            return onTouchEventNoSwipe(event);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isTouchInContactPhoto(x, y) || isTouchInStar(x, y)
                        || getAttachmentPreviewsIndexForTouch(x, y) > -1) {
                    mDownEvent = true;
                    Utils.traceEndSection();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mDownEvent) {
                    int index;
                    if (isTouchInContactPhoto(x, y)) {
                        // Touch on the check mark
                        Utils.traceEndSection();
                        mDownEvent = false;
                        toggleSelectedState();
                        Utils.traceEndSection();
                        return true;
                    } else if (isTouchInStar(x, y)) {
                        // Touch on the star
                        mDownEvent = false;
                        toggleStar();
                        Utils.traceEndSection();
                        return true;
                    } else if ((index = getAttachmentPreviewsIndexForTouch(x, y)) > -1) {
                        // Touch on an attachment preview
                        mDownEvent = false;
                        viewAttachmentPreview(index);
                        Utils.traceEndSection();
                        return true;
                    }
                }
                break;
        }
        // Let View try to handle it as well.
        boolean handled = super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            Utils.traceEndSection();
            return true;
        }
        Utils.traceEndSection();
        return handled;
    }

    @Override
    public boolean performClick() {
        final boolean handled = super.performClick();
        final SwipeableListView list = getListView();
        if (list != null && list.getAdapter() != null) {
            final int pos = list.findConversation(this, mHeader.conversation);
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
        Utils.traceBeginSection("reset");
        setAlpha(1f);
        setTranslationX(0f);
        mAnimatedHeightFraction = 1.0f;
        if (mProgressAnimator.isStarted()) {
            removeCallbacks(mCancelProgressAnimatorRunnable);
            postDelayed(mCancelProgressAnimatorRunnable, sFadeAnimationDuration);
        }
        Utils.traceEndSection();
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

    private ObjectAnimator createProgressAnimator() {
        final ObjectAnimator animator = ObjectAnimator
                .ofFloat(this, "animatedProgressFraction", 0.0f, 1.0f).setDuration(
                        sProgressAnimationDuration);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.RESTART);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                invalidateAll();
            }

            @Override
            public void onAnimationCancel(final Animator animation) {
                invalidateAll();
                mProgressAnimatorCancelledTime = SystemClock.uptimeMillis();
            }

            private void invalidateAll() {
                invalidatePlaceholdersAndProgressBars();
            }
        });
        return animator;
    }

    private ObjectAnimator createFadeAnimator(final int index) {
        final ObjectAnimator animator = ObjectAnimator
                .ofFloat(this, "animatedFadeFraction" + index, 0.0f, 1.0f).setDuration(
                        sFadeAnimationDuration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.setRepeatCount(0);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                invalidateAttachmentPreview(index, mImageLoadStatuses.length);
            }

            @Override
            public void onAnimationCancel(final Animator animation) {
                invalidateAttachmentPreview(index, mImageLoadStatuses.length);
            }
        });
        return animator;
    }

    // Used by animator
    public void setAnimatedProgressFraction(final float fraction) {
        // ObjectAnimator.cancel() sets the field to 0.0f.
        if (fraction == 0.0f) {
            return;
        }
        mAnimatedProgressFraction = fraction;
        invalidatePlaceholdersAndProgressBars();
    }

    // Used by animator
    public void setAnimatedFadeFraction0(final float fraction) {
        mAnimatedFadeFraction0 = fraction;
        invalidateAttachmentPreview(0, mImageLoadStatuses.length);
    }

    // Used by animator
    public void setAnimatedFadeFraction1(final float fraction) {
        mAnimatedFadeFraction1 = fraction;
        invalidateAttachmentPreview(1, mImageLoadStatuses.length);
    }

    @Override
    public SwipeableView getSwipeableView() {
        return SwipeableView.from(this);
    }

    /**
     * Begin drag mode. Keep the conversation selected (NOT toggle selection) and start drag.
     */
    private void beginDragMode() {
        if (mLastTouchX < 0 || mLastTouchY < 0) {
            return;
        }
        // If this is already checked, don't bother unchecking it!
        if (!mSelected) {
            toggleSelectedState();
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
