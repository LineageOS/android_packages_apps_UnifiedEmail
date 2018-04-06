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


import android.app.DialogFragment;
import android.app.DownloadManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;

import android.widget.Toast;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AttachmentColumns;
import com.android.mail.providers.UIProvider.AttachmentContentValueKeys;
import com.android.mail.providers.UIProvider.AttachmentDestination;
import com.android.mail.providers.UIProvider.AttachmentState;
import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MimeType;
import com.android.mail.utils.Utils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class AttachmentActionHandler {
    private static final String PROGRESS_FRAGMENT_TAG = "attachment-progress";

    private String mAccount;
    private Message mMessage;
    private Attachment mAttachment;

    private final AttachmentCommandHandler mCommandHandler;
    private final AttachmentViewInterface mView;
    private final Context mContext;
    private final Handler mHandler;
    private FragmentManager mFragmentManager;
    private boolean mViewOnFinish;

    private static final String LOG_TAG = LogTag.getLogTag();

    private static OptionHandler sOptionHandler = new OptionHandler();

    public AttachmentActionHandler(Context context, AttachmentViewInterface view) {
        mCommandHandler = new AttachmentCommandHandler(context);
        mView = view;
        mContext = context;
        mHandler = new Handler();
        mViewOnFinish = true;
    }

    public void initialize(FragmentManager fragmentManager) {
        mFragmentManager = fragmentManager;
    }

    public void setAccount(String account) {
        mAccount = account;
    }

    public void setMessage(Message message) {
        mMessage = message;
    }

    public void setAttachment(Attachment attachment) {
        mAttachment = attachment;
    }

    public void setViewOnFinish(boolean viewOnFinish) {
        mViewOnFinish = viewOnFinish;
    }

    public void showAttachment(int destination) {
        if (mView == null) {
            return;
        }

        // If the caller requested that this attachments be saved to the external storage, we should
        // verify that the it was saved there.
        if (mAttachment.isPresentLocally() &&
                (destination == AttachmentDestination.CACHE ||
                        mAttachment.destination == destination)) {
            mView.viewAttachment();
        } else {

            startDownloadingAttachment(destination);
        }
    }

    /**
     * Start downloading the full size attachment set with
     * {@link #setAttachment(Attachment)} immediately.
     */
    public void startDownloadingAttachment(int destination) {
        startDownloadingAttachment(destination, UIProvider.AttachmentRendition.BEST, 0, false);
    }

    public void startDownloadingAttachment(
            int destination, int rendition, int additionalPriority, boolean delayDownload) {
        startDownloadingAttachment(
                mAttachment, destination, rendition, additionalPriority, delayDownload);
    }

    public void saveAttachment(int destination){

        if (mAttachment.state == AttachmentState.SAVED
                && destination == AttachmentDestination.EXTERNAL){
            performAttachmentSave(mAttachment);
        }else{
            Toast.makeText(mContext, mContext.getResources().getString(R.string.download_first),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startDownloadingAttachment(
            Attachment attachment, int destination, int rendition, int additionalPriority,
            boolean delayDownload) {
        //do not auto install apk from stream . must save first so that can be install
        if (attachment.state == AttachmentState.SAVED
                && destination == AttachmentDestination.EXTERNAL
                && !MimeType.isInstallable(attachment.getContentType())) {
            File savedFile = performAttachmentSave(attachment);
            if (savedFile != null && mView != null) {
                // The attachment is saved successfully from cache.
                mView.viewAttachment();
                return;
            }
        }

        final ContentValues params = new ContentValues(5);
        params.put(AttachmentColumns.STATE, AttachmentState.DOWNLOADING);
        params.put(AttachmentColumns.DESTINATION, destination);
        params.put(AttachmentContentValueKeys.RENDITION, rendition);
        params.put(AttachmentContentValueKeys.ADDITIONAL_PRIORITY, additionalPriority);
        params.put(AttachmentContentValueKeys.DELAY_DOWNLOAD, delayDownload);

        mCommandHandler.sendCommand(attachment.uri, params);
    }

    public void cancelAttachment() {
        final ContentValues params = new ContentValues(1);
        params.put(AttachmentColumns.STATE, AttachmentState.NOT_SAVED);

        mCommandHandler.sendCommand(mAttachment.uri, params);
    }

    public void startRedownloadingAttachment(Attachment attachment) {
        final ContentValues params = new ContentValues(2);
        params.put(AttachmentColumns.STATE, AttachmentState.REDOWNLOADING);
        params.put(AttachmentColumns.DESTINATION, attachment.destination);

        mCommandHandler.sendCommand(attachment.uri, params);
    }

    /**
     * Displays a loading dialog to be used for downloading attachments.
     * Must be called on the UI thread.
     */
    public void showDownloadingDialog() {
        final FragmentTransaction ft = mFragmentManager.beginTransaction();
        final Fragment prev = mFragmentManager.findFragmentByTag(PROGRESS_FRAGMENT_TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

         // Create and show the dialog.
        final DialogFragment newFragment = AttachmentProgressDialogFragment.newInstance(
                mAttachment);
        newFragment.show(ft, PROGRESS_FRAGMENT_TAG);
    }

    /**
     * Update progress-related views. Will also trigger a view intent if a progress dialog was
     * previously brought up (by tapping 'View') and the download has now finished.
     */
    public void updateStatus(boolean loaderResult) {
        if (mView == null) {
            return;
        }

        final boolean showProgress = mAttachment.shouldShowProgress();

        final AttachmentProgressDialogFragment dialog = (AttachmentProgressDialogFragment)
                mFragmentManager.findFragmentByTag(PROGRESS_FRAGMENT_TAG);
        if (dialog != null && dialog.isShowingDialogForAttachment(mAttachment)) {
            dialog.setProgress(mAttachment.downloadedSize);

            // We don't want the progress bar to switch back to indeterminate mode after
            // have been in determinate progress mode.
            final boolean indeterminate = !showProgress && dialog.isIndeterminate();
            dialog.setIndeterminate(indeterminate);

            if (loaderResult && mAttachment.isDownloadFinishedOrFailed()) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                    }
                });
            }

            if (mAttachment.state == AttachmentState.SAVED && mViewOnFinish) {
                mView.viewAttachment();
            }
        } else {
            mView.updateProgress(showProgress);
        }

        // Call on update status for the view so that it can do some specific things.
        mView.onUpdateStatus();
    }

    public boolean isProgressDialogVisible() {
        final Fragment dialog = mFragmentManager.findFragmentByTag(PROGRESS_FRAGMENT_TAG);
        return dialog != null && dialog.isVisible();
    }

    public void shareAttachment() {
        if (mAttachment.contentUri == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

        final Uri uri = Utils.normalizeUri(mAttachment.contentUri);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setType(Utils.normalizeMimeType(mAttachment.getContentType()));

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

    public static void setOptionHandler(OptionHandler handler) {
        sOptionHandler = handler;
    }

    public boolean shouldShowExtraOption1(final String accountType, final String mimeType) {
        return (sOptionHandler != null) && sOptionHandler.shouldShowExtraOption1(
                accountType, mimeType);
    }

    public void handleOption1() {
        if (sOptionHandler == null) {
            return;
        }
        sOptionHandler.handleOption1(mContext, mAccount, mMessage, mAttachment, mFragmentManager);
    }

    /**
     * A default, no-op option class. Override this and set it globally with
     * {@link AttachmentActionHandler#setOptionHandler(OptionHandler)}.<br>
     * <br>
     * Subclasses of this type will live pretty much forever, so really, really try to avoid
     * keeping any state as member variables in them.
     */
    public static class OptionHandler {

        public boolean shouldShowExtraOption1(String accountType, String mimeType) {
            return false;
        }

        public void handleOption1(Context context, String account, Message message,
                Attachment attachment, FragmentManager fm) {
            // no-op
        }
    }

    private File createUniqueFile(File directory, String filename) throws IOException {
        File file = new File(directory, filename);
        if (file.createNewFile()) {
            return file;
        }
        // Get the extension of the file, if any.
        int index = filename.lastIndexOf('.');
        String format;
        if (index != -1) {
            String name = filename.substring(0, index);
            String extension = filename.substring(index);
            format = name + "-%d" + extension;
        } else {
            format = filename + "-%d";
        }

        for (int i = 2; i < Integer.MAX_VALUE; i++) {
            file = new File(directory, String.format(format, i));
            if (file.createNewFile()) {
                return file;
            }
        }
        return null;
    }

    private File performAttachmentSave(final Attachment attachment) {
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            downloads.mkdirs();
            File file = createUniqueFile(downloads, attachment.getName());
            Uri contentUri = attachment.contentUri;
            InputStream in = mContext.getContentResolver().openInputStream(contentUri);
            OutputStream out = new FileOutputStream(file);
            int size = IOUtils.copy(in, out);
            out.flush();
            out.close();
            in.close();
            String absolutePath = file.getAbsolutePath();
            MediaScannerConnection.scanFile(mContext, new String[] {absolutePath},
                    null, null);
            DownloadManager dm =
                    (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            dm.addCompletedDownload(attachment.getName(), attachment.getName(),
                    false /* do not use media scanner */,
                    attachment.getContentType(), absolutePath, size,
                    true /* show notification */);
            Toast.makeText(mContext,
                    mContext.getResources().getString(R.string.save_to) + absolutePath,
                    Toast.LENGTH_SHORT).show();
            return file;
        } catch (IOException ioe) {
            // Ignore. Callers will handle it from the return code.
        }

        return null;
    }
}
