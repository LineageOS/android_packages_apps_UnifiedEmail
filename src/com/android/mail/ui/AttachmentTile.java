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

package com.android.mail.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.ex.photo.util.ImageUtils;
import com.android.mail.R;
import com.android.mail.providers.Attachment;
import com.android.mail.utils.LogUtils;

/**
 * Base class for attachment tiles that handles the work
 * of fetching and displaying the bitmaps for the tiles.
 */
public class AttachmentTile extends RelativeLayout implements AttachmentBitmapHolder {
    protected Attachment mAttachment;
    private ImageView mIcon;
    private ImageView.ScaleType mIconScaleType;
    private ThumbnailLoadTask mThumbnailTask;

    private static final String LOG_TAG = new LogUtils().getLogTag();

    /**
     * Returns true if the attachment should be rendered as a tile.
     * with a large image preview.
     * @param attachment the attachment to render
     * @return true if the attachment should be rendered as a tile
     */
    public static boolean isTiledAttachment(final Attachment attachment) {
        return ImageUtils.isImageMimeType(attachment.contentType);
    }

    public AttachmentTile(Context context) {
        this(context, null);
    }

    public AttachmentTile(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIcon = (ImageView) findViewById(R.id.attachment_tile_image);
        mIconScaleType = mIcon.getScaleType();
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

        LogUtils.d(LOG_TAG, "got attachment list row: name=%s state/dest=%d/%d dled=%d" +
                " contentUri=%s MIME=%s", attachment.name, attachment.state,
                attachment.destination, attachment.downloadedSize, attachment.contentUri,
                attachment.contentType);

        ThumbnailLoadTask.setupThumbnailPreview(mThumbnailTask, this, attachment, prevAttachment);
    }

    public void setThumbnailToDefault() {
        mIcon.setImageResource(R.drawable.ic_menu_attachment_holo_light);
        mIcon.setScaleType(ImageView.ScaleType.CENTER);
    }

    public void setThumbnail(Bitmap result) {
        mIcon.setImageBitmap(result);
        mIcon.setScaleType(mIconScaleType);
    }

    public int getThumbnailWidth() {
        return mIcon.getWidth();
    }

    public int getThumbnailHeight() {
        return mIcon.getHeight();
    }

    @Override
    public ContentResolver getResolver() {
        return getContext().getContentResolver();
    }
}
