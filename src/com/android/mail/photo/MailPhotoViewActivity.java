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
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.ex.photo.PhotoViewActivity;
import com.android.ex.photo.fragments.PhotoViewFragment;
import com.android.ex.photo.views.ProgressBarWrapper;
import com.android.mail.R;
import com.android.mail.browse.AttachmentActionHandler;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider.AttachmentDestination;
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
    private MenuItem mSaveItem;
    private MenuItem mSaveAllItem;
    private MenuItem mShareItem;
    private MenuItem mShareAllItem;
    private AttachmentActionHandler mActionHandler;
    private Menu mMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_PROGRESS);
        super.onCreate(savedInstanceState);

        mActionHandler = new AttachmentActionHandler(this, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.photo_view_menu, menu);
        mMenu = menu;

        mSaveItem = mMenu.findItem(R.id.menu_save);
        mSaveAllItem = mMenu.findItem(R.id.menu_save_all);
        mShareItem = mMenu.findItem(R.id.menu_share);
        mShareAllItem = mMenu.findItem(R.id.menu_share_all);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateActionItems();
        return true;
    }

    /**
     * Updates the action items to tweak their visibility in case
     * there is functionality that is not relevant (eg, the Save
     * button should not appear if the photo has already been saved).
     */
    @Override
    protected void updateActionItems() {
        final boolean runningJellyBeanOrLater = Utils.isRunningJellybeanOrLater();
        final Attachment attachment = getCurrentAttachment();

        if (attachment != null && mSaveItem != null && mShareItem != null) {
            mSaveItem.setEnabled(!attachment.isDownloading()
                    && attachment.canSave() && !attachment.isSavedToExternal());
            mShareItem.setEnabled(attachment.canShare());
        } else {
            if (mMenu != null) {
                mMenu.setGroupEnabled(R.id.photo_view_menu_group, false);
            }
            return;
        }

        List<Attachment> attachments = getAllAttachments();
        if (attachments != null) {
            boolean enabled = false;
            for (final Attachment a : attachments) {
                // If one attachment can be saved, enable save all
                if (!a.isDownloading() && a.canSave() && !a.isSavedToExternal()) {
                    enabled = true;
                    break;
                }
            }
            mSaveAllItem.setEnabled(enabled);

            // all attachments must be present to be able to share all
            enabled = true;
            for (final Attachment a : attachments) {
                if (!a.canShare()) {
                    enabled = false;
                    break;
                }
            }
            mShareAllItem.setEnabled(enabled);
        }

        // Turn off the functionality that only works on JellyBean.
        if (!runningJellyBeanOrLater) {
            mShareItem.setVisible(false);
            mShareAllItem.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // app icon in action bar clicked; go back to conversation
                finish();
                return true;
            case R.id.menu_save: // save the current photo
                saveAttachment();
                return true;
            case R.id.menu_save_all: // save all of the photos
                saveAllAttachments();
                return true;
            case R.id.menu_share: // share the current photo
                shareAttachment();
                return true;
            case R.id.menu_share_all: // share all of the photos
                shareAllAttachments();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Adjusts the activity title and subtitle to reflect the image name and size.
     */
    @Override
    protected void updateActionBar(PhotoViewFragment fragment) {
        super.updateActionBar(fragment);

        final Attachment attachment = getCurrentAttachment();

        final ActionBar actionBar = getActionBar();
        String subtitle =
                AttachmentUtils.convertToHumanReadableSize(this, attachment.size);


        // update the status
        // There are 3 states
        //      1. Saved, Attachment Size
        //      2. Saving...
        //      3. Default, Attachment Size
        if (attachment.isSavedToExternal()) {
            actionBar.setSubtitle(
                    getResources().getString(R.string.saved) + " " + subtitle);
        } else if (attachment.isDownloading() &&
                attachment.destination == AttachmentDestination.EXTERNAL) {
                actionBar.setSubtitle(R.string.saving);
        } else {
            actionBar.setSubtitle(subtitle);
        }

        updateActionItems();
    }

    @Override
    public void onFragmentVisible(PhotoViewFragment fragment) {
        super.onFragmentVisible(fragment);

        final Attachment attachment = getCurrentAttachment();
        updateProgressAndEmptyViews(fragment, attachment);
    }


    /**
     * Updates the empty views of the fragment based upon the current
     * state of the attachment.
     * @param fragment the current fragment
     * @param attachment the current {@link Attachment}
     */
    private void updateProgressAndEmptyViews(
            PhotoViewFragment fragment, final Attachment attachment) {

        final ProgressBarWrapper progressBar = fragment.getPhotoProgressBar();
        final TextView emptyText = fragment.getEmptyText();
        final ImageView retryButton = fragment.getRetryButton();

        // update the progress
        if (attachment.shouldShowProgress()) {
            progressBar.setMax(attachment.size);
            progressBar.setProgress(attachment.downloadedSize);
            progressBar.setIndeterminate(false);
        } else if (fragment.isProgressBarNeeded()) {
            progressBar.setIndeterminate(true);
        }

        // If the download failed, show the empty text and retry button
        if (attachment.downloadFailed()) {
            emptyText.setText(R.string.photo_load_failed);
            emptyText.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
            retryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    downloadAttachment();
                    progressBar.setVisibility(View.VISIBLE);
                }
            });
            progressBar.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
        }
    }

    /**
     * Save the current attachment.
     */
    private void saveAttachment() {
        saveAttachment(getCurrentAttachment());
    }

    /**
     * Downloads the attachment.
     */
    private void downloadAttachment() {
        final Attachment attachment = getCurrentAttachment();
        if (attachment != null && attachment.canSave()) {
            mActionHandler.setAttachment(attachment);
            mActionHandler.startDownloadingAttachment(AttachmentDestination.CACHE);
        }
    }

    /**
     * Saves the attachment.
     * @param attachment the attachment to save.
     */
    private void saveAttachment(final Attachment attachment) {
        if (attachment != null && attachment.canSave()) {
            mActionHandler.setAttachment(attachment);
            mActionHandler.startDownloadingAttachment(AttachmentDestination.EXTERNAL);
        }
    }

    /**
     * Save all of the attachments in the cursor.
     */
    private void saveAllAttachments() {
        Cursor cursor = getCursorAtProperPosition();

        if (cursor == null) {
            return;
        }

        int i = -1;
        while (cursor.moveToPosition(++i)) {
            saveAttachment(new Attachment(cursor));
        }
    }

    /**
     * Share the current attachment.
     */
    private void shareAttachment() {
        shareAttachment(getCurrentAttachment());
    }

    /**
     * Shares the attachment
     * @param attachment the attachment to share
     */
    private void shareAttachment(final Attachment attachment) {
        if (attachment != null) {
            mActionHandler.setAttachment(attachment);
            mActionHandler.shareAttachment();
        }
    }

    /**
     * Share all of the attachments in the cursor.
     */
    private void shareAllAttachments() {
        Cursor cursor = getCursorAtProperPosition();

        if (cursor == null) {
            return;
        }

        ArrayList<Parcelable> uris = new ArrayList<Parcelable>();
        int i = -1;
        while (cursor.moveToPosition(++i)) {
            uris.add(Utils.normalizeUri(new Attachment(cursor).contentUri));
        }

        mActionHandler.shareAttachments(uris);
    }

    /**
     * Helper method to get the currently visible attachment.
     * @return
     */
    private Attachment getCurrentAttachment() {
        final Cursor cursor = getCursorAtProperPosition();

        if (cursor == null) {
            return null;
        }

        return new Attachment(cursor);
    }

    private List<Attachment> getAllAttachments() {
        final Cursor cursor = getCursor();

        if (cursor == null || cursor.isClosed() || !cursor.moveToFirst()) {
            return null;
        }

        List<Attachment> list = Lists.newArrayList();
        do {
            list.add(new Attachment(cursor));
        } while (cursor.moveToNext());

        return list;
    }
}
