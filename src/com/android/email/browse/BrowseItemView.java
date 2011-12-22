/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.email.browse;

import com.android.email.browse.BrowseItemViewModel.SenderFragment;
import com.android.email.perf.Timer;
import com.android.email.providers.UIProvider;
import com.android.email.R;
import com.android.email.ViewMode;
import com.android.email.utils.Utils;
import com.google.common.annotations.VisibleForTesting;

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
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import java.util.Map;

public class BrowseItemView extends View {
    // Timer.
    private static int sLayoutCount = 0;
    private static Timer sTimer; // Create the sTimer here if you need to do perf analysis.
    private static final int PERF_LAYOUT_ITERATIONS = 50;
    private static final String PERF_TAG_LAYOUT = "CCHV.layout";
    private static final String PERF_TAG_CALCULATE_TEXTS_BITMAPS = "CCHV.txtsbmps";
    private static final String PERF_TAG_CALCULATE_SENDER_SUBJECT = "CCHV.sendersubj";
    private static final String PERF_TAG_CALCULATE_LABELS = "CCHV.labels";
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
    private static CharacterStyle sLightTextStyle;
    private static CharacterStyle sNormalTextStyle;

    // Static paints.
    private static TextPaint sPaint = new TextPaint();
    private static TextPaint sLabelsPaint = new TextPaint();

    // Backgrounds for different states.
    private final SparseArray<Drawable> mBackgrounds = new SparseArray<Drawable>();

    // Dimensions and coordinates.
    private int mViewWidth = -1;
    private int mMode = -1;
    private int mDateX;
    private int mPaperclipX;
    private int mLabelsXEnd;
    private int mSendersWidth;

    /** Whether we're running under test mode. */
    private boolean mTesting = false;

    @VisibleForTesting
    BrowseItemViewCoordinates mCoordinates;

    private final Context mContext;

    private String mAccount;
    private BrowseItemViewModel mHeader;
    private ViewMode mViewMode;
    private boolean mDownEvent;
    private boolean mChecked;
    private static int sFadedColor = -1;
    private static int sFadedActivatedColor = -1;

    static {
        sPaint.setAntiAlias(true);
        sLabelsPaint.setAntiAlias(true);
    }

    /**
     * This handler will be called when user toggle a star in a conversation
     * header view. It can be used to update the state of other views to ensure
     * UI consistency.
     */
    public static interface StarHandler {
        public void toggleStar(boolean toggleOn, long conversationId, long maxMessageId);
    }

    public BrowseItemView(Context context, String account) {
        super(context);
        mContext = context.getApplicationContext();
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
            DATE_BACKGROUND = BitmapFactory.decodeResource(res, R.drawable.label_bg_holo_light);

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

            // Initialize static color.
            sNormalTextStyle = new StyleSpan(Typeface.NORMAL);
            sLightTextStyle = new ForegroundColorSpan(LIGHT_TEXT_COLOR);
        }
    }

    /**
     * Bind this view to the content of the cursor and request layout.
     */
    public void bind(BrowseItemViewModel model, StarHandler starHandler, String account,
            CharSequence displayedLabel, ViewMode viewMode) {
        mAccount = account;
        mViewMode = viewMode;
        mHeader = model;
        setContentDescription(mHeader.getContentDescription(mContext));
        requestLayout();
    }

    public void bind(Cursor cursor, StarHandler starHandler, String account,
            CharSequence displayedLabel, ViewMode viewMode) {
        mAccount = account;
        mViewMode = viewMode;
        mHeader = BrowseItemViewModel.forCursor(account, cursor);
        setContentDescription(mHeader.getContentDescription(mContext));
        requestLayout();
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
                mMode = BrowseItemViewCoordinates.getMode(mContext, mViewMode);
            }
        }
        mHeader.viewWidth = mViewWidth;
        Resources res = getResources();
        mHeader.standardScaledDimen = res.getDimensionPixelOffset(R.dimen.standard_scaled_dimen);
        if (mHeader.standardScaledDimen != sStandardScaledDimen) {
            // Large Text has been toggle on/off. Update the static dimens.
            sStandardScaledDimen = mHeader.standardScaledDimen;
            BrowseItemViewCoordinates.refreshConversationHeights(mContext);
            sDateBackgroundHeight = res.getDimensionPixelSize(R.dimen.date_background_height);
        }
        mCoordinates = BrowseItemViewCoordinates.forWidth(mContext, mViewWidth, mMode,
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

        // Update font color.
        int fontColor = getFontColor(DEFAULT_TEXT_COLOR);
        boolean fontChanged = false;
        if (mHeader.fontColor != fontColor) {
            fontChanged = true;
            mHeader.fontColor = fontColor;
        }

        boolean isUnread = true;

        final boolean checkboxEnabled = true;
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

        // Initialize label displayer.
        startTimer(PERF_TAG_CALCULATE_LABELS);

        pauseTimer(PERF_TAG_CALCULATE_LABELS);

        // Star.
        mHeader.starBitmap = mHeader.starred ? STAR_ON : STAR_OFF;

        // Date.
        mHeader.dateText = DateUtils.getRelativeTimeSpanString(mContext, mHeader.dateMs).toString();

        // Paper clip icon.
        mHeader.paperclip = null;
        if (mHeader.hasAttachments) {
            mHeader.paperclip = ATTACHMENT;
        }

        // Personal level.
        mHeader.personalLevelBitmap = null;

        startTimer(PERF_TAG_CALCULATE_SENDER_SUBJECT);

        // Subject.
        createSubjectSpans(isUnread);

        // Parse senders fragments.
        parseSendersFragments(isUnread);

        pauseTimer(PERF_TAG_CALCULATE_SENDER_SUBJECT);
        pauseTimer(PERF_TAG_CALCULATE_TEXTS_BITMAPS);
    }

    private void createSubjectSpans(boolean isUnread) {
        String subject = filterTag(mHeader.subject);
        String snippet = mHeader.snippet;
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
        return isActivated() && mViewMode.isTwoPane() ? ACTIVATED_TEXT_COLOR
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
        if (TextUtils.isEmpty(mHeader.fromSnippetInstructions)) {
            return;
        }
        SpannableStringBuilder sendersBuilder = new SpannableStringBuilder();
        SpannableStringBuilder statusBuilder = new SpannableStringBuilder();
        Utils.getStyledSenderSnippet(mContext, mHeader.fromSnippetInstructions, sendersBuilder,
                statusBuilder, BrowseItemViewCoordinates.getSubjectLength(mContext, mMode,
                        false, mHeader.hasAttachments), false,
                        false, mHeader.hasDraftMessage);
        mHeader.sendersText = sendersBuilder.toString();

        CharacterStyle[] spans = sendersBuilder.getSpans(0, sendersBuilder.length(),
                CharacterStyle.class);
        mHeader.clearSenderFragments();
        int lastPosition = 0;
        CharacterStyle style = sNormalTextStyle;
        if (spans != null) {
            for (CharacterStyle span : spans) {
                style = span;
                int start = sendersBuilder.getSpanStart(style);
                int end = sendersBuilder.getSpanEnd(style);
                if (start > lastPosition) {
                    mHeader.addSenderFragment(lastPosition, start, sNormalTextStyle, false);
                }
                // From instructions won't be updated until the next sync. So we
                // have to override the text style here to be consistent with
                // the background color.
                if (isUnread) {
                    mHeader.addSenderFragment(start, end, style, false);
                } else {
                    mHeader.addSenderFragment(start, end, sNormalTextStyle, false);
                }
                lastPosition = end;
            }
        }
        if (lastPosition < sendersBuilder.length()) {
            style = sLightTextStyle;
            mHeader.addSenderFragment(lastPosition, sendersBuilder.length(), style, true);
        }
        if (statusBuilder.length() > 0) {
            if (mHeader.sendersText.length() > 0) {
                mHeader.sendersText = mHeader.sendersText.concat(", ");

                // Extend the last fragment to include the comma.
                int lastIndex = mHeader.senderFragments.size() - 1;
                int start = mHeader.senderFragments.get(lastIndex).start;
                int end = mHeader.senderFragments.get(lastIndex).end + 2;
                style = mHeader.senderFragments.get(lastIndex).style;

                // The new fragment is only fixed if the previous fragment
                // is fixed.
                boolean isFixed = mHeader.senderFragments.get(lastIndex).isFixed;

                // Remove the old fragment.
                mHeader.senderFragments.remove(lastIndex);

                // Add new fragment.
                mHeader.addSenderFragment(start, end, style, isFixed);
            }
            int pos = mHeader.sendersText.length();
            mHeader.sendersText = mHeader.sendersText.concat(statusBuilder.toString());
            mHeader.addSenderFragment(pos, mHeader.sendersText.length(), new ForegroundColorSpan(
                    DRAFT_TEXT_COLOR), true);
        }
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

        int cellWidth = mContext.getResources().getDimensionPixelSize(R.dimen.label_cell_width);

        if (BrowseItemViewCoordinates.displayLabelsAboveDate(mMode)) {
            mLabelsXEnd = mCoordinates.dateXEnd;
            mSendersWidth = mCoordinates.sendersWidth;
        } else {
            if (mHeader.paperclip != null) {
                mLabelsXEnd = mPaperclipX;
            } else {
                mLabelsXEnd = mDateX - cellWidth / 2;
            }
            mSendersWidth = mLabelsXEnd - mCoordinates.sendersX - 2 * cellWidth;
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
        if (!BrowseItemViewCoordinates.displaySendersInline(mMode)) {
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
        int width = measureWidth(widthMeasureSpec);
        int height = measureHeight(heightMeasureSpec,
                BrowseItemViewCoordinates.getMode(mContext, mViewMode));
        setMeasuredDimension(width, height);
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
            result = BrowseItemViewCoordinates.getHeight(mContext, mode);
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
        if (mHeader.personalLevelBitmap != null) {
            canvas.drawBitmap(mHeader.personalLevelBitmap, mCoordinates.personalLevelX,
                    mCoordinates.personalLevelY, sPaint);
        }

        // Senders.
        sPaint.setTextSize(mCoordinates.sendersFontSize);
        sPaint.setTypeface(Typeface.DEFAULT);
        boolean isUnread = true;
        int sendersColor = getFontColor(isUnread ? SENDERS_TEXT_COLOR_UNREAD
                : SENDERS_TEXT_COLOR_READ);
        sPaint.setColor(sendersColor);
        for (SenderFragment fragment : mHeader.senderFragments) {
            if (fragment.shouldDisplay) {
                sPaint.setTypeface(Typeface.DEFAULT);
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

        // Date background: shown when there is an attachment or a visible
        // label.
        if (!isActivated()
                && mHeader.hasAttachments
                && BrowseItemViewCoordinates.showAttachmentBackground(mMode)) {
            mHeader.dateBackground = DATE_BACKGROUND;
            int leftOffset = (mHeader.hasAttachments ? mPaperclipX : mDateX)
                    - DATE_BACKGROUND_PADDING_LEFT;
            int top = mCoordinates.labelsY;
            Rect src = new Rect(0, 0, mHeader.dateBackground.getWidth(), mHeader.dateBackground
                    .getHeight());
            Rect dst = new Rect(leftOffset, top, mViewWidth, top + sDateBackgroundHeight);
            canvas.drawBitmap(mHeader.dateBackground, src, dst, sPaint);
        } else {
            mHeader.dateBackground = null;
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
            if (mViewMode.isTwoPane() && mViewMode.isConversationListMode()) {
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
            if (mViewMode.isTwoPane() && mViewMode.isConversationListMode()) {
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
        if (isActivated() && mViewMode.isTwoPane()) {
            setBackgroundResource(R.drawable.list_arrow_selected_holo);
        } else {
            setBackgroundResource(R.drawable.list_selected_holo);
        }
    }

    /**
     * Toggle the check mark on this view and update the conversation
     */
    public void toggleCheckMark() {
        mChecked = !mChecked;
        // We update the background after the checked state has changed now that
        // we have a selected background asset. Setting the background usually
        // waits for a layout pass, but we don't need a full layout, just an
        // update to the background.
        requestLayout();
    }

    /**
     * Toggle the star on this view and update the conversation.
     */
    private void toggleStar() {
        mHeader.starred = !mHeader.starred;
        mHeader.starBitmap = mHeader.starred ? STAR_ON : STAR_OFF;
        postInvalidate(mCoordinates.starX, mCoordinates.starY, mCoordinates.starX
                + mHeader.starBitmap.getWidth(),
                mCoordinates.starY + mHeader.starBitmap.getHeight());
    }

    private boolean touchCheckmark(float x, float y) {
        // Everything before senders and include a touch slop.
        return mHeader.checkboxVisible && x < mCoordinates.sendersX + TOUCH_SLOP;
    }

    private boolean touchStar(float x, float y) {
        // Everything after the star and include a touch slop.
        return x > mCoordinates.starX - TOUCH_SLOP;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = false;

        int x = (int) event.getX();
        int y = (int) event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownEvent = true;
                if (touchCheckmark(x, y) || touchStar(x, y)) {
                    handled = true;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                mDownEvent = false;
                break;

            case MotionEvent.ACTION_UP:
                if (mDownEvent) {
                    if (touchCheckmark(x, y)) {
                        // Touch on the check mark
                        toggleCheckMark();
                    } else if (touchStar(x, y)) {
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
}
