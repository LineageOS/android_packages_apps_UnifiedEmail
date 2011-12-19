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

package com.android.email.browse;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.email.R;
import com.android.email.ViewMode;

import java.util.ArrayList;

public class ConversationListActivity extends Activity {

    private ListView mListView;
    private BrowseItemAdapter mAdapter;
    private ArrayList<ConversationItemViewModel> mTestBrowseItems =
        new ArrayList<ConversationItemViewModel>();

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_list_activity);
        mListView = (ListView) findViewById(R.id.conversation_list);
        mAdapter = new BrowseItemAdapter(this, R.layout.conversation_item_view_normal);
        ConversationItemViewModel itemOne = new ConversationItemViewModel();
        itemOne.subject = "First";
        itemOne.sendersText = "Mindy, Andy, Paul, Minh";
        itemOne.conversationId = 1;
        itemOne.snippet = "first snippet with several lines of text so that we know "
                + "ellipsizing and everything are working well";
        itemOne.fromSnippetInstructions = "n\n3\n0\n0\nMindy\n0\n2\nAndy\n0\n1\nPaul\n";
        itemOne.checkboxVisible = true;
        mTestBrowseItems.add(itemOne);
        mTestBrowseItems.add(itemOne);
        ConversationItemViewModel itemTwo = new ConversationItemViewModel();
        itemTwo.subject = "Second";
        itemTwo.sendersText = "Mindy, Andy, Paul, Minh";
        itemTwo.conversationId = 2;
        itemTwo.snippet = "second snippet";
        itemTwo.fromSnippetInstructions = "n\n3\n0\n0\nMindy\n0\n2\nAndy\n0\n1\nPaul\n";
        itemTwo.checkboxVisible = true;
        mTestBrowseItems.add(itemTwo);
        mTestBrowseItems.add(itemTwo);
        ConversationItemViewModel itemThree = new ConversationItemViewModel();
        itemThree.subject = "Third";
        itemThree.sendersText = "Mindy, Andy, Paul, Minh";
        itemThree.conversationId = 3;
        itemThree.snippet = "third snippet";
        itemThree.fromSnippetInstructions = "n\n3\n0\n0\nMindy\n0\n2\nAndy\n0\n1\nPaul\n";
        itemThree.checkboxVisible = true;
        mTestBrowseItems.add(itemThree);
        mTestBrowseItems.add(itemThree);
        ConversationItemViewModel itemFour = new ConversationItemViewModel();
        itemFour.subject = "Fourth";
        itemFour.sendersText = "Mindy, Andy, Paul, Minh";
        itemFour.conversationId = 4;
        itemFour.fromSnippetInstructions = "n\n3\n0\n0\nMindy\n0\n2\nAndy\n0\n1\nPaul\n";
        itemFour.snippet = "fourth snippet with several lines of text so that we know "
                + "ellipsizing and everything are working well";
        itemFour.checkboxVisible = true;
        mTestBrowseItems.add(itemFour);
        mTestBrowseItems.add(itemFour);
        mAdapter.addAll(mTestBrowseItems);

        mListView.setAdapter(mAdapter);
    }

    class BrowseItemAdapter extends ArrayAdapter<ConversationItemViewModel> {

        public BrowseItemAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ConversationItemView view =
                new ConversationItemView(getContext(), "test@testaccount.com");
            view.bind(mAdapter.getItem(position), null, "test@testaccount.com", null,
                    new ViewMode(getContext()));
            return view;
        }
    }
}
