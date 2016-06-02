/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.mail.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.android.emailcommon.service.SearchParams;
import com.android.mail.R;

public class MaterialSearchFactorSelecteView extends LinearLayout implements
        RadioGroup.OnCheckedChangeListener {

    private RadioGroup mFactorGroup;
    private Context mContext;
    private int[] mFactorIds = {
            R.id.check_factor_all, R.id.check_factor_subject, R.id.check_factor_sender,
            R.id.check_factor_receiver
    };

    private MaterialSearchViewController mController;
    public final static int FACTOR_ALL_ID = R.id.check_factor_all;

    public MaterialSearchFactorSelecteView(Context context) {
        this(context, null);
    }

    public MaterialSearchFactorSelecteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    // PUBLIC API
    public void setController(MaterialSearchViewController controller) {
        mController = controller;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mFactorGroup = (RadioGroup) this.findViewById(R.id.group_check_factor);
        mFactorGroup.check(R.id.check_factor_all);
        mFactorGroup.setOnCheckedChangeListener(this);
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

        changeCheckRadioButton(getFactor(checkedId), true);
    }

    public void changeCheckRadioButton(String factor, boolean isUser) {
        for (int factorId : mFactorIds) {
            RadioButton filterBtn = (RadioButton) this.findViewById(factorId);
            if (factorId == mFactorGroup.getCheckedRadioButtonId()) {
                filterBtn.setTextColor(Color.WHITE);
                filterBtn.setBackgroundColor(mContext.getResources().getColor(
                        R.color.search_factor_selector_color));
            } else {
                filterBtn.setTextColor(mContext.getResources().getColor(
                        R.color.search_factor_text_normal_color));
                filterBtn.setBackgroundColor(mContext.getResources().getColor(
                        R.color.search_factor_normal_color));
            }
        }
        changeFactorAction(factor, isUser);
    }

    private void changeFactorAction(String factor, boolean isUser) {
        if (factor != null) {
            mController.changeFactorAction(factor, isUser);
        }
    }

    public void checkAllFactor() {
        if (mFactorGroup.getCheckedRadioButtonId() != FACTOR_ALL_ID) {
            mFactorGroup.check(FACTOR_ALL_ID);
        } else {
            changeFactorAction(SearchParams.SEARCH_FACTOR_ALL, false);
        }
    }

    public int getCheckRadioButtonId() {
        return mFactorGroup.getCheckedRadioButtonId();
    }

    public String getFactor(int checkedId) {
        String factor = null;
        switch (checkedId) {
            case R.id.check_factor_all:
                factor = SearchParams.SEARCH_FACTOR_ALL;
                break;
            case R.id.check_factor_subject:
                factor = SearchParams.SEARCH_FACTOR_SUBJECT;
                break;
            case R.id.check_factor_sender:
                factor = SearchParams.SEARCH_FACTOR_SENDER;
                break;
            case R.id.check_factor_receiver:
                factor = SearchParams.SEARCH_FACTOR_RECEIVER;
                break;
            default:
                break;
        }
        return factor;
    }
}
