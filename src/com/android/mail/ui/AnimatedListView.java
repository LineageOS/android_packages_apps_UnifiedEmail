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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

import com.android.mail.providers.Conversation;

import java.util.ArrayList;
import java.util.Collection;

public class AnimatedListView extends ListView implements ActionCompleteListener {
    private ActionCompleteListener mActionCompleteListener;

    public AnimatedListView(Context context) {
        this(context, null);
    }

    public AnimatedListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnimatedListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void delete(Collection<Conversation> conversations) {
        // Animate out the positions.
        // Call when all the animations are complete.
        ArrayList<Integer> positions = new ArrayList<Integer>();
        for (Conversation c : conversations) {
            positions.add(c.position);
        }
        ((AnimatedAdapter)getAdapter()).delete(positions, this);
    }

    public void setOnActionCompleteListener(ActionCompleteListener listener) {
        mActionCompleteListener = listener;
    }

    @Override
    public void onActionComplete() {
        mActionCompleteListener.onActionComplete();
    }
}
