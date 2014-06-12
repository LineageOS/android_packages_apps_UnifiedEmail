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

package com.android.mail.photo;

import android.app.ActionBar;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.ex.photo.Intents;
import com.android.ex.photo.PhotoViewActivity;
import com.android.ex.photo.PhotoViewController;
import com.android.ex.photo.fragments.PhotoViewFragment;
import com.android.ex.photo.views.ProgressBarWrapper;
import com.android.mail.R;
import com.android.mail.browse.AttachmentActionHandler;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AttachmentDestination;
import com.android.mail.providers.UIProvider.AttachmentState;
import com.android.mail.utils.AttachmentUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives from {@link PhotoViewActivity} to allow customization
 * to the {@link ActionBar} from the default implementation.
 */
public class MailPhotoViewActivity extends PhotoViewActivity {

    /**
     * Start a new MailPhotoViewActivity to view the given images.
     *
     * @param imageListUri The uri to query for the images that you want to view. The resulting
     *                     cursor must have the columns as defined in
     *                     {@link com.android.ex.photo.provider.PhotoContract.PhotoViewColumns}.
     * @param photoIndex The index of the photo to show first.
     */
    public static void startMailPhotoViewActivity(final Context context, final Uri imageListUri,
            final int photoIndex) {
        final Intents.PhotoViewIntentBuilder builder =
                Intents.newPhotoViewIntentBuilder(context, MailPhotoViewActivity.class);
        builder
                .setPhotosUri(imageListUri.toString())
                .setProjection(UIProvider.ATTACHMENT_PROJECTION)
                .setPhotoIndex(photoIndex);

        context.startActivity(builder.build());
    }

    /**
     * Start a new MailPhotoViewActivity to view the given images.
     *
     * @param imageListUri The uri to query for the images that you want to view. The resulting
     *                     cursor must have the columns as defined in
     *                     {@link com.android.ex.photo.provider.PhotoContract.PhotoViewColumns}.
     * @param initialPhotoUri The uri of the photo to show first.
     */
    public static void startMailPhotoViewActivity(final Context context, final Uri imageListUri,
            final String initialPhotoUri) {
        final Intents.PhotoViewIntentBuilder builder =
                Intents.newPhotoViewIntentBuilder(context, MailPhotoViewActivity.class);
        builder
                .setPhotosUri(imageListUri.toString())
                .setProjection(UIProvider.ATTACHMENT_PROJECTION)
                .setInitialPhotoUri(initialPhotoUri);

        context.startActivity(builder.build());
    }

    @Override
    public PhotoViewController createController() {
        return new MailPhotoViewController(this);
    }

    @Override
    public MailPhotoViewController getController() {
        return (MailPhotoViewController) super.getController();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_PROGRESS);
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return getController().onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return getController().onPrepareOptionsMenu(menu);
    }

}
