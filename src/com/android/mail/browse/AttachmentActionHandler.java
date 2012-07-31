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

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;

import com.android.mail.R;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider.AttachmentColumns;
import com.android.mail.providers.UIProvider.AttachmentDestination;
import com.android.mail.providers.UIProvider.AttachmentState;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class AttachmentActionHandler implements DialogInterface.OnCancelListener,
        DialogInterface.OnDismissListener {
    private ProgressDialog mViewProgressDialog;
    private Attachment mAttachment;
    private boolean mDialogClosed;

    private final AttachmentCommandHandler mCommandHandler;
    private final AttachmentViewInterface mView;
    private final Context mContext;

    private static final String LOG_TAG = LogTag.getLogTag();

    private class AttachmentCommandHandler extends AsyncQueryHandler {

        public AttachmentCommandHandler(Context context) {
            super(context.getContentResolver());
        }

        /**
         * Asynchronously begin an update() on a ContentProvider.
         *
         */
        public void sendCommand(Uri uri, ContentValues params) {
            startUpdate(0, null, uri, params, null, null);
        }

    }

    public AttachmentActionHandler(Context context, AttachmentViewInterface view) {
        mCommandHandler = new AttachmentCommandHandler(context);
        mView = view;
        mContext = context;
        mDialogClosed = false;
    }

    public void setAttachment(Attachment attachment) {
        mAttachment = attachment;
    }

    public void showAttachment(int destination) {
        if (mAttachment.isPresentLocally()) {
            mView.viewAttachment();
        } else {
            showDownloadingDialog();
            startDownloadingAttachment(destination);
        }
    }

    public void showAndDownloadAttachments() {
        final List<Attachment> attachments = mView.getAttachments();

        for (final Attachment attachment : attachments) {
            if (!attachment.isPresentLocally()) {
                startDownloadingAttachment(attachment, AttachmentDestination.CACHE);
            }
        }

        mView.viewAttachment();
    }

    public void startDownloadingAttachment(int destination) {
        startDownloadingAttachment(mAttachment, destination);
    }

    private void startDownloadingAttachment(Attachment attachment, int destination) {
        final ContentValues params = new ContentValues(2);
        params.put(AttachmentColumns.STATE, AttachmentState.DOWNLOADING);
        params.put(AttachmentColumns.DESTINATION, destination);

        mCommandHandler.sendCommand(attachment.uri, params);
    }

    public void cancelAttachment() {
        final ContentValues params = new ContentValues(1);
        params.put(AttachmentColumns.STATE, AttachmentState.NOT_SAVED);

        mCommandHandler.sendCommand(mAttachment.uri, params);
    }

    /**
     * Displays a loading dialog to be used for downloading attachments.
     * Must be called on the UI thread.
     */
    private void showDownloadingDialog() {
        mViewProgressDialog = new ProgressDialog(mContext);
        mViewProgressDialog.setTitle(R.string.fetching_attachment);
        mViewProgressDialog.setMessage(mContext.getResources().getString(R.string.please_wait));
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
        mDialogClosed = true;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        cancelAttachment();
    }

    /**
     * Update progress-related views. Will also trigger a view intent if a progress dialog was
     * previously brought up (by tapping 'View') and the download has now finished.
     */
    public void updateStatus() {
        final boolean showProgress = mAttachment.shouldShowProgress();

        if (isProgressDialogVisible()) {
            mViewProgressDialog.setProgress(mAttachment.downloadedSize);
            mViewProgressDialog.setIndeterminate(!showProgress);

            if (!mAttachment.isDownloading()) {
                mViewProgressDialog.dismiss();
            }

            if (mAttachment.state == AttachmentState.SAVED) {
                mView.viewAttachment();
            }
        } else {
            mView.updateProgress(showProgress);
        }

        // Call on update status for the view so that it can do some specific things.
        mView.onUpdateStatus();
    }

    public boolean isProgressDialogVisible() {
        return mViewProgressDialog != null && mViewProgressDialog.isShowing();
    }

    public void shareAttachment() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        final Uri uri = Utils.normalizeUri(mAttachment.contentUri);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setType(Utils.normalizeMimeType(mAttachment.contentType));

        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // couldn't find activity for SEND intent
            LogUtils.e(LOG_TAG, "Couldn't find Activity for intent", e);
        }
    }

    public void shareAttachments(ArrayList<Parcelable> uris) {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        intent.setType("image/*");
        intent.putParcelableArrayListExtra(
                Intent.EXTRA_STREAM, uris);

        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // couldn't find activity for SEND_MULTIPLE intent
            LogUtils.e(LOG_TAG, "Couldn't find Activity for intent", e);
        }
    }

    /**
     * Returns true if this is the first time this method has been called after
     * the progress dialog was closed. Necessary to prevent a brief flicker where the
     * cancel button would appear after closing the progress dialog. Subsequent
     * calls to this method will return false until the progress dialog is
     * opened again.
     * @return true if this is the first time this method has been called
     * since a progress dialog was visible. false otherwise.
     */
    public boolean dialogJustClosed() {
        final boolean closed = mDialogClosed;
        mDialogClosed = false;
        return closed;
    }
}
