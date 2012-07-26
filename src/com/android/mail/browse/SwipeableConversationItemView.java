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

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.ui.AnimatedAdapter;
import com.android.mail.ui.ConversationSelectionSet;
import com.android.mail.ui.ViewMode;

public class SwipeableConversationItemView extends FrameLayout {

    private ConversationItemView mConversationItemView;
    private TextView mBackground;

    public SwipeableConversationItemView(Context context, String account) {
        super(context);
        mConversationItemView = new ConversationItemView(context, account);
        addView(mConversationItemView);
    }

    public void addBackground(Context context, int textRes) {
        addBackground(context, context.getResources().getString(textRes));
    }

    public void addBackground(Context context, String text) {
        mBackground = (TextView) findViewById(R.id.background);
        if (mBackground == null) {
            mBackground = (TextView) LayoutInflater.from(context).inflate(R.layout.background,
                    null, true);
            addView(mBackground, 0);
        }
        mBackground.setText(text);
    }

    public void setBackgroundVisibility(int visibility) {
        if (mBackground != null) {
            mBackground.setVisibility(visibility);
        }
    }

    public ListView getListView() {
        return (ListView) getParent();
    }

    public void reset() {
        setBackgroundVisibility(View.GONE);
        mConversationItemView.setAlpha(1);
        mConversationItemView.setTranslationX(0);
    }

    public ConversationItemView getSwipeableItemView() {
        return mConversationItemView;
    }

    public void bind(Conversation conversation, ViewMode viewMode, ConversationSelectionSet set,
            Folder folder, boolean checkboxesDisabled, boolean swipeEnabled,
            AnimatedAdapter animatedAdapter) {
        mConversationItemView.bind(conversation, viewMode, set, folder, checkboxesDisabled,
                swipeEnabled, animatedAdapter);
    }

    public void bind(Cursor cursor, ViewMode viewMode, ConversationSelectionSet set, Folder folder,
            boolean checkboxesDisabled, boolean swipeEnabled, AnimatedAdapter animatedAdapter) {
        mConversationItemView.bind(cursor, viewMode, set, folder, checkboxesDisabled, swipeEnabled,
                animatedAdapter);
    }

    public void startUndoAnimation(int actionText, ViewMode viewMode, AnimatedAdapter listener,
            boolean swipe) {
        if (swipe) {
            addBackground(getContext(), actionText);
            setBackgroundVisibility(View.VISIBLE);
            mConversationItemView.startSwipeUndoAnimation(viewMode, listener);
        } else {
            setBackgroundVisibility(View.GONE);
            mConversationItemView.startUndoAnimation(viewMode, listener);
        }

    }
}
