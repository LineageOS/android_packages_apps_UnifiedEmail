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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.android.mail.R;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.utils.Utils;

/**
 * Implements a {@link DialogFragment} that uses an internal {@link AlertDialog} to show information
 * for first time users or new upgrades.
 */
public class WhatsNewDialogFragment extends DialogFragment {
    /**
     * An interface that must be implemented by classes that display {@link WhatsNewDialogFragment}.
     */
    public interface WhatsNewDialogLauncher {
        /**
         * Displays the What's New dialog.
         */
        void showWhatsNewDialog();
    }

    /**
     * A listener for events on the {@link WhatsNewDialogFragment}.
     */
    public interface WhatsNewDialogListener {
        /**
         * Called when the layout for the dialog is inflated. This is where any customization should
         * be done, and listeners on subviews should be set.
         *
         * @param view The root {@link View} in R.layout.whats_new_dialog
         */
        void onWhatsNewDialogLayoutInflated(View view);
    }

    public static final String FRAGMENT_TAG = "WhatsNewDialogFragment";

    private WhatsNewDialogListener mCallback = null;

    public static WhatsNewDialogFragment newInstance() {
        final WhatsNewDialogFragment fragment = new WhatsNewDialogFragment();
        return fragment;
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        try {
            mCallback = (WhatsNewDialogListener) activity;
        } catch (final ClassCastException e) {
            throw new ClassCastException(
                    activity.getClass().getSimpleName() + " must implement WhatsNewDialogListener");
        }
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Context context = getActivity().getApplicationContext();

        final View view =
                LayoutInflater.from(getActivity()).inflate(R.layout.whats_new_dialog, null);

        mCallback.onWhatsNewDialogLayoutInflated(view);

        return new AlertDialog.Builder(getActivity()).setTitle(R.string.whats_new_dialog_title)
                .setView(view)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int whichButton) {
                        final int version = Utils.getVersionCode(context);
                        if (version != -1) {
                            MailPrefs.get(context).setHasShownWhatsNew(version);
                        }

                        dialog.dismiss();
                    }
                }).create();
    }

    @Override
    public void onCancel(final DialogInterface dialog) {
        super.onCancel(dialog);

        final int version = Utils.getVersionCode(getActivity());
        if (version != -1) {
            MailPrefs.get(getActivity()).setHasShownWhatsNew(version);
        }
    }
}
