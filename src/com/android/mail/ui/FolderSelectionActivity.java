/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Settings;
import com.android.mail.ui.FolderListFragment.FolderListSelectionListener;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.android.mail.widget.WidgetProvider;

/**
 * This activity displays the list of available folders for the current account.
 */
public class FolderSelectionActivity extends Activity implements OnClickListener,
        DialogInterface.OnClickListener, FolderChangeListener, ControllableActivity,
        FolderListSelectionListener {
    public static final String EXTRA_ACCOUNT_SHORTCUT = "account-shortcut";

    private static final String LOG_TAG = LogTag.getLogTag();

    private static final int CONFIGURE = 0;

    private static final int VIEW = 1;

    private Account mAccount;
    private Folder mSelectedFolder;
    private boolean mConfigureShortcut;
    private boolean mConfigureWidget;
    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int mMode = -1;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.folders_activity);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        mConfigureShortcut = Intent.ACTION_CREATE_SHORTCUT.equals(action);
        mConfigureWidget = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(action);
        if (!mConfigureShortcut && !mConfigureWidget) {
            LogUtils.wtf(LOG_TAG, "unexpected intent: %s", intent);
        }
        if (mConfigureShortcut || mConfigureWidget) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.setIcon(R.mipmap.ic_launcher_shortcut_folder);
            }
            mMode = CONFIGURE;
        } else {
            mMode = VIEW;
        }

        if (mConfigureWidget) {
            mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                LogUtils.wtf(LOG_TAG, "invalid widgetId");
            }
        }

        mAccount = intent.getParcelableExtra(EXTRA_ACCOUNT_SHORTCUT);
        Button firstButton = (Button) findViewById(R.id.first_button);
        firstButton.setVisibility(View.VISIBLE);
        // TODO(mindyp) disable the manage folders buttons until we have a manage folders screen.
        if (mMode == VIEW) {
            firstButton.setEnabled(false);
        }
        firstButton.setOnClickListener(this);

        createFolderListFragment(null, mAccount.folderListUri);
    }

    private void createFolderListFragment(Folder parent, Uri uri) {
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        Fragment fragment = FolderListFragment.newInstance(parent, uri);
        fragmentTransaction.replace(R.id.content_pane, fragment);
        fragmentTransaction.commitAllowingStateLoss();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO: (mindyp) Make sure we're operating on the same account as
        // before. If the user switched accounts, switch back.
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.first_button:
                if (mMode == CONFIGURE) {
                    doCancel();
                } else {
                    // TODO (mindyp): open manage folders screen.
                }
                break;
        }
    }

    private void doCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * Create a widget for the specified account and folder
     */
    protected void createWidget(int id, Account account, Folder selectedFolder) {
        WidgetProvider.updateWidget(this, id, account, selectedFolder);
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // The only dialog that is
            createWidget(mAppWidgetId, mAccount, mSelectedFolder);
        } else {
            doCancel();
        }
    }

    @Override
    public void onFolderChanged(Folder folder) {
        if (!folder.equals(mSelectedFolder)) {
            mSelectedFolder = folder;
            Intent resultIntent = new Intent();

            if (mConfigureShortcut) {
                /*
                 * Create the shortcut Intent based on it with the additional
                 * information that we have in this activity: name of the
                 * account, calculate the human readable name of the folder and
                 * use it as the shortcut name, etc...
                 */
                final Intent clickIntent = Utils.createViewFolderIntent(mSelectedFolder,
                        mAccount);
                resultIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, clickIntent);
                resultIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.fromContext(this,
                                R.mipmap.ic_launcher_shortcut_folder));

                CharSequence humanFolderName = mSelectedFolder.name;

                resultIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, humanFolderName);

                // Now ask the user what name they want for this shortcut. Pass
                // the
                // shortcut intent that we just created, the user can modify the
                // folder in
                // ShortcutNameActivity.
                final Intent shortcutNameIntent = new Intent(this, ShortcutNameActivity.class);
                shortcutNameIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY
                        | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                shortcutNameIntent.putExtra(ShortcutNameActivity.EXTRA_FOLDER_CLICK_INTENT,
                        resultIntent);
                shortcutNameIntent.putExtra(ShortcutNameActivity.EXTRA_SHORTCUT_NAME,
                        humanFolderName);

                startActivity(shortcutNameIntent);
                finish();
            } else if (mConfigureWidget) {
                createWidget(mAppWidgetId, mAccount, mSelectedFolder);
            }
        }
    }

    @Override
    public String getHelpContext() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Context getActivityContext() {
        return this;
    }

    @Override
    public ViewMode getViewMode() {
        return null;
    }

    @Override
    public void setViewModeListener(ModeChangeListener listener) {
    }

    @Override
    public void unsetViewModeListener(ModeChangeListener listener) {
    }

    @Override
    public ConversationListCallbacks getListHandler() {
        return null;
    }

    @Override
    public FolderChangeListener getFolderChangeListener() {
        return this;
    }

    @Override
    public Settings getSettings() {
        return null;
    }

    @Override
    public boolean onSearchRequested(String query) {
        return false;
    }

    @Override
    public boolean shouldShowFirstConversation() {
        return false;
    }

    @Override
    public ConversationSelectionSet getSelectedSet() {
        return null;
    }

    @Override
    public void onFolderSelected(Folder folder) {
        if (folder.hasChildren) {
            // Replace this fragment with a new FolderListFragment
            // showing this folder's children if we are not already looking
            // at the child view for this folder.
            createFolderListFragment(folder, folder.childFoldersListUri);
            return;
        }
        onFolderChanged(folder);
    }

    @Override
    public FolderListSelectionListener getFolderListSelectionListener() {
        return this;
    }

    @Override
    public boolean supportsDrag(DragEvent event, Folder folder) {
        return false;
    }

    @Override
    public void handleDrop(DragEvent event, Folder folder) {
        // Do nothing.
    }

    @Override
    public void onUndoAvailable(ToastBarOperation undoOp) {
        // Do nothing.
    }

    @Override
    public void onConversationSeen(Conversation conv) {
        // Do nothing.
    }

    @Override
    public Folder getHierarchyFolder() {
        return null;
    }

    @Override
    public ConversationUpdater getConversationUpdater() {
        return null;
    }

    @Override
    public SubjectDisplayChanger getSubjectDisplayChanger() {
        return null;
    }

    @Override
    public ErrorListener getErrorListener() {
        return null;
    }

    @Override
    public void setPendingToastOperation(ToastBarOperation op) {
        // Do nothing.
    }

    @Override
    public ToastBarOperation getPendingToastOperation() {
        return null;
    }
}
