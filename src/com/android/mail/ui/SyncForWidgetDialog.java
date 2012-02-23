// Copyright 2012 Google Inc. All Rights Reserved.

package com.android.mail.ui;

import android.content.res.Resources;
import android.view.View;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.widget.CheckBox;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * A dialog for users attempting to create a widget for a non-synced label.
 */
public class SyncForWidgetDialog extends AlertDialog
        implements DialogInterface.OnClickListener {

    private static final long SYNC_DAYS = 30;
    private final Account mAccount;
    private final Folder mFolder;

    private final DialogInterface.OnClickListener mConfirmClickListener;

    public SyncForWidgetDialog(Context context, Account account, Folder folder,
                DialogInterface.OnClickListener confirmWidgetCreationListener) {
        super(context);

        mAccount = account;
        mFolder = folder;
        mConfirmClickListener = confirmWidgetCreationListener;
        final ContentResolver resolver = context.getContentResolver();

        // Get the current sync window for the specified account
        final Cursor settings = mAccount.getSettings();
        // TODO: get sync days from settings.
        final long syncDays = SYNC_DAYS;

        final Resources res = context.getResources();

        setTitle(R.string.folder_sync_for_widget_title);
        setIcon(res.getDrawable(R.mipmap.ic_launcher_mail));
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
        setButton(DialogInterface.BUTTON_NEGATIVE,
                context.getString(android.R.string.cancel), this);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final View dialogContents = inflater.inflate(R.layout.folder_sync_for_widget_dialog, null);
        final CheckBox checkboxView =
                (CheckBox) dialogContents.findViewById(R.id.folder_sync_for_widget_confirm);
        checkboxView.setText(res.getString(R.string.folder_sync_for_widget_checkbox, syncDays));
        setView(dialogContents);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            CheckBox confirmView = (CheckBox) findViewById(R.id.folder_sync_for_widget_confirm);

            if (confirmView.isChecked()) {

                AsyncTask<Context, Void, Void> enableLabelSyncTask =
                        new AsyncTask<Context, Void, Void>() {

                    @Override
                    protected Void doInBackground(Context... params) {
                        final Context context = params[0];
                        final Cursor settings = mAccount.getSettings();
                        // TODO: (mindyp) enable syncing of folder.

                        return null;
                    }

                };
                enableLabelSyncTask.execute(getContext());
            }
        }

        // Call the activity the created this widget, to let them know that the user pressed OK
        mConfirmClickListener.onClick(dialog, which);
    }

}
