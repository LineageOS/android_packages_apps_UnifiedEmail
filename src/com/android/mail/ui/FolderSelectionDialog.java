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

import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;

import com.android.mail.utils.LogUtils;

public abstract class FolderSelectionDialog implements OnDismissListener {
    private static boolean sDialogShown;

    protected static boolean isShown() {
        return sDialogShown;
    }

    public static void setDialogDismissed() {
        LogUtils.d("Gmail", "Folder Selection dialog dismissed");
        sDialogShown = false;
    }

    public void show() {
        FolderSelectionDialog.sDialogShown = true;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        FolderSelectionDialog.setDialogDismissed();
    }
}
