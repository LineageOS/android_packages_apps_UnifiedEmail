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

import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.Message;
import com.android.mail.ui.AttachmentTile;
import com.android.mail.ui.AttachmentTileGrid;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

/*
 * View for displaying attachments in the compose screen.
 */
class AttachmentsView extends LinearLayout implements OnClickListener, TransitionListener {
    private static final String LOG_TAG = LogTag.getLogTag();

    private final Resources mResources;

    private ArrayList<Attachment> mAttachments;
    private AttachmentDeletedListener mChangeListener;
    private AttachmentTileGrid mTileGrid;
    private LinearLayout mAttachmentLayout;
    private GridLayout mCollapseLayout;
    private TextView mCollapseText;
    private ImageView mCollapseCaret;
    private LayoutTransition mComposeLayoutTransition;

    private boolean mIsExpanded;
    private long mChangingDelay;

    public AttachmentsView(Context context) {
        this(context, null);
    }

    public AttachmentsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAttachments = Lists.newArrayList();
        mResources = context.getResources();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTileGrid = (AttachmentTileGrid) findViewById(R.id.attachment_tile_grid);
        mAttachmentLayout = (LinearLayout) findViewById(R.id.attachment_bar_list);
        mCollapseLayout = (GridLayout) findViewById(R.id.attachment_collapse_view);
        mCollapseText = (TextView) findViewById(R.id.attachment_collapse_text);
        mCollapseCaret = (ImageView) findViewById(R.id.attachment_collapse_caret);

        mCollapseLayout.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.attachment_collapse_view:
                if (mIsExpanded) {
                    collapseView();
                    mComposeLayoutTransition.setStartDelay(
                            LayoutTransition.CHANGING, mChangingDelay);
                } else {
                    expandView();
                    mComposeLayoutTransition.setStartDelay(LayoutTransition.CHANGING, 0l);
                }
                mComposeLayoutTransition.enableTransitionType(LayoutTransition.CHANGING);
                break;
        }
    }

    public void expandView() {
        mTileGrid.setVisibility(VISIBLE);
        mAttachmentLayout.setVisibility(VISIBLE);
        setupCollapsibleView(false);
        mIsExpanded = true;

        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    public void collapseView() {
        mTileGrid.setVisibility(GONE);
        mAttachmentLayout.setVisibility(GONE);

        // If there are some attachments, show the preview
        if (!mAttachments.isEmpty()) {
            setupCollapsibleView(true);
            mCollapseLayout.setVisibility(VISIBLE);
        }

        mIsExpanded = false;
    }

    private void setupCollapsibleView(boolean isCollapsed) {
        // setup text
        final int numAttachments = mAttachments.size();
        final String attachmentText = mResources.getQuantityString(
                R.plurals.number_of_attachments, numAttachments, numAttachments);
        mCollapseText.setText(attachmentText);

        if (isCollapsed) {
            mCollapseCaret.setImageResource(R.drawable.ic_menu_expander_minimized_holo_light);
        } else {
            mCollapseCaret.setImageResource(R.drawable.ic_menu_expander_maximized_holo_light);
        }
    }

    /**
     * Set a listener for changes to the attachments.
     * @param listener
     */
    public void setAttachmentChangesListener(AttachmentDeletedListener listener) {
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
        expandView();

        // If we have an attachment that should be shown in a tiled look,
        // set up the tile and add it to the tile grid.
        if (AttachmentTile.isTiledAttachment(attachment)) {
            final ComposeAttachmentTile attachmentTile =
                    mTileGrid.addComposeTileFromAttachment(attachment);
            attachmentTile.addDeleteListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteAttachment(attachmentTile, attachment);
                }
            });
        // Otherwise, use the old bar look and add it to the new
        // inner LinearLayout.
        } else {
            final AttachmentComposeView attachmentView =
                new AttachmentComposeView(getContext(), attachment);

            attachmentView.addDeleteListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteAttachment(attachmentView, attachment);
                }
            });


            mAttachmentLayout.addView(attachmentView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
        }
    }

    @VisibleForTesting
    protected void deleteAttachment(final View attachmentView,
            final Attachment attachment) {
        mComposeLayoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        mComposeLayoutTransition.setStartDelay(
                LayoutTransition.CHANGING, mChangingDelay);
        final LayoutTransition transition = getLayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        transition.setStartDelay(LayoutTransition.CHANGING, mChangingDelay);
        transition.addTransitionListener(this);

        mAttachments.remove(attachment);
        ((ViewGroup) attachmentView.getParent()).removeView(attachmentView);
        if (mChangeListener != null) {
            mChangeListener.onAttachmentDeleted();
        }
        if (mAttachments.size() == 0) {
            setVisibility(View.GONE);
            collapseView();
        } else {
            setupCollapsibleView(true);
        }
    }

    public void setComposeLayoutTransition(LayoutTransition transition) {
        mComposeLayoutTransition = transition;
        mComposeLayoutTransition.addTransitionListener(this);
        mChangingDelay =
                mComposeLayoutTransition.getDuration(LayoutTransition.DISAPPEARING);
    }

    @Override
    public void startTransition(LayoutTransition transition, ViewGroup container, View view,
            int transitionType) {
        /* Do nothing */
    }

    @Override
    public void endTransition(LayoutTransition transition, ViewGroup container, View view,
            int transitionType) {
        transition.disableTransitionType(LayoutTransition.CHANGING);
        transition.setStartDelay(LayoutTransition.CHANGING, 0l);
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
        mTileGrid.removeAllViews();
        mAttachmentLayout.removeAllViews();
        setVisibility(GONE);
        collapseView();
    }

    /**
     * Get the total size of all attachments currently in this view.
     */
    public long getTotalAttachmentsSize() {
        long totalSize = 0;
        for (Attachment attachment : mAttachments) {
            totalSize += attachment.size;
        }
        return totalSize;
    }

    /**
     * Interface to implement to be notified about changes to the attachments.
     *
     */
    public interface AttachmentDeletedListener {
        public void onAttachmentDeleted();
    }

    /**
     * Generate an {@link Attachment} object for a given local content URI. Attempts to populate
     * the {@link Attachment#name}, {@link Attachment#size}, and {@link Attachment#contentType}
     * fields using a {@link ContentResolver}.
     *
     * @param contentUri
     * @return an Attachment object
     * @throws AttachmentFailureException
     */
    public Attachment generateLocalAttachment(Uri contentUri) throws AttachmentFailureException {
        // FIXME: do not query resolver for type on the UI thread
        final ContentResolver contentResolver = getContext().getContentResolver();
        String contentType = contentResolver.getType(contentUri);
        if (contentUri == null || TextUtils.isEmpty(contentUri.getPath())) {
            throw new AttachmentFailureException("Failed to create local attachment");
        }

        if (contentType == null) contentType = "";

        final Attachment attachment = new Attachment();
        attachment.uri = null; // URI will be assigned by the provider upon send/save
        attachment.name = null;
        attachment.contentType = contentType;
        attachment.size = 0;
        attachment.contentUri = contentUri;

        Cursor metadataCursor = null;
        try {
            metadataCursor = contentResolver.query(
                    contentUri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                    null, null, null);
            if (metadataCursor != null) {
                try {
                    if (metadataCursor.moveToNext()) {
                        attachment.name = metadataCursor.getString(0);
                        attachment.size = metadataCursor.getInt(1);
                    }
                } finally {
                    metadataCursor.close();
                }
            }
        } catch (SQLiteException ex) {
            // One of the two columns is probably missing, let's make one more attempt to get at
            // least one.
            // Note that the documentations in Intent#ACTION_OPENABLE and
            // OpenableColumns seem to contradict each other about whether these columns are
            // required, but it doesn't hurt to fail properly.

            // Let's try to get DISPLAY_NAME
            try {
                metadataCursor = getOptionalColumn(contentResolver, contentUri,
                        OpenableColumns.DISPLAY_NAME);
                if (metadataCursor != null && metadataCursor.moveToNext()) {
                    attachment.name = metadataCursor.getString(0);
                }
            } finally {
                if (metadataCursor != null) metadataCursor.close();
            }

            // Let's try to get SIZE
            try {
                metadataCursor =
                        getOptionalColumn(contentResolver, contentUri, OpenableColumns.SIZE);
                if (metadataCursor != null && metadataCursor.moveToNext()) {
                    attachment.size = metadataCursor.getInt(0);
                } else {
                    // Unable to get the size from the metadata cursor. Open the file and seek.
                    attachment.size = getSizeFromFile(contentUri, contentResolver);
                }
            } finally {
                if (metadataCursor != null) metadataCursor.close();
            }
        } catch (SecurityException e) {
            throw new AttachmentFailureException("Security Exception from attachment uri", e);
        }

        if (attachment.name == null) {
            attachment.name = contentUri.getLastPathSegment();
        }

        return attachment;
    }

    /**
     * Adds a local attachment by file path.
     * @param account
     * @param contentUri the uri of the local file path
     *
     * @return size of the attachment added.
     * @throws AttachmentFailureException if an error occurs adding the attachment.
     */
    public long addAttachment(Account account, Uri contentUri)
            throws AttachmentFailureException {
        return addAttachment(account, generateLocalAttachment(contentUri));
    }

    /**
     * Adds an attachment of either local or remote origin, checking to see if the attachment
     * exceeds file size limits.
     * @param account
     * @param attachment the attachment to be added.
     *
     * @return size of the attachment added.
     * @throws AttachmentFailureException if an error occurs adding the attachment.
     */
    public long addAttachment(Account account, Attachment attachment)
            throws AttachmentFailureException {
        int maxSize = account.settings.getMaxAttachmentSize();

        // Error getting the size or the size was too big.
        if (attachment.size == -1 || attachment.size > maxSize) {
            throw new AttachmentFailureException("Attachment too large to attach");
        } else if ((getTotalAttachmentsSize()
                + attachment.size) > maxSize) {
            throw new AttachmentFailureException("Attachment too large to attach");
        } else {
            addAttachment(attachment);
        }

        return attachment.size;
    }


    public void addAttachments(Account account, Message refMessage)
            throws AttachmentFailureException {
        if (refMessage.hasAttachments) {
            for (Attachment a : refMessage.getAttachments()) {
                addAttachment(account, a);
            }
        }
    }

    @VisibleForTesting
    protected int getSizeFromFile(Uri uri, ContentResolver contentResolver) {
        int size = -1;
        ParcelFileDescriptor file = null;
        try {
            file = contentResolver.openFileDescriptor(uri, "r");
            size = (int) file.getStatSize();
        } catch (FileNotFoundException e) {
            LogUtils.w(LOG_TAG, "Error opening file to obtain size.");
        } finally {
            try {
                if (file != null) {
                    file.close();
                }
            } catch (IOException e) {
                LogUtils.w(LOG_TAG, "Error closing file opened to obtain size.");
            }
        }
        return size;
    }

    /**
     * @return a cursor to the requested column or null if an exception occurs while trying
     * to query it.
     */
    private Cursor getOptionalColumn(ContentResolver contentResolver, Uri uri, String columnName) {
        Cursor result = null;
        try {
            result = contentResolver.query(uri, new String[]{columnName}, null, null, null);
        } catch (SQLiteException ex) {
            // ignore, leave result null
        }
        return result;
    }

    /**
     * Class containing information about failures when adding attachments.
     */
    static class AttachmentFailureException extends Exception {
        private static final long serialVersionUID = 1L;

        public AttachmentFailureException(String error) {
            super(error);
        }
        public AttachmentFailureException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }
}
