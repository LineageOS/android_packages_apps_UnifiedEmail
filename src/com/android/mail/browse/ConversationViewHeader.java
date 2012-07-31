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
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.FolderSpan.FolderSpanDimensions;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.ui.FolderDisplayer;

/**
 * A view for the subject and folders in the conversation view. This container
 * makes an attempt to combine subject and folders on the same horizontal line if
 * there is enough room to fit both without wrapping. If they overlap, it
 * adjusts the layout to position the folders below the subject.
 */
public class ConversationViewHeader extends RelativeLayout implements OnClickListener {

    public interface ConversationViewHeaderCallbacks {
        /**
         * Called in response to a click on the folders region.
         */
        void onFoldersClicked();

        /**
         * Called when the height of the {@link ConversationViewHeader} changes.
         *
         * @param newHeight the new height in px
         */
        void onConversationViewHeaderHeightChange(int newHeight);

        /**
         * Measure a subject string for display outside a conversation view and
         * return the substring of trailing characters that didn't fit. Should
         * not actually render the text, just measure it.
         *
         * @param subject string to measure
         * @return the remainder of text that didn't fit
         */
        String getSubjectRemainder(String subject);
    }

    private String mSubject;
    private TextView mSubjectView;
    private FolderSpanTextView mFoldersView;
    private ConversationViewHeaderCallbacks mCallbacks;
    private ConversationFolderDisplayer mFolderDisplayer;

    private boolean mSizeChanged;

    public ConversationViewHeader(Context context) {
        this(context, null);
    }

    public ConversationViewHeader(Context context, AttributeSet attrs) {
        super(context, attrs);

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSubjectView = (TextView) findViewById(R.id.subject);
        mFoldersView = (FolderSpanTextView) findViewById(R.id.folders);

        mFoldersView.setOnClickListener(this);
        mFolderDisplayer = new ConversationFolderDisplayer(getContext(), mFoldersView);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // reposition the folders if they don't fit horizontally next to the
        // subject
        // (taking into account child margins and parent padding)
        final int childWidthSum = getTotalMeasuredChildWidth(mSubjectView)
                + getTotalMeasuredChildWidth(mFoldersView) + getPaddingLeft() + getPaddingRight();

        if (childWidthSum > getMeasuredWidth()) {
            LayoutParams params = (LayoutParams) mFoldersView.getLayoutParams();
            params.addRule(RelativeLayout.BELOW, R.id.subject);
            params.addRule(RelativeLayout.ALIGN_BASELINE, 0);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private static int getTotalMeasuredChildWidth(View child) {
        LayoutParams p = (LayoutParams) child.getLayoutParams();
        return child.getMeasuredWidth() + p.leftMargin + p.rightMargin;
    }

    public void setCallbacks(ConversationViewHeaderCallbacks callbacks) {
        mCallbacks = callbacks;
    }

    public void setSubject(final String subject, boolean notify) {
        mSubject = subject;
        String subjectToShow = subject;
        if (mCallbacks != null && mCallbacks.getSubjectRemainder(subject) == null) {
            subjectToShow = null;
        }
        mSubjectView.setText(subjectToShow);

        if (TextUtils.isEmpty(subjectToShow)) {
            mSubjectView.setVisibility(GONE);
        }

        if (notify) {
            handleSizeChanged();
        }
    }

    public String getSubject() {
        return mSubject;
    }

    public void setFolders(Conversation conv, boolean notify) {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        // TODO: read 'show priority arrows' pref from settings
        final boolean importanceArrowsEnabled = true;
        if (importanceArrowsEnabled && conv.isImportant()) {
            sb.append('.');
            sb.setSpan(new PriorityIndicatorSpan(getContext(),
                    R.drawable.ic_email_caret_none_important_unread, mFoldersView.getPadding(), 0),
                    0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }

<<<<<<< HEAD
        mFolderDisplayer.loadConversationFolders(conv.rawFolders, null /* ignoreFolder */);
||||||| merged common ancestors
        mFolderDisplayer.loadConversationFolders(conv.getRawFolders(), null /* ignoreFolder */);
=======
        mFolderDisplayer.loadConversationFolders(conv, null /* ignoreFolder */);
>>>>>>> abb78177
        mFolderDisplayer.appendFolderSpans(sb);

        mFoldersView.setText(sb);

        if (notify) {
            handleSizeChanged();
        }
    }

    @Override
    public void onClick(View v) {
        if (R.id.folders == v.getId()) {
            if (mCallbacks != null) {
                mCallbacks.onFoldersClicked();
            }
        }
    }

    private void handleSizeChanged() {
        mSizeChanged = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (mSizeChanged) {
            // propagate new size to webview conversation header spacer
            // only do this for known size changes
            if (mCallbacks != null) {
                mCallbacks.onConversationViewHeaderHeightChange(h);
            }

            mSizeChanged = false;
        }
    }

    private static class ConversationFolderDisplayer extends FolderDisplayer {

        private FolderSpanDimensions mDims;

        public ConversationFolderDisplayer(Context context, FolderSpanDimensions dims) {
            super(context);
            mDims = dims;
        }

        public void appendFolderSpans(SpannableStringBuilder sb) {
            for (Folder f : mFoldersSortedSet) {
                addSpan(sb, f);
            }

            if (mFoldersSortedSet.isEmpty()) {
                Folder addLabel = new Folder();
                final Resources r = mContext.getResources();
                addLabel.name = r.getString(R.string.add_label);
                addLabel.bgColor = ""
                        + r.getColor(R.color.conv_header_add_label_background);
                addLabel.fgColor = "" + r.getColor(R.color.conv_header_add_label_text);
                addSpan(sb, addLabel);
            }
        }

        private void addSpan(SpannableStringBuilder sb, Folder folder) {
            final int start = sb.length();
            sb.append(folder.name);
            final int end = sb.length();

            final int fgColor = folder.getForegroundColor(mDefaultFgColor);
            final int bgColor = folder.getBackgroundColor(mDefaultBgColor);

            sb.setSpan(new BackgroundColorSpan(bgColor), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(fgColor), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new FolderSpan(sb, mDims), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

    }

}
