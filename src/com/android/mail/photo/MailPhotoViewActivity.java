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

import com.android.ex.photo.PhotoViewActivity;
import com.android.mail.R;
import com.android.mail.browse.AttachmentActionHandler;
import com.android.mail.providers.Attachment;
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
    private MenuItem mRetryItem;
    private MenuItem mSaveItem;
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
        mRetryItem = mMenu.findItem(R.id.menu_retry);
        mShareItem = mMenu.findItem(R.id.menu_share);
        mShareAllItem = mMenu.findItem(R.id.menu_share_all);

        updateActionItems();

        return true;
    }

    /**
     * Updates the action items to tweak their visibility in case
     * there is functionality that is not relevant (eg, the Save
     * button should not appear if the photo has already been saved).
     */
    private void updateActionItems() {
        final boolean runningJellyBeanOrLater = Utils.isRunningJellybeanOrLater();
        final Attachment attachment = getCurrentAttachment();

        if (attachment != null) {
            final boolean isDownloading = attachment.isDownloading();
            final boolean isSavedToExternal = attachment.isSavedToExternal();
            final boolean canSave = attachment.canSave();
            final boolean isPresentLocally = attachment.isPresentLocally();

            mMenu.setGroupVisible(R.id.photo_view_menu_group, isPresentLocally);

            mSaveItem.setVisible(!isDownloading && canSave && !isSavedToExternal);
            mRetryItem.setVisible(attachment.downloadFailed());
        } else {
            mMenu.setGroupVisible(R.id.photo_view_menu_group, false);
            return;
        }

        // all attachments must be present to be able to share all
        List<Attachment> attachments = getAllAttachments();
        if (attachments != null) {
            for (final Attachment a : attachments) {
                if (!a.isPresentLocally()) {
                    mShareAllItem.setVisible(false);
                    break;
                }
            }
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
            case R.id.menu_retry:
                downloadAttachment();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Adjusts the activity title and subtitle to reflect the image name and size.
     */
    @Override
    protected void updateActionBar() {
        super.updateActionBar();

        final Attachment attachment = getCurrentAttachment();
        final boolean isDownloading = attachment.isDownloading();
        final boolean isSavedToExternal = attachment.isSavedToExternal();
        final boolean showProgress = attachment.shouldShowProgress();

        final ActionBar actionBar = getActionBar();
        String subtitle =
                AttachmentUtils.convertToHumanReadableSize(this, attachment.size);

        // update the progress
        if (isDownloading) {
            final double progress = (double) attachment.downloadedSize / attachment.size;
            setProgress((int) (progress * 10000));
            setProgressBarVisibility(true);
            setProgressBarIndeterminate(!showProgress);
        } else {
            setProgressBarVisibility(false);
        }

        // update the status
        // There are 4 states
        //      1. Download failed
        //      2. Saved, Attachment Size
        //      3. Saving...
        //      4. Attachment Size
        if (attachment.downloadFailed()) {
            actionBar.setSubtitle(getResources().getString(R.string.download_failed));
        } else if (isSavedToExternal) {
            actionBar.setSubtitle(
                    getResources().getString(R.string.saved) + " " + subtitle);
        } else if (isDownloading &&
                attachment.destination == AttachmentDestination.EXTERNAL) {
                actionBar.setSubtitle(R.string.saving);
        } else {
            actionBar.setSubtitle(subtitle);
        }

        updateActionItems();
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
