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
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.android.mail.widget.WidgetProvider;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * This activity displays the list of available folders for the current account.
 */
public class FolderSelectionActivity extends Activity implements OnClickListener,
        DialogInterface.OnClickListener, FolderChangeListener {
    public static final String EXTRA_ACCOUNT_SHORTCUT = "account-shortcut";

    private static final String LOG_TAG = new LogUtils().getLogTag();

    private Account mAccount;
    private Folder mSelectedFolder;
    private boolean mConfigureShortcut;
    private boolean mConfigureWidget;
    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

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
        }

        if (mConfigureWidget) {
            mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                LogUtils.wtf(LOG_TAG, "invalid widgetId");
            }
        }

        mAccount = intent.getParcelableExtra(EXTRA_ACCOUNT_SHORTCUT);
        Button cancelButton = (Button) findViewById(R.id.cancel);
        cancelButton.setVisibility(View.VISIBLE);
        cancelButton.setOnClickListener(this);

        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        Fragment fragment = FolderListFragment.newInstance(this, mAccount.folderListUri,
                FolderListFragment.MODE_PICK);
        fragmentTransaction.replace(R.id.folders_pane, fragment);
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
            case R.id.cancel:
                doCancel();
                break;
        }
    }

    private void doCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        Dialog dialog = null;
        switch (id) {
            case R.layout.folder_sync_for_widget_dialog:
                dialog = new SyncForWidgetDialog(this, mAccount, mSelectedFolder, this);
                break;
        }
        return dialog == null ? super.onCreateDialog(id, bundle) : dialog;
    }

    /**
     * Create a widget for the specified account and label
     */
    private void createWidget() {
        WidgetProvider.updateWidget(this, mAppWidgetId, mAccount, mSelectedFolder);
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // The only dialog that is
            createWidget();
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
                 * account, calculate the human readable name of the label and
                 * use it as the shortcut name, etc...
                 */
                final Intent clickIntent = Utils.createViewConversationIntent(this, mAccount,
                        mSelectedFolder, UIProvider.INVALID_CONVERSATION_ID);
                resultIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, clickIntent);
                resultIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.fromContext(this,
                                R.mipmap.ic_launcher_shortcut_folder));

                CharSequence humanLabelName = mSelectedFolder.name;

                resultIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, humanLabelName);

                // Now ask the user what name they want for this shortcut. Pass
                // the
                // shortcut intent that we just created, the user can modify the
                // label in
                // ShortcutNameActivity.
                final Intent shortcutNameIntent = new Intent(this, ShortcutNameActivity.class);
                shortcutNameIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY
                        | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                shortcutNameIntent.putExtra(ShortcutNameActivity.EXTRA_LABEL_CLICK_INTENT,
                        resultIntent);
                shortcutNameIntent.putExtra(ShortcutNameActivity.EXTRA_SHORTCUT_NAME,
                        humanLabelName);

                startActivity(shortcutNameIntent);
                finish();
            } else if (mConfigureWidget) {
                // Check to see if the widget is set to be synchronized
                final Cursor settings = mAccount.getSettings();
                final Set<String> synchronizedLabelsSet = Sets.newHashSet();

                // Add all of the synchronized labels to the set
                // TODO: (mindyp) deal with labels.
                // synchronizedLabelsSet.addAll(settings.getLabelsIncluded());
                // synchronizedLabelsSet.addAll(settings.getLabelsPartial());

                if (!synchronizedLabelsSet.contains(mSelectedFolder.name)) {
                    // Display a dialog offering to enable sync for this label
                    showDialog(R.layout.folder_sync_for_widget_dialog);
                } else {
                    createWidget();
                }
            }
        }
    }
}
