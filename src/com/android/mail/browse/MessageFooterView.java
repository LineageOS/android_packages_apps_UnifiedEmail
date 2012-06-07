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

import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.AttachmentLoader.AttachmentCursor;
import com.android.mail.browse.ConversationContainer.DetachListener;
import com.android.mail.browse.ConversationViewAdapter.MessageHeaderItem;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.Message;
import com.android.mail.utils.LogUtils;
import com.google.common.collect.Lists;

import java.util.List;

public class MessageFooterView extends LinearLayout implements DetachListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    private MessageHeaderItem mMessageHeaderItem;
    private LoaderManager mLoaderManager;
    private AttachmentCursor mAttachmentsCursor;
    private TextView mTitleText;
    private View mTitleBar;
    private AttachmentTileGrid mAttachmentGrid;

    /**
     * An easy way for the conversation view to disable immediately kicking off attachment loaders
     * when measuring overlays during the initial render.
     */
    private static boolean sEnableAttachmentLoaders = true;

    private static final String LOG_TAG = new LogUtils().getLogTag();

    public MessageFooterView(Context context) {
        this(context, null);
    }

    public MessageFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Utility method that can be used to temporarily disable attachment loaders in all
     * {@link MessageFooterView}s.
     *
     * @param enabled true to enable loaders
     */
    public static void enableAttachmentLoaders(boolean enabled) {
        sEnableAttachmentLoaders = enabled;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTitleText = (TextView) findViewById(R.id.attachments_header_text);
        mTitleBar = findViewById(R.id.attachments_header_bar);
        mAttachmentGrid = (AttachmentTileGrid) findViewById(R.id.attachment_tile_grid);
    }

    public void initialize(LoaderManager loaderManager) {
        mLoaderManager = loaderManager;
    }

    public void bind(MessageHeaderItem headerItem) {
        mMessageHeaderItem = headerItem;

        /*
         * Assuming ConversationContainer does not requesting adapter views for zero-height items,
         * we should just always render even if the matching header is collapsed.
         */

        mAttachmentGrid.removeAllViewsInLayout();
        mTitleText.setVisibility(View.GONE);
        mTitleBar.setVisibility(View.GONE);

        // kick off load of Attachment objects in background thread
        final Integer attachmentLoaderId = getAttachmentLoaderId();
        if (sEnableAttachmentLoaders && attachmentLoaderId != null) {
            LogUtils.i(LOG_TAG, "binding footer view, calling initLoader for message %d",
                    attachmentLoaderId);
            mLoaderManager.initLoader(attachmentLoaderId, Bundle.EMPTY, this);
        }

        // Do an initial render if initLoader didn't already do one
        if (mAttachmentGrid.getChildCount() == 0) {
            renderAttachments();
        }
        setVisibility(mMessageHeaderItem.isExpanded() ? VISIBLE : GONE);
    }

    private void unbind() {
        final Integer loaderId = getAttachmentLoaderId();
        if (mLoaderManager != null && loaderId != null) {
            LogUtils.i(LOG_TAG, "detaching footer view, calling destroyLoader for message %d",
                    loaderId);
            mLoaderManager.destroyLoader(loaderId);
        }
    }

    private void renderAttachments() {
        final List<Attachment> attachments;
        if (mAttachmentsCursor != null && !mAttachmentsCursor.isClosed()) {
            int i = -1;
            attachments = Lists.newArrayList();
            while (mAttachmentsCursor.moveToPosition(++i)) {
                attachments.add(mAttachmentsCursor.get());
            }
        } else {
            // before the attachment loader results are in, we can still render immediately using
            // the basic info in the message's attachmentsJSON
            attachments = mMessageHeaderItem.message.getAttachments();
        }
        renderAttachments(attachments);
    }

    private void renderAttachments(List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        mTitleText.setVisibility(View.VISIBLE);
        mTitleBar.setVisibility(View.VISIBLE);
        mAttachmentGrid.setVisibility(View.VISIBLE);

        // Setup the tiles.
        mAttachmentGrid.configureGrid(mMessageHeaderItem.message.attachmentListUri, attachments);
    }

    private Integer getAttachmentLoaderId() {
        Integer id = null;
        final Message msg = mMessageHeaderItem == null ? null : mMessageHeaderItem.message;
        if (msg != null && msg.hasAttachments && msg.attachmentListUri != null) {
            id = msg.attachmentListUri.hashCode();
        }
        return id;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unbind();
    }

    @Override
    public void onDetachedFromParent() {
        unbind();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new AttachmentLoader(getContext(), mMessageHeaderItem.message.attachmentListUri);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAttachmentsCursor = (AttachmentCursor) data;

        if (mAttachmentsCursor == null || mAttachmentsCursor.isClosed()) {
            return;
        }

        renderAttachments();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAttachmentsCursor = null;
    }
}
