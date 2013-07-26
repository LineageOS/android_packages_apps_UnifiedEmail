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

package com.android.mail.ads;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationViewAdapter.AdHeaderItem;

public class AdHeaderView extends LinearLayout implements View.OnClickListener {
    private TextView mAdSubjectView;

    private AdHeaderItem mHeaderItem;

    public AdHeaderView(Context context) {
        super(context);
    }

    public AdHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mAdSubjectView = (TextView) findViewById(R.id.ad_subject);
        findViewById(R.id.ad_info).setOnClickListener(this);
    }

    public void setAdSubject(final String subject) {
        mAdSubjectView.setText(subject);
        if (TextUtils.isEmpty(subject)) {
            mAdSubjectView.setVisibility(GONE);
        }
    }

    public void bind(AdHeaderItem headerItem) {
        mHeaderItem = headerItem;
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.ad_info:
                // TODO wire up clicking the ad info button
//                Utils.showAdPreferenceManager(getContext());
                break;
            default:
                break;
        }
    }
}
