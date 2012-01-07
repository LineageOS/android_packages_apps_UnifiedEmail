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
package com.android.mail.compose;

import com.android.mail.providers.Attachment;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;

/*
 * View for displaying attachments in the compose screen.
 */
class AttachmentsView extends LinearLayout {
    private ArrayList<Attachment> mAttachments;
    private AttachmentChangesListener mChangeListener;

    public AttachmentsView(Context context) {
        this(context, null);
    }

    public AttachmentsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAttachments = Lists.newArrayList();
    }

    /**
     * Set a listener for changes to the attachments.
     * @param listener
     */
    public void setAttachmentChangesListener(AttachmentChangesListener listener) {
        mChangeListener = listener;
    }

    /**
     * Add an attachment and update the ui accordingly.
     * @param attachment
     */
    public void addAttachment(final Attachment attachment) {
        if (!isShown()) {
            setVisibility(View.VISIBLE);
        }
        mAttachments.add(attachment);

        final AttachmentComposeView attachmentView =
            new AttachmentComposeView(getContext(), attachment);

        attachmentView.addDeleteListener(new OnClickListener() {
            public void onClick(View v) {
                deleteAttachment(attachmentView, attachment);
            }
        });


        addView(attachmentView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        if (mChangeListener != null) {
            mChangeListener.onAttachmentAdded();
        }
    }

    @VisibleForTesting
    protected void deleteAttachment(final AttachmentComposeView attachmentView,
            final Attachment attachment) {
        mAttachments.remove(attachment);
        removeView(attachmentView);
        if (mChangeListener != null) {
            mChangeListener.onAttachmentDeleted();
        }
        if (mAttachments.size() == 0) {
            setVisibility(View.GONE);
        }
    }

    /**
     * Get all attachments being managed by this view.
     * @return attachments.
     */
    public ArrayList<Attachment> getAttachments() {
        return mAttachments;
    }

    /**
     * Delete all attachments being managed by this view.
     */
    public void deleteAllAttachments() {
        mAttachments.clear();
        removeAllViews();
    }

    /**
     * See if all the attachments in this view are synced.
     */
    public boolean areAttachmentsSynced() {
        for (Attachment a : mAttachments) {
            if (a.isSynced()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the total size of all attachments currently in this view.
     */
    public int getTotalAttachmentsSize() {
        int totalSize = 0;
        for (Attachment attachment : mAttachments) {
            totalSize += attachment.getSize();
        }
        return totalSize;
    }

    /**
     * Interface to implement to be notified about changes to the attachments.
     * @author mindyp@google.com
     *
     */
    public interface AttachmentChangesListener {
        public void onAttachmentDeleted();
        public void onAttachmentAdded();
    }
}
