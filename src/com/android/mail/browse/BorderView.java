/**
 * Copyright (c) 2013, Google Inc.
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

package com.android.mail.browse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.android.mail.browse.ConversationViewAdapter.BorderItem;

import com.android.mail.R;

public class BorderView extends LinearLayout {

    private View mCardBottom;
    private View mBorderSpace;

    public BorderView(Context context) {
        this(context, null);
    }

    public BorderView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public BorderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mCardBottom = findViewById(R.id.card_bottom);
        mBorderSpace = findViewById(R.id.border_space);
    }

    public void bind(BorderItem borderItem, boolean measureOnly) {
        mCardBottom.setVisibility(borderItem.isFirstBorder() ? GONE : VISIBLE);
    }
}
