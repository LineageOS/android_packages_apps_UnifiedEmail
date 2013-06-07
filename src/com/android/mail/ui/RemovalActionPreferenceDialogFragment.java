/*
 * Copyright (C) 2013 Google Inc.
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
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;

import com.android.mail.R;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider.AccountCapabilities;

import java.lang.ref.WeakReference;

/**
 * This dialog is shown when a user first archives or deletes a message, and asks them what there
 * preferred action is (archive, delete, or both).
 */
public class RemovalActionPreferenceDialogFragment extends DialogFragment {
    /**
     * A listener that can be attached to a {@link RemovalActionPreferenceDialogFragment} to receive
     * onDismiss() events.
     */
    public interface RemovalActionPreferenceDialogListener {
        void onDismiss();
    }

    public static final String FRAGMENT_TAG = "ArchiveDeletePreferenceDialogFragment";

    private static final String ARG_DEFAULT_VALUE = "defaultValue";

    private String mDefaultValue;

    private WeakReference<RemovalActionPreferenceDialogListener> mListener = null;

    /**
     * Create a new {@link ArchiveDeletePreferenceDialogFragment}.
     *
     * @param defaultValue the initial value to show as checked
     */
    public static RemovalActionPreferenceDialogFragment newInstance(final String defaultValue) {
        final RemovalActionPreferenceDialogFragment fragment =
                new RemovalActionPreferenceDialogFragment();

        final Bundle args = new Bundle(1);
        args.putString(ARG_DEFAULT_VALUE, defaultValue);
        fragment.setArguments(args);

        return fragment;
    }

    public void setListener(final RemovalActionPreferenceDialogListener listener) {
        mListener = new WeakReference<RemovalActionPreferenceDialogListener>(listener);
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final String[] entries = getResources().getStringArray(R.array.prefEntries_removal_action);
        final String[] entryValues =
                getResources().getStringArray(R.array.prefValues_removal_action);

        final String defaultValue = getArguments().getString(ARG_DEFAULT_VALUE);

        // Find the default value in the entryValues array
        // If we can't find it, we end up on index 0, which is "archive"
        int defaultItem;
        for (defaultItem = entryValues.length - 1; defaultItem >= 0; defaultItem--) {
            if (entryValues[defaultItem].equals(defaultValue)) {
                break;
            }
        }

        mDefaultValue = entryValues[defaultItem];

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.prefDialogTitle_removal_action_first_time)
                .setSingleChoiceItems(entries, defaultItem, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        mDefaultValue = entryValues[which];
                        dismiss();
                    }
                });

        return builder.create();
    }

    @Override
    public void onDismiss(final DialogInterface dialog) {
        super.onDismiss(dialog);

        // Use whatever was originally selected
        saveRemovalAction(mDefaultValue);
    }

    /**
     * Shows the dialog, if necessary.
     *
     * @param account The account this is being requested for
     * @param mRemovalActionPreferenceDialogListener
     * @param defaultValue The default removal action to show checked
     * @return <code>true</code> if the dialog was shown, <code>false</code> otherwise
     */
    public static boolean showIfNecessary(final Context context, final Account account,
            final FragmentManager fragmentManager,
            final RemovalActionPreferenceDialogListener removalActionPreferenceDialogListener) {
        final boolean supportsArchive = account.supportsCapability(AccountCapabilities.ARCHIVE);

        if (shouldDisplayDialog(context, account, supportsArchive)) {
            final String defaultValue = MailPrefs.get(context).getRemovalAction(supportsArchive);

            final RemovalActionPreferenceDialogFragment fragment = newInstance(defaultValue);
            fragment.setListener(removalActionPreferenceDialogListener);
            fragment.show(fragmentManager, FRAGMENT_TAG);

            return true;
        }

         return false;
    }

    /**
     * Checks whether we should show the dialog. We show it if:
     * <ol>
     * <li>Archive is supported by the account</li>
     * <li>We have not previously shown the dialog</li>
     * </ol>
     *
     * @param account The account this is being requested for
     * @param supportsArchive <code>true</code> if the current account supports
     *            archive, <code>false</code> otherwise
     * @return <code>true</code> if the dialog needs to be displayed (because it
     *         hasn't been shown yet), <code>false</code> otherwise
     */
    private static boolean shouldDisplayDialog(final Context context, final Account account,
            final boolean supportsArchive) {
        return supportsArchive && !MailPrefs.get(context).hasRemovalActionDialogShown();
    }

    private void saveRemovalAction(final String removalAction) {
        final MailPrefs mailPrefs = MailPrefs.get(getActivity());
        mailPrefs.setRemovalAction(removalAction);
        mailPrefs.setRemovalActionDialogShown();

        if (mListener != null) {
            final RemovalActionPreferenceDialogListener listener = mListener.get();
            if (listener != null) {
                listener.onDismiss();
            }
        }
    }
}
