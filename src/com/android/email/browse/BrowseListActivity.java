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

public class BrowseListActivity extends Activity {

    private ListView mListView;
    private BrowseItemAdapter mAdapter;
    private ArrayList<BrowseItemViewModel> mTestBrowseItems = new ArrayList<BrowseItemViewModel>();

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.browse_list_activity);
        mListView = (ListView) findViewById(R.id.browse_list);
        mAdapter = new BrowseItemAdapter(this, R.layout.browse_item_view_normal);
        BrowseItemViewModel itemOne = new BrowseItemViewModel();
        itemOne.subject = "First";
        itemOne.sendersText = "Mindy, Andy, Paul, Minh";
        itemOne.conversationId = 1;
        itemOne.snippet = "first snippet";
        itemOne.fromSnippetInstructions = "n\n3\n0\n0\nMindy\n0\n2\nAndy\n0\n1\nPaul\n";
        itemOne.checkboxVisible = true;
        mTestBrowseItems.add(itemOne);
        mTestBrowseItems.add(itemOne);
        BrowseItemViewModel itemTwo = new BrowseItemViewModel();
        itemTwo.subject = "Second";
        itemTwo.sendersText = "Mindy, Andy, Paul, Minh";
        itemTwo.conversationId = 2;
        itemTwo.snippet = "second snippet";
        itemTwo.fromSnippetInstructions = "n\n3\n0\n0\nMindy\n0\n2\nAndy\n0\n1\nPaul\n";
        itemTwo.checkboxVisible = true;
        mTestBrowseItems.add(itemTwo);
        mTestBrowseItems.add(itemTwo);
        BrowseItemViewModel itemThree = new BrowseItemViewModel();
        itemThree.subject = "Third";
        itemThree.sendersText = "Mindy, Andy, Paul, Minh";
        itemThree.conversationId = 3;
        itemThree.snippet = "third snippet";
        itemThree.fromSnippetInstructions = "n\n3\n0\n0\nMindy\n0\n2\nAndy\n0\n1\nPaul\n";
        itemThree.checkboxVisible = true;
        mTestBrowseItems.add(itemThree);
        mTestBrowseItems.add(itemThree);
        BrowseItemViewModel itemFour = new BrowseItemViewModel();
        itemFour.subject = "Fourth";
        itemFour.sendersText = "Mindy, Andy, Paul, Minh";
        itemFour.conversationId = 4;
        itemFour.fromSnippetInstructions = "n\n3\n0\n0\nMindy\n0\n2\nAndy\n0\n1\nPaul\n";
        itemFour.snippet = "fourth snippet";
        itemFour.checkboxVisible = true;
        mTestBrowseItems.add(itemFour);
        mTestBrowseItems.add(itemFour);
        mAdapter.addAll(mTestBrowseItems);

        mListView.setAdapter(mAdapter);
    }

    class BrowseItemAdapter extends ArrayAdapter<BrowseItemViewModel> {

        public BrowseItemAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            BrowseItemView view = new BrowseItemView(getContext(), "test@testaccount.com");
            view.bind(mAdapter.getItem(position), null, "test@testaccount.com", null,
                    new ViewMode(getContext()));
            return view;
        }
    }
}
