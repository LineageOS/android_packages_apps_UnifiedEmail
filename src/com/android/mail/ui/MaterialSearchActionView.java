/*
 * Copyright (C) 2014 Google Inc.
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

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;

/**
 * Custom view for the action bar when search is displayed.
 */
public class MaterialSearchActionView extends LinearLayout implements TextWatcher,
        View.OnClickListener, TextView.OnEditorActionListener {
    private MaterialSearchViewController mController;
    private InputMethodManager mImm;
    private boolean mShowingClose;
    private boolean mSupportVoice;

    private View mBackButton;
    private EditText mQueryText;
    private ImageView mEndingButton;

    public MaterialSearchActionView(Context context) {
        super(context);
    }

    public MaterialSearchActionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // PUBLIC API
    public void setController(MaterialSearchViewController controller, String initialQuery,
            boolean supportVoice) {
        mController = controller;
        mQueryText.setText(initialQuery);
        mSupportVoice = supportVoice;
    }

    public void clearSearchQuery() {
        mQueryText.setText("");
    }

    public void focusSearchBar(boolean hasFocus) {
        mQueryText.requestFocus();
        if (hasFocus) {
            mImm.showSoftInput(mQueryText, 0);
        } else {
            mImm.hideSoftInputFromWindow(mQueryText.getWindowToken(), 0);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mImm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        mBackButton = findViewById(R.id.search_actionbar_back_button);
        mBackButton.setOnClickListener(this);
        mQueryText = (EditText) findViewById(R.id.search_actionbar_query_text);
        mQueryText.addTextChangedListener(this);
        mQueryText.setOnClickListener(this);
        mQueryText.setOnEditorActionListener(this);
        mEndingButton = (ImageView) findViewById(R.id.search_actionbar_ending_button);
        mEndingButton.setOnClickListener(this);
    }

    @Override
    public void setVisibility(int visibility) {
        if (visibility != VISIBLE) {
            mQueryText.setText("");
        }
        super.setVisibility(visibility);
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        // Only care about onTextChanged
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        mController.onQueryTextChanged(charSequence.toString());
        if (!mSupportVoice || charSequence.length() > 0) {
            mShowingClose = true;
            mEndingButton.setImageResource(R.drawable.ic_close_24dp);
        } else {
            mShowingClose = false;
            mEndingButton.setImageResource(R.drawable.ic_mic_24dp);
        }
    }

    @Override
    public void afterTextChanged(Editable editable) {
        // Only care about onTextChanged
    }

    @Override
    public void onClick(View view) {
        if (view == mBackButton) {
            mController.onSearchCanceled();
            mQueryText.setText("");
        } else if (view == mEndingButton) {
            if (mShowingClose) {
                mQueryText.setText("");
                mController.showSearchActionBar(
                        MaterialSearchViewController.SEARCH_VIEW_STATE_VISIBLE);
            } else {
                mController.onVoiceSearch();
            }
        } else if (view == mQueryText) {
            mController.showSearchActionBar(MaterialSearchViewController.SEARCH_VIEW_STATE_VISIBLE);
        }
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            mController.onSearchPerformed(mQueryText.getText().toString());
        }
        return false;
    }
}
