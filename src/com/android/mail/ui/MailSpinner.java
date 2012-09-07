/**
 * Copyright (c) 2012, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import com.android.mail.AccountSpinnerAdapter;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

public class MailSpinner extends FrameLayout implements OnItemClickListener, OnClickListener {
    private static final String LOG_TAG = LogTag.getLogTag();
    private ListPopupWindow mListPopupWindow;
    private AccountSpinnerAdapter mSpinnerAdapter;
    private Account mAccount;
    private Folder mFolder;
    private ActivityController mController;
    private TextView mAccountName;
    private TextView mFolderName;
    private TextView mFolderCount;

    public MailSpinner(Context context) {
        this(context, null);
    }

    public MailSpinner(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public MailSpinner(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setOnClickListener(this);
        mListPopupWindow = new ListPopupWindow(context);
        mListPopupWindow.setOnItemClickListener(this);
        mListPopupWindow.setAnchorView(this);
        int dropDownWidth = context.getResources().getDimensionPixelSize(
                R.dimen.account_dropdown_dropdownwidth);
        mListPopupWindow.setWidth(dropDownWidth);
        mListPopupWindow.setModal(true);
        addView(LayoutInflater.from(getContext()).inflate(R.layout.account_switch_spinner_item,
                null));
        mAccountName = (TextView)findViewById(R.id.account_second);
        mFolderName = (TextView)findViewById(R.id.account_first);
        mFolderCount = (TextView) findViewById(R.id.account_unread);
    }

    public void setAdapter(AccountSpinnerAdapter adapter) {
        mSpinnerAdapter = adapter;
        mListPopupWindow.setAdapter(mSpinnerAdapter);
    }

    public void setAccount(Account account) {
        mAccount = account;
        if (mAccount != null) {
            mAccountName.setText(mAccount.name);
        }
    }

    public void setFolder(Folder f) {
        mFolder = f;
        if (mFolder != null) {
            mFolderName.setText(mFolder.name);
            mFolderCount.setText(Utils.getUnreadCountString(getContext(),
                    Utils.getFolderUnreadDisplayCount(mFolder)));

            if (mSpinnerAdapter != null) {
                // Update the spinner with this current folder, as it could change the recent items
                // that are shown.
                mSpinnerAdapter.setCurrentFolder(mFolder);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        LogUtils.d(LOG_TAG, "onNavigationItemSelected(%d, %d) called", position, id);
        final int type = mSpinnerAdapter.getType(position);
        boolean dismiss = false;
        switch (type) {
            case AccountSpinnerAdapter.TYPE_DEAD_HEADER:
                // Automatic selections
                LogUtils.d(LOG_TAG, "Got a tap on the dead header");
                break;
            case AccountSpinnerAdapter.TYPE_ACCOUNT:
                // Get the capabilities associated with this account.
                final Account account = (Account) mSpinnerAdapter.getItem(position);
                LogUtils.d(LOG_TAG, "onNavigationItemSelected: Selecting account: %s",
                        account.name);
                if (mAccount.uri.equals(account.uri)) {
                    // The selected account is the same, let's load the default inbox.
                    mController.loadAccountInbox();
                } else {
                    // Switching accounts.
                    mController.onAccountChanged(account);
                }
                dismiss = true;
                break;
            case AccountSpinnerAdapter.TYPE_FOLDER:
                final Object folder = mSpinnerAdapter.getItem(position);
                assert (folder instanceof Folder);
                LogUtils.d(LOG_TAG, "onNavigationItemSelected: Selecting folder: %s",
                        ((Folder)folder).name);
                mController.onFolderChanged((Folder) folder);
                dismiss = true;
                break;
            case AccountSpinnerAdapter.TYPE_ALL_FOLDERS:
                mController.showFolderList();
                dismiss = true;
                break;
        }
        if (dismiss) {
            mListPopupWindow.dismiss();
        }
    }

    public void setController(ActivityController controller) {
        mController = controller;
    }

    @Override
    public void onClick(View arg0) {
        if (!mListPopupWindow.isShowing()) {
            mListPopupWindow.show();
        }
    }

    public void dismiss() {
        mListPopupWindow.dismiss();
    }

    public void onFolderUpdated(Folder folder) {
        mSpinnerAdapter.onFolderUpdated(folder);
        setFolder(folder);
    }
}
