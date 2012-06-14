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

import android.content.ActivityNotFoundException;
import android.content.Context;
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
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.RelativeLayout;

import com.android.mail.R;
import com.android.mail.photo.Intents;
import com.android.mail.photo.Intents.PhotoViewIntentBuilder;
import com.android.mail.photo.MailPhotoViewActivity;
import com.android.mail.photo.util.ImageUtils;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AttachmentDestination;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import java.io.IOException;

/**
 * View for a single attachment in conversation view. Shows download status and allows launching
 * intents to act on an attachment.
 *
 */
public class MessageAttachmentTile extends RelativeLayout implements OnClickListener,
        OnMenuItemClickListener, AttachmentViewInterface {

    private Attachment mAttachment;
    private ImageView mIcon;
    private ImageView.ScaleType mIconScaleType;
    private int mPhotoIndex;
    private Uri mAttachmentsListUri;

    private final AttachmentActionHandler mActionHandler;

    private ThumbnailLoadTask mThumbnailTask;

    private static final String LOG_TAG = new LogUtils().getLogTag();

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
        this(context, null);
    }

    public MessageAttachmentTile(Context context, AttributeSet attrs) {
        super(context, attrs);

        mActionHandler = new AttachmentActionHandler(context, this);
    }

    public static MessageAttachmentTile inflate(LayoutInflater inflater, ViewGroup parent) {
        MessageAttachmentTile view = (MessageAttachmentTile) inflater.inflate(
                R.layout.conversation_message_attachment_tile, parent, false);
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
        mActionHandler.setAttachment(mAttachment);
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

        mActionHandler.updateStatus();
    }

    private void setThumbnailToDefault() {
        mIcon.setImageResource(R.drawable.ic_menu_attachment_holo_light);
        mIcon.setScaleType(ImageView.ScaleType.CENTER);
    }



    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIcon = (ImageView) findViewById(R.id.attachment_tile_image);

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
        mActionHandler.showAttachment(AttachmentDestination.CACHE);

        return true;
    }

    public void viewAttachment() {
        if (ImageUtils.isImageMimeType(Utils.normalizeMimeType(mAttachment.contentType))) {
            final PhotoViewIntentBuilder builder =
                    Intents.newPhotoViewIntentBuilder(getContext(), MailPhotoViewActivity.class);
            builder.setPhotoName(mAttachment.name)
                .setPhotosUri(mAttachmentsListUri.toString())
                .setProjection(UIProvider.ATTACHMENT_PROJECTION)
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

    public void updateProgress(boolean showDeterminateProgress) {
    }

    public void onUpdateStatus() {
    }
}
