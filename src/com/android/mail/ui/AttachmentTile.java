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
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

import com.android.ex.photo.util.ImageUtils;
import com.android.mail.R;
import com.android.mail.providers.Attachment;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.AttachmentUtils;
import com.android.mail.utils.LogUtils;

/**
 * Base class for attachment tiles that handles the work of fetching and displaying the bitmaps for
 * the tiles.
 */
public class AttachmentTile extends RelativeLayout implements AttachmentBitmapHolder {
    protected Attachment mAttachment;
    private ImageView mIcon;
    private ImageView mDefaultIcon;
    private ThumbnailLoadTask mThumbnailTask;
    private TextView mTitle;
    private TextView mSubtitle;
    private String mAttachmentSizeText;
    private String mDisplayType;
    private boolean mDefaultThumbnailSet;
    private AttachmentPreviewCache mAttachmentPreviewCache;

    private static final String LOG_TAG = LogTag.getLogTag();

    /**
     * Returns true if the attachment should be rendered as a tile. with a large image preview.
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
        mDefaultThumbnailSet = true;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTitle = (TextView) findViewById(R.id.attachment_tile_title);
        mSubtitle = (TextView) findViewById(R.id.attachment_tile_subtitle);
        mIcon = (ImageView) findViewById(R.id.attachment_tile_image);
        mDefaultIcon = (ImageView) findViewById(R.id.attachment_default_image);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        ThumbnailLoadTask.setupThumbnailPreview(mThumbnailTask, this, mAttachment, null);
    }

    /**
     * Render or update an attachment's view. This happens immediately upon instantiation, and
     * repeatedly as status updates stream in, so only properties with new or changed values will
     * cause sub-views to update.
     */
    public void render(Attachment attachment, Uri attachmentsListUri, int index,
            AttachmentPreviewCache attachmentPreviewCache) {
        if (attachment == null) {
            setVisibility(View.INVISIBLE);
            return;
        }

        final Attachment prevAttachment = mAttachment;
        mAttachment = attachment;
        mAttachmentPreviewCache = attachmentPreviewCache;

        LogUtils.d(LOG_TAG, "got attachment list row: name=%s state/dest=%d/%d dled=%d" +
                " contentUri=%s MIME=%s", attachment.name, attachment.state,
                attachment.destination, attachment.downloadedSize, attachment.contentUri,
                attachment.contentType);

        if (prevAttachment == null || !TextUtils.equals(attachment.name, prevAttachment.name)) {
            mTitle.setText(attachment.name);
        }

        if (prevAttachment == null || attachment.size != prevAttachment.size) {
            mAttachmentSizeText = AttachmentUtils.convertToHumanReadableSize(getContext(),
                    attachment.size);
            mDisplayType = AttachmentUtils.getDisplayType(getContext(), attachment);
            updateSubtitleText();
        }

        ThumbnailLoadTask.setupThumbnailPreview(mThumbnailTask, this, attachment, prevAttachment);
    }

    private void updateSubtitleText() {
        // TODO: make this a formatted resource when we have a UX design.
        // not worth translation right now.
        StringBuilder sb = new StringBuilder();
        sb.append(mAttachmentSizeText);
        sb.append(' ');
        sb.append(mDisplayType);
        mSubtitle.setText(sb.toString());
    }

    @Override
    public void setThumbnailToDefault() {
        Bitmap cachedPreview = mAttachmentPreviewCache.get(mAttachment);
        if (cachedPreview != null) {
            setThumbnail(cachedPreview);
            return;
        }
        mDefaultIcon.setVisibility(View.VISIBLE);
        mDefaultThumbnailSet = true;
    }

    @Override
    public void setThumbnail(Bitmap result) {
        // We got a real thumbnail; hide the default thumbnail.
        mDefaultIcon.setVisibility(View.GONE);
        mIcon.setImageBitmap(result);
        if (result.getWidth() < mIcon.getWidth() || result.getHeight() < mIcon.getHeight()) {
            mIcon.setScaleType(ScaleType.CENTER);
        } else {
            mIcon.setScaleType(ScaleType.CENTER_CROP);
        }
        mAttachmentPreviewCache.set(mAttachment, result);
        mDefaultThumbnailSet = false;
    }

    @Override
    public int getThumbnailWidth() {
        return mIcon.getWidth();
    }

    @Override
    public int getThumbnailHeight() {
        return mIcon.getHeight();
    }

    @Override
    public ContentResolver getResolver() {
        return getContext().getContentResolver();
    }

    @Override
    public boolean bitmapSetToDefault() {
        return mDefaultThumbnailSet;
    }

    public static final class AttachmentPreview implements Parcelable {
        public String attachmentIdentifier;
        public Bitmap preview;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(attachmentIdentifier);
            dest.writeParcelable(preview, 0);
        }

        public static final Parcelable.Creator<AttachmentPreview> CREATOR
                = new Parcelable.Creator<AttachmentPreview>() {
                        @Override
                    public AttachmentPreview createFromParcel(Parcel in) {
                        return new AttachmentPreview(in);
                    }

                        @Override
                    public AttachmentPreview[] newArray(int size) {
                        return new AttachmentPreview[size];
                    }
                };

        private AttachmentPreview(Parcel in) {
            attachmentIdentifier = in.readString();
            preview = in.readParcelable(null);
        }

        public AttachmentPreview(Attachment attachment, Bitmap preview) {
            this.attachmentIdentifier = AttachmentUtils.getIdentifier(attachment);
            this.preview = preview;
        }
    }

    public interface AttachmentPreviewCache {
        void set(Attachment attachment, Bitmap preview);
        Bitmap get(Attachment attachment);
    }
}
