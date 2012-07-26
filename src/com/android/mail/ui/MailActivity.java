/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui;

import android.app.Dialog;
import android.content.Intent;
import android.database.DataSetObserver;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.ActionMode;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;

import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.ui.FolderListFragment.FolderListSelectionListener;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.Utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * This is the root activity container that holds the left navigation fragment
 * (usually a list of folders), and the main content fragment (either a
 * conversation list or a conversation view).
 */
public class MailActivity extends AbstractMailActivity implements ControllableActivity {
    // TODO(viki) This class lacks: Conversation Position tracking
    // TODO(viki) This class lacks: What's New dialog
    // TODO(viki) This class lacks: Sync Window Upgrade dialog

    private static final boolean STRICT_MODE = true;
    private NfcAdapter mNfcAdapter; // final after onCreate
    private NdefMessage mForegroundNdef;

    /**
     * The activity controller to which we delegate most Activity lifecycle events.
     */
    private ActivityController mController;
    /**
     * A clean launch is when the activity is not resumed. We want to show a "What's New" dialog
     * on a clean launch: when the user started the Activity by tapping on the icon: not when he
     * selected "Up" from compose, not when he resumed the activity, etc.
     */
    private boolean mLaunchedCleanly = false;

    private ViewMode mViewMode;

    private ToastBarOperation mPendingToastOp;
    private static MailActivity sForegroundInstance;

    public MailActivity() {
        super();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mController.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Default implementation returns a null view mode.
     */
    @Override
    public ViewMode getViewMode() {
        return mViewMode;
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mController.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (!mController.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
//                    .penaltyDeath()
                    .build());
        }
        super.onCreate(savedState);

        mViewMode = new ViewMode(this);
        final boolean tabletUi = Utils.useTabletUI(this);
        mController = ControllerFactory.forActivity(this, mViewMode, tabletUi);
        mController.onCreate(savedState);

        Intent intent = getIntent();
        // Only display "What's New" and similar dialogs on a clean launch.
        // A clean launch is one where the activity is not resumed.
        // We also want to avoid showing any dialogs when the user goes "up"
        // from a compose
        // activity launched directly from a send-to intent. (in that case the
        // action is null.)
        if (savedState == null && intent.getAction() != null) {
            mLaunchedCleanly = true;
        }
        setupNfc();
    }

    private void setupNfc() {
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    /**
     * Sets an NDEF message to be shared with "zero-clicks" using NFC. The message
     * will be available as long as the current activity is in the foreground.
     */
    public static void setForegroundNdef(NdefMessage ndef) {
        MailActivity foreground = sForegroundInstance;
        if (foreground != null && foreground.mNfcAdapter != null) {
            synchronized (foreground) {
                foreground.mForegroundNdef = ndef;
                if (sForegroundInstance != null) {
                    if (ndef != null) {
                        sForegroundInstance.mNfcAdapter.enableForegroundNdefPush(
                                sForegroundInstance, ndef);
                    } else {
                        sForegroundInstance.mNfcAdapter.disableForegroundNdefPush(
                                sForegroundInstance);
                    }
                }
            }
        }
    }

    /**
     * Returns an NDEF message with a single mailto URI record
     * for the given email address.
     */
    public static NdefMessage getMailtoNdef(String account) {
        byte[] accountBytes;
        try {
            accountBytes = URLEncoder.encode(account, "UTF-8").getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            accountBytes = account.getBytes();
        }
        byte prefix = 0x06; // mailto:
        byte[] recordBytes = new byte[accountBytes.length + 1];
        recordBytes[0] = prefix;
        System.arraycopy(accountBytes, 0, recordBytes, 1, accountBytes.length);
        NdefRecord mailto = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_URI,
                new byte[0], recordBytes);
        return new NdefMessage(new NdefRecord[] { mailto });
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mController.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        Dialog dialog = mController.onCreateDialog(id, bundle);
        // TODO(viki): Handle what's new and the sync window upgrade dialog here.
        return dialog == null ? super.onCreateDialog(id, bundle) : dialog;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return mController.onCreateOptionsMenu(menu) || super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mController.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mController.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        mController.onPause();
        synchronized (this) {
            if (mNfcAdapter != null && mForegroundNdef != null) {
                mNfcAdapter.disableForegroundNdefPush(this);
            }
            sForegroundInstance = null;
        }
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        super.onPrepareDialog(id, dialog, bundle);
        mController.onPrepareDialog(id, dialog, bundle);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return mController.onPrepareOptionsMenu(menu) || super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onResume() {
        super.onResume();
        mController.onResume();
        synchronized (this) {
            sForegroundInstance = this;
            if (mNfcAdapter != null && mForegroundNdef != null) {
                mNfcAdapter.enableForegroundNdefPush(this, mForegroundNdef);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mController.onSaveInstanceState(outState);
    }

    @Override
    public boolean onSearchRequested(String query) {
        mController.onSearchRequested(query);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mLaunchedCleanly) {
            // TODO(viki): Show a "what's new screen"
        }
    }

    @Override
    public boolean onSearchRequested() {
        mController.startSearch();
        return true;
    }

    @Override
    public void onStop() {
        super.onStop();
        mController.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mController.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mController.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void setViewModeListener(ModeChangeListener listener) {
        mViewMode.addListener(listener);
    }

    @Override
    public void unsetViewModeListener(ModeChangeListener listener) {
        mViewMode.removeListener(listener);
    }

    @Override
    public ConversationListCallbacks getListHandler() {
        return mController;
    }

    @Override
    public FolderChangeListener getFolderChangeListener() {
        return mController;
    }

    @Override
    public FolderListSelectionListener getFolderListSelectionListener() {
        return mController;
    }

    @Override
    public FolderController getFolderController() {
        return mController;
    }

    @Override
    public Settings getSettings() {
        return mController.getSettings();
    }

    @Override
    public boolean shouldShowFirstConversation() {
        return mController.shouldShowFirstConversation();
    }

    @Override
    public ConversationSelectionSet getSelectedSet() {
        return mController.getSelectedSet();
    }

    @Override
    public boolean supportsDrag(DragEvent event, Folder folder) {
        return mController.supportsDrag(event, folder);
    }

    @Override
    public void handleDrop(DragEvent event, Folder folder) {
        mController.handleDrop(event, folder);
    }

    @Override
    public void onUndoAvailable(ToastBarOperation undoOp) {
        mController.onUndoAvailable(undoOp);
    }

    @Override
    public void onConversationSeen(Conversation conv) {
        mController.onConversationSeen(conv);
    }

    @Override
    public Folder getHierarchyFolder() {
        return mController.getHierarchyFolder();
    }

    @Override
    public ConversationUpdater getConversationUpdater() {
        return mController;
    }

    @Override
    public SubjectDisplayChanger getSubjectDisplayChanger() {
        return mController.getSubjectDisplayChanger();
    }

    @Override
    public ErrorListener getErrorListener() {
        return mController;
    }

    @Override
    public void setPendingToastOperation(ToastBarOperation op) {
        mPendingToastOp = op;
    }

    @Override
    public ToastBarOperation getPendingToastOperation() {
        return mPendingToastOp;
    }
}
