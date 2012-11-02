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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;

import com.android.mail.R;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountColumns;

public class AutoFitPromptDialogFragment extends DialogFragment {
    public static final String FRAGMENT_TAG = "AutoFitPromptDialogFragment";

    private static final String ARG_UPDATE_SETTINGS_URI = "updateSettingsUri";

    public static AutoFitPromptDialogFragment newInstance(final Uri updateSettingsUri) {
        final AutoFitPromptDialogFragment fragment = new AutoFitPromptDialogFragment();

        final Bundle args = new Bundle(1);
        args.putParcelable(ARG_UPDATE_SETTINGS_URI, updateSettingsUri);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity()).setTitle(
                R.string.auto_fit_messages_dialog_title)
                .setMessage(R.string.auto_fit_messages_dialog_message)
                .setPositiveButton(R.string.yes, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        // Change the setting to enable auto-fit
                        saveAutoFitSetting(UIProvider.ConversationViewMode.OVERVIEW);
                    }
                })
                .setNegativeButton(R.string.no, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        // Change the setting to disable auto-fit
                        saveAutoFitSetting(UIProvider.ConversationViewMode.READING);
                    }
                })
                .create();
    }

    @Override
    public void onCancel(final DialogInterface dialog) {
        super.onCancel(dialog);

        // Change the setting to disable auto-fit
        saveAutoFitSetting(UIProvider.ConversationViewMode.READING);
    }

    private void saveAutoFitSetting(final int conversationViewMode) {
        final Context context = getActivity();

        if (context == null) {
            return;
        }

        final ContentValues values = new ContentValues(1);
        values.put(AccountColumns.SettingsColumns.CONVERSATION_VIEW_MODE, conversationViewMode);

        final ContentResolver resolver = context.getContentResolver();
        resolver.update(
                (Uri) getArguments().getParcelable(ARG_UPDATE_SETTINGS_URI), values, null, null);
    }
}
