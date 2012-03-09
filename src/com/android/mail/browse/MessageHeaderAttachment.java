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

import android.app.AlertDialog;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.AttachmentLoader.AttachmentCursor;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider.AttachmentColumns;
import com.android.mail.providers.UIProvider.AttachmentDestination;
import com.android.mail.providers.UIProvider.AttachmentState;
import com.android.mail.utils.AttachmentUtils;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MimeType;
import com.android.mail.utils.Utils;

/**
 * View for a single attachment in conversation view. Shows download status and allows launching
 * intents to act on an attachment.
 *
 */
public class MessageHeaderAttachment extends LinearLayout implements OnClickListener,
        OnMenuItemClickListener, DialogInterface.OnCancelListener,
        DialogInterface.OnDismissListener, LoaderManager.LoaderCallbacks<Cursor> {

    private LoaderManager mLoaderManager;
    private Attachment mAttachment;
    private ImageView mIcon;
    private TextView mTitle;
    private TextView mSubTitle;
    private String mAttachmentSizeText;
    private String mDisplayType;
    private ProgressDialog mViewProgressDialog;
    private AttachmentCommandHandler mCommandHandler;
    private ProgressBar mProgress;
    private Button mPreviewButton;
    private Button mViewButton;
    private Button mSaveButton;
    private Button mInfoButton;
    private Button mPlayButton;
    private Button mInstallButton;
    private Button mCancelButton;

    private static final String LOG_TAG = new LogUtils().getLogTag();

    private class AttachmentCommandHandler extends AsyncQueryHandler {

        public AttachmentCommandHandler() {
            super(getContext().getContentResolver());
        }

        /**
         * Asynchronously begin an update() on a ContentProvider and initialize a loader to watch
         * for resulting changes on this attachment.
         *
         */
        public void sendCommand(ContentValues params) {
            startUpdate(0, null, mAttachment.uri, params, null, null);
            mLoaderManager.initLoader(mAttachment.uri.hashCode(), Bundle.EMPTY,
                    MessageHeaderAttachment.this);
        }

    }

    public MessageHeaderAttachment(Context context) {
        super(context);
    }

    public MessageHeaderAttachment(Context context, AttributeSet attrs) {
        super(context, attrs);

        mCommandHandler = new AttachmentCommandHandler();
    }

    public static MessageHeaderAttachment inflate(LayoutInflater inflater, ViewGroup parent,
            LoaderManager loaderManager) {
        MessageHeaderAttachment view = (MessageHeaderAttachment) inflater.inflate(
                R.layout.conversation_message_attachment, parent, false);

        view.mLoaderManager = loaderManager;

        return view;
    }

    /**
     * Render most of the UI using given immutable attachment properties. This happens immediately
     * upon instantiation.
     *
     */
    public void render(Attachment attachment) {
        mAttachment = attachment;

        LogUtils.d(LOG_TAG, "got attachment list row: name=%s state/dest=%d/%d dled=%d" +
                " contentUri=%s MIME=%s", mAttachment.name, mAttachment.state,
                mAttachment.destination, mAttachment.downloadedSize, mAttachment.contentUri,
                mAttachment.mimeType);

        mTitle.setText(attachment.name);

        mAttachmentSizeText = AttachmentUtils.convertToHumanReadableSize(getContext(),
                attachment.size);
        mDisplayType = AttachmentUtils.getDisplayType(getContext(), attachment);
        updateSubtitleText(null);

        if (mAttachment.isImage() && mAttachment.thumbnailUri != null) {
            // FIXME: this decodes on the UI thread. Also, it doesn't handle large images, so
            // using the full image is out of the question.
            mIcon.setImageURI(mAttachment.thumbnailUri);
        }
        if (mIcon.getDrawable() == null) {
            // not an image, or image load failed. fall back to default.
            mIcon.setImageResource(R.drawable.ic_menu_attachment_holo_light);
            mIcon.setScaleType(ImageView.ScaleType.CENTER);
        }

        mProgress.setMax(attachment.size);

        updateActions();

        if (mAttachment.isDownloading()) {
            mLoaderManager.initLoader(mAttachment.uri.hashCode(), Bundle.EMPTY, this);
            // TODO: clean up loader when the view is detached
        }
    }

    private void updateStatus(Attachment newAttachment) {

        LogUtils.d(LOG_TAG, "got attachment update: name=%s state/dest=%d/%d dled=%d" +
                " contentUri=%s MIME=%s", newAttachment.name, newAttachment.state,
                newAttachment.destination, newAttachment.downloadedSize, newAttachment.contentUri,
                newAttachment.mimeType);

        mAttachment = newAttachment;

        final boolean showProgress = newAttachment.size > 0 && newAttachment.downloadedSize > 0
                && newAttachment.downloadedSize < newAttachment.size;

        if (mViewProgressDialog != null && mViewProgressDialog.isShowing()) {
            mViewProgressDialog.setProgress(newAttachment.downloadedSize);
            mViewProgressDialog.setIndeterminate(!showProgress);

            if (!newAttachment.isDownloading()) {
                mViewProgressDialog.dismiss();
            }

            if (newAttachment.state == AttachmentState.SAVED) {
                sendViewIntent();
            }
        } else {

            if (newAttachment.isDownloading()) {
                mProgress.setProgress(newAttachment.downloadedSize);
                setProgressVisible(true);
                mProgress.setIndeterminate(!showProgress);
            } else {
                setProgressVisible(false);
            }

        }

        if (newAttachment.state == AttachmentState.FAILED) {
            mSubTitle.setText(getResources().getString(R.string.download_failed));
        } else {
            updateSubtitleText(newAttachment.isSavedToExternal() ?
                    getResources().getString(R.string.saved) : null);
        }

        updateActions();
    }

    private void setProgressVisible(boolean visible) {
        if (visible) {
            mProgress.setVisibility(VISIBLE);
            mSubTitle.setVisibility(INVISIBLE);
        } else {
            mProgress.setVisibility(GONE);
            mSubTitle.setVisibility(VISIBLE);
        }
    }

    private void updateSubtitleText(String prefix) {
        // TODO: make this a formatted resource when we have a UX design.
        // not worth translation right now.
        StringBuilder sb = new StringBuilder();
        if (prefix != null) {
            sb.append(prefix);
        }
        sb.append(mAttachmentSizeText);
        sb.append(' ');
        sb.append(mDisplayType);
        mSubTitle.setText(sb.toString());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIcon = (ImageView) findViewById(R.id.attachment_icon);
        mTitle = (TextView) findViewById(R.id.attachment_title);
        mSubTitle = (TextView) findViewById(R.id.attachment_subtitle);
        mProgress = (ProgressBar) findViewById(R.id.attachment_progress);

        mPreviewButton = (Button) findViewById(R.id.preview_attachment);
        mViewButton = (Button) findViewById(R.id.view_attachment);
        mSaveButton = (Button) findViewById(R.id.save_attachment);
        mInfoButton = (Button) findViewById(R.id.info_attachment);
        mPlayButton = (Button) findViewById(R.id.play_attachment);
        mInstallButton = (Button) findViewById(R.id.install_attachment);
        mCancelButton = (Button) findViewById(R.id.cancel_attachment);

        setOnClickListener(this);
        mPreviewButton.setOnClickListener(this);
        mViewButton.setOnClickListener(this);
        mSaveButton.setOnClickListener(this);
        mInfoButton.setOnClickListener(this);
        mPlayButton.setOnClickListener(this);
        mInstallButton.setOnClickListener(this);
        mCancelButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        onClick(v.getId(), v);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return onClick(item.getItemId(), null);
    }

    private boolean onClick(int res, View v) {
        switch (res) {
            case R.id.preview_attachment:
                getContext().startActivity(mAttachment.previewIntent);
                break;
            case R.id.view_attachment:
            case R.id.play_attachment:
                showAttachment(AttachmentDestination.CACHE);
                break;
            case R.id.save_attachment:
                if (mAttachment.canSave()) {
                    startDownloadingAttachment(AttachmentDestination.EXTERNAL);
                }
                break;
            case R.id.info_attachment:
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                int dialogMessage = MimeType.isBlocked(mAttachment.mimeType)
                        ? R.string.attachment_type_blocked : R.string.no_application_found;
                builder.setTitle(R.string.more_info_attachment).setMessage(dialogMessage).show();
                break;
            case R.id.install_attachment:
                showAttachment(AttachmentDestination.EXTERNAL);
                break;
            case R.id.cancel_attachment:
                cancelAttachment();
                break;
            default:
                // entire attachment view is clickable.
                // TODO: this should execute a default action
                break;
        }
        return true;
    }

    private void showAttachment(int destination) {
        if (mAttachment.isPresentLocally()) {
            sendViewIntent();
        } else {
            showDownloadingDialog();
            startDownloadingAttachment(destination);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new AttachmentLoader(getContext(), mAttachment.uri);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        AttachmentCursor cursor = (AttachmentCursor) data;
        if (cursor == null || cursor.isClosed() || cursor.getCount() == 0) {
            return;
        }
        cursor.moveToFirst();
        updateStatus(cursor.get());
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Do nothing.
    }

    private void startDownloadingAttachment(int destination) {
        final ContentValues params = new ContentValues(2);
        params.put(AttachmentColumns.STATE, AttachmentState.DOWNLOADING);
        params.put(AttachmentColumns.DESTINATION, destination);

        mCommandHandler.sendCommand(params);
    }

    private void cancelAttachment() {
        final ContentValues params = new ContentValues(1);
        params.put(AttachmentColumns.STATE, AttachmentState.NOT_SAVED);

        mCommandHandler.sendCommand(params);
    }

    private void setButtonVisible(View button, boolean visible) {
        button.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * Update all action buttons based on current downloading state.
     */
    private void updateActions() {
        // To avoid visibility state transition bugs, every button's visibility should be touched
        // once by this routine.

        final boolean isDownloading = mAttachment.isDownloading();

        setButtonVisible(mCancelButton, isDownloading);

        final boolean canInstall = MimeType.isInstallable(mAttachment.mimeType);
        setButtonVisible(mInstallButton, canInstall && !isDownloading);

        if (!canInstall) {

            final boolean canPreview = (mAttachment.previewIntent != null);
            final boolean canView = MimeType.isViewable(getContext(), mAttachment.mimeType);
            final boolean canPlay = MimeType.isPlayable(mAttachment.mimeType);

            setButtonVisible(mPreviewButton, canPreview);
            setButtonVisible(mPlayButton, canView && canPlay && !isDownloading);
            setButtonVisible(mViewButton, canView && !canPlay && !isDownloading);
            setButtonVisible(mSaveButton, canView && mAttachment.canSave() && !isDownloading);
            setButtonVisible(mInfoButton, !(canPreview || canView));

        } else {

            setButtonVisible(mPreviewButton, false);
            setButtonVisible(mPlayButton, false);
            setButtonVisible(mViewButton, false);
            setButtonVisible(mSaveButton, false);
            setButtonVisible(mInfoButton, false);

        }
    }

    /**
     * View an attachment by an application on device.
     */
    private void sendViewIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        Utils.setIntentDataAndTypeAndNormalize(intent, Uri.parse(mAttachment.contentUri),
                mAttachment.mimeType);
        try {
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // couldn't find activity for View intent
            LogUtils.e(LOG_TAG, "Coun't find Activity for intent", e);
        }
    }

    /**
     * Displays a loading dialog to be used for downloading attachments.
     * Must be called on the UI thread.
     */
    private void showDownloadingDialog() {
        mViewProgressDialog = new ProgressDialog(getContext());
        mViewProgressDialog.setTitle(R.string.fetching_attachment);
        mViewProgressDialog.setMessage(getResources().getString(R.string.please_wait));
        mViewProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mViewProgressDialog.setMax(mAttachment.size);
        mViewProgressDialog.setOnDismissListener(this);
        mViewProgressDialog.setOnCancelListener(this);
        mViewProgressDialog.show();

        // The progress number format needs to be set after the dialog is shown.  See bug: 5149918
        mViewProgressDialog.setProgressNumberFormat(null);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mViewProgressDialog = null;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        cancelAttachment();
    }

}
