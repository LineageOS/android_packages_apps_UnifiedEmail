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
import android.app.FragmentManager;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;

import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider.AttachmentDestination;

public class MessageLoadMoreBar extends FrameLayout implements OnClickListener,
    AttachmentViewInterface {

    private final AttachmentActionHandler mActionHandler;
    private DialogFragment mDialog;
    private Handler mHandler;

    public MessageLoadMoreBar(Context context) {
        this(context, null);
    }

    public MessageLoadMoreBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        mActionHandler = new AttachmentActionHandler(context, this);
        mHandler = new Handler();
    }

    public void initialize(FragmentManager fragmentManager) {
        mActionHandler.initialize(fragmentManager);
    }

    public void setAttachment(Attachment attachment) {
        mActionHandler.setAttachment(attachment);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (mDialog != null) {
            mDialog.dismiss();
        }
        mDialog = mActionHandler.showDownloadingDialog(true);
        mActionHandler.setViewOnFinish(false);
        mActionHandler.startDownloadingAttachment(AttachmentDestination.CACHE);
    }

    public void onMessageLoaded() {
        // This method is invoked from the onLoadFinished method, so it's must be
        // called in the uithread to dismiss the dialog
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mDialog != null) {
                    mDialog.dismiss();
                }
                mDialog = null;
            }
        });
    }

    @Override
    public void viewAttachment() {
    }

    @Override
    public void updateProgress(boolean showDeterminateProgress) {
    }

    @Override
    public void onUpdateStatus() {
    }

}
