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

import android.app.FragmentManager;;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.android.ex.photo.Intents;
import com.android.ex.photo.Intents.PhotoViewIntentBuilder;
import com.android.ex.photo.util.ImageUtils;
import com.android.mail.R;
import com.android.mail.photo.MailPhotoViewActivity;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.AttachmentTile;
import com.android.mail.ui.AttachmentTileGrid;
import com.android.mail.ui.AttachmentTile.AttachmentPreviewCache;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import java.util.List;

/**
 * View for a single attachment in conversation view. Shows download status and allows launching
 * intents to act on an attachment.
 *
 */
public class MessageAttachmentTile extends AttachmentTile implements OnClickListener,
        AttachmentViewInterface {

    private int mPhotoIndex;
    private Uri mAttachmentsListUri;
    private View mTextContainer;

    private final AttachmentActionHandler mActionHandler;

    private static final String LOG_TAG = LogTag.getLogTag();

    public MessageAttachmentTile(Context context) {
        this(context, null);
    }

    public MessageAttachmentTile(Context context, AttributeSet attrs) {
        super(context, attrs);

        mActionHandler = new AttachmentActionHandler(context, this);
    }

    public void initialize(FragmentManager fragmentManager) {
        mActionHandler.initialize(fragmentManager);
    }

    /**
     * Render or update an attachment's view. This happens immediately upon instantiation, and
     * repeatedly as status updates stream in, so only properties with new or changed values will
     * cause sub-views to update.
     */
    @Override
    public void render(Attachment attachment, Uri attachmentsListUri, int index,
            AttachmentPreviewCache attachmentPreviewCache, boolean loaderResult) {
        super.render(attachment, attachmentsListUri, index, attachmentPreviewCache, loaderResult);

        mAttachmentsListUri = attachmentsListUri;
        mPhotoIndex = index;

        mActionHandler.setAttachment(mAttachment);
        mActionHandler.updateStatus(loaderResult);
    }

    public static MessageAttachmentTile inflate(LayoutInflater inflater, ViewGroup parent) {
        MessageAttachmentTile view = (MessageAttachmentTile) inflater.inflate(
                R.layout.conversation_message_attachment_tile, parent, false);
        return view;
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTextContainer = findViewById(R.id.attachment_tile_text_container);

        setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        onClick(v.getId(), v);
    }

    private boolean onClick(int res, View v) {
        mActionHandler.showAndDownloadAttachments();

        return true;
    }

    public void viewAttachment() {
        if (ImageUtils.isImageMimeType(Utils.normalizeMimeType(mAttachment.contentType))) {
            final PhotoViewIntentBuilder builder =
                    Intents.newPhotoViewIntentBuilder(getContext(), MailPhotoViewActivity.class);
            builder
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

    @Override
    public void setThumbnailToDefault() {
        super.setThumbnailToDefault();
        mTextContainer.setVisibility(VISIBLE);
    }

    @Override
    public void setThumbnail(Bitmap result) {
        super.setThumbnail(result);
        mTextContainer.setVisibility(GONE);
    }

    @Override
    public List<Attachment> getAttachments() {
        return ((AttachmentTileGrid) getParent()).getAttachments();
    }
}
