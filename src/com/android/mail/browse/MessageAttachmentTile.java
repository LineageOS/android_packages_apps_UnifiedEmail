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
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ProgressBar;

import com.android.mail.R;
import com.android.mail.photo.Intents;
import com.android.mail.photo.Intents.PhotoViewIntentBuilder;
import com.android.mail.photo.util.ImageUtils;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider.AttachmentColumns;
import com.android.mail.providers.UIProvider.AttachmentDestination;
import com.android.mail.providers.UIProvider.AttachmentState;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MimeType;
import com.android.mail.utils.Utils;

import java.io.IOException;

/**
 * View for a single attachment in conversation view. Shows download status and allows launching
 * intents to act on an attachment.
 *
 */
public class MessageAttachmentTile extends LinearLayout implements OnClickListener,
        OnMenuItemClickListener, DialogInterface.OnCancelListener,
        DialogInterface.OnDismissListener {

    private Attachment mAttachment;
    private ImageView mIcon;
    private ImageView.ScaleType mIconScaleType;
    private int mPhotoIndex;
    private Uri mAttachmentsListUri;
    private ProgressDialog mViewProgressDialog;
    private AttachmentCommandHandler mCommandHandler;
    private ProgressBar mProgress;

    private ThumbnailLoadTask mThumbnailTask;

    private static final String LOG_TAG = new LogUtils().getLogTag();

    private class AttachmentCommandHandler extends AsyncQueryHandler {

        public AttachmentCommandHandler() {
            super(getContext().getContentResolver());
        }

        /**
         * Asynchronously begin an update() on a ContentProvider.
         *
         */
        public void sendCommand(ContentValues params) {
            startUpdate(0, null, mAttachment.uri, params, null, null);
        }

    }

    private class ThumbnailLoadTask extends AsyncTask<Uri, Void, Bitmap> {

        private final int mWidth;
        private final int mHeight;

        public ThumbnailLoadTask(int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        @Override
        protected Bitmap doInBackground(Uri... params) {
            final Uri thumbnailUri = params[0];

            AssetFileDescriptor fd = null;
            Bitmap result = null;

            try {
                fd = getContext().getContentResolver().openAssetFileDescriptor(thumbnailUri, "r");
                if (isCancelled() || fd == null) {
                    return null;
                }

                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;

                BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, opts);
                if (isCancelled() || opts.outWidth == -1 || opts.outHeight == -1) {
                    return null;
                }

                opts.inJustDecodeBounds = false;

                LogUtils.d(LOG_TAG, "in background, src w/h=%d/%d dst w/h=%d/%d, divider=%d",
                        opts.outWidth, opts.outHeight, mWidth, mHeight, opts.inSampleSize);

                result = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, opts);

            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Unable to decode thumbnail %s", thumbnailUri);
            } finally {
                if (fd != null) {
                    try {
                        fd.close();
                    } catch (IOException e) {
                        LogUtils.e(LOG_TAG, e, "");
                    }
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result == null) {
                LogUtils.d(LOG_TAG, "back in UI thread, decode failed");
                setThumbnailToDefault();
                return;
            }

            LogUtils.d(LOG_TAG, "back in UI thread, decode success, w/h=%d/%d", result.getWidth(),
                    result.getHeight());
            mIcon.setImageBitmap(result);
            mIcon.setScaleType(mIconScaleType);
        }

    }

    public MessageAttachmentTile(Context context) {
        super(context);
    }

    public MessageAttachmentTile(Context context, AttributeSet attrs) {
        super(context, attrs);

        mCommandHandler = new AttachmentCommandHandler();
    }

    public static MessageAttachmentTile inflate(LayoutInflater inflater, ViewGroup parent) {
        MessageAttachmentTile view = (MessageAttachmentTile) inflater.inflate(
                R.layout.conversation_message_attachment, parent, false);
        return view;
    }

    /**
     * Render or update an attachment's view. This happens immediately upon instantiation, and
     * repeatedly as status updates stream in, so only properties with new or changed values will
     * cause sub-views to update.
     *
     */
    public void render(Attachment attachment, Uri attachmentsListUri, int index) {
        if (attachment == null) {
            setVisibility(View.INVISIBLE);
            return;
        }

        final Attachment prevAttachment = mAttachment;
        mAttachment = attachment;
        mAttachmentsListUri = attachmentsListUri;
        mPhotoIndex = index;

        LogUtils.d(LOG_TAG, "got attachment list row: name=%s state/dest=%d/%d dled=%d" +
                " contentUri=%s MIME=%s", attachment.name, attachment.state,
                attachment.destination, attachment.downloadedSize, attachment.contentUri,
                attachment.contentType);

        final Uri imageUri = attachment.getImageUri();
        final Uri prevImageUri = (prevAttachment == null) ? null : prevAttachment.getImageUri();
        // begin loading a thumbnail if this is an image and either the thumbnail or the original
        // content is ready (and different from any existing image)
        if (imageUri != null && (prevImageUri == null || !imageUri.equals(prevImageUri))) {
            // cancel/dispose any existing task and start a new one
            if (mThumbnailTask != null) {
                mThumbnailTask.cancel(true);
            }
            mThumbnailTask = new ThumbnailLoadTask(mIcon.getWidth(), mIcon.getHeight());
            mThumbnailTask.execute(imageUri);
        } else if (imageUri == null) {
            // not an image, or no thumbnail exists. fall back to default.
            // async image load must separately ensure the default appears upon load failure.
            setThumbnailToDefault();
        }

        if (mProgress != null) {
            mProgress.setMax(attachment.size);
        }

        updateStatus();
    }

    private void setThumbnailToDefault() {
        mIcon.setImageResource(R.drawable.ic_menu_attachment_holo_light);
        mIcon.setScaleType(ImageView.ScaleType.CENTER);
    }

    /**
     * Update progress-related views. Will also trigger a view intent if a progress dialog was
     * previously brought up (by tapping 'View') and the download has now finished.
     */
    private void updateStatus() {
        final boolean showProgress = mAttachment.size > 0 && mAttachment.downloadedSize > 0
                && mAttachment.downloadedSize < mAttachment.size;

        if (mViewProgressDialog != null && mViewProgressDialog.isShowing()) {
            mViewProgressDialog.setProgress(mAttachment.downloadedSize);
            mViewProgressDialog.setIndeterminate(!showProgress);

            if (!mAttachment.isDownloading()) {
                mViewProgressDialog.dismiss();
            }

            if (mAttachment.state == AttachmentState.SAVED) {
                sendViewIntent();
            }
        } else {

            if (mAttachment.isDownloading()) {
                mProgress.setProgress(mAttachment.downloadedSize);
                setProgressVisible(true);
                mProgress.setIndeterminate(!showProgress);
            } else {
                setProgressVisible(false);
            }

        }
    }

    private void setProgressVisible(boolean visible) {
        if (visible) {
            mProgress.setVisibility(VISIBLE);
        } else {
            mProgress.setVisibility(GONE);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIcon = (ImageView) findViewById(R.id.attachment_tile_image);
        mProgress = (ProgressBar) findViewById(R.id.attachment_progress);

        setOnClickListener(this);

        mIconScaleType = mIcon.getScaleType();
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
            case R.id.attachment_tile:
                showAttachment(AttachmentDestination.CACHE);
                break;
            case R.id.save_attachment:
                if (mAttachment.canSave()) {
                    startDownloadingAttachment(AttachmentDestination.EXTERNAL);
                }
                break;
            case R.id.info_attachment:
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                int dialogMessage = MimeType.isBlocked(mAttachment.contentType)
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

    /**
     * View an attachment by an application on device.
     */
    private void sendViewIntent() {
        if (ImageUtils.isImageMimeType(Utils.normalizeMimeType(mAttachment.contentType))) {
            final PhotoViewIntentBuilder builder =
                    Intents.newPhotoViewActivityIntentBuilder(getContext());
            builder.setAlbumName(mAttachment.name)
                .setPhotosUri(mAttachmentsListUri.toString())
                .setPhotoIndex(mPhotoIndex);

            getContext().startActivity(builder.build());
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        Utils.setIntentDataAndTypeAndNormalize(intent, mAttachment.contentUri,
                mAttachment.contentType);
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
