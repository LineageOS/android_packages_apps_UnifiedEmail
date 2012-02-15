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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;

/**
 * The conversation list UI component.
 */
public final class FolderListFragment extends ListFragment {
    private static final String LOG_TAG = new LogUtils().getLogTag();

    private ControllableActivity mActivity;

    // Control state.
    private Cursor mFolderListCursor;

    // The internal view objects.
    private ListView mListView;

    private ContentResolver mResolver;

    private String mFolderListUri;
    /**
     * Hidden constructor.
     */
    private FolderListFragment(String uri) {
        super();
        mFolderListUri = uri;
    }

    /**
     * Creates a new instance of {@link ConversationListFragment}, initialized to display
     * conversation list context.
     */
    public static FolderListFragment newInstance(String uri) {
        FolderListFragment fragment = new FolderListFragment(uri);
        return fragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Strictly speaking, we get back an android.app.Activity from getActivity. However, the
        // only activity creating a ConversationListContext is a MailActivity which is of type
        // ControllableActivity, so this cast should be safe. If this cast fails, some other
        // activity is creating ConversationListFragments. This activity must be of type
        // ControllableActivity.
        final Activity activity = getActivity();
        if (! (activity instanceof ControllableActivity)){
            LogUtils.wtf(LOG_TAG, "FolderListFragment expects only a ControllableActivity to" +
                    "create it. Cannot proceed.");
        }
        mActivity = (ControllableActivity) activity;
        mActivity.attachFolderList(this);
        mResolver = mActivity.getContentResolver();

        if (mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }

        // Show list and start loading list.
        showList();
    }

    @Override
    public void onCreate(Bundle savedState) {
        LogUtils.v(LOG_TAG, "onCreate in FolderListFragment(this=%s)", this);
        super.onCreate(savedState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        LogUtils.v(LOG_TAG, "onCreateView in FolderListFragment(this=%s)", this);
        View rootView = inflater.inflate(R.layout.folder_list, null);
        mListView = (ListView) rootView.findViewById(android.R.id.list);
        mListView.setHeaderDividersEnabled(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        // Note - we manually save/restore the listview state.
        mListView.setSaveEnabled(false);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        // Clear the adapter.
        mListView.setAdapter(null);

        mActivity.attachFolderList(null);

        super.onDestroyView();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        viewFolder(position);
    }

    private void viewFolder(int position) {
        mFolderListCursor.moveToPosition(position);
        Folder selected = new Folder(mFolderListCursor);
        if (selected.hasChildren) {
            // Replace this fragment with a new FolderListFragment
            // showing this folder's children.
            FragmentTransaction fragmentTransaction = mActivity.getFragmentManager()
                    .beginTransaction();
            fragmentTransaction.addToBackStack(null);
            final boolean accountChanged = false;
            // TODO(viki): This account transition looks strange in two pane
            // mode. Revisit as the app
            // is coming together and improve the look and feel.
            final int transition = accountChanged ? FragmentTransaction.TRANSIT_FRAGMENT_FADE
                    : FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
            fragmentTransaction.setTransition(transition);

            Fragment folderListFragment = FolderListFragment
                    .newInstance(selected.childFoldersListUri);
            fragmentTransaction.replace(R.id.content_pane, folderListFragment);

            fragmentTransaction.commitAllowingStateLoss();

        } else {
            // Go to the conversation list for this folder.
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    /**
     * Handles a request to show a new conversation list, either from a search query or for viewing
     * a label. This will initiate a data load, and hence must be called on the UI thread.
     */
    private void showList() {
        mListView.setEmptyView(null);

        mFolderListCursor = mResolver.query(Uri.parse(mFolderListUri),
                UIProvider.FOLDERS_PROJECTION, null, null, null);
        mListView.setAdapter(new FolderListAdapter(mActivity.getActivityContext(),
                R.layout.folder_item, mFolderListCursor, null, null));
    }

    private class FolderListAdapter extends SimpleCursorAdapter {

        public FolderListAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, new String[0], new int[0], 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FolderItemView folderItemView;
            if (convertView != null) {
                folderItemView = (FolderItemView) convertView;
            } else {
                folderItemView = (FolderItemView) LayoutInflater.from(
                        mActivity.getActivityContext()).inflate(R.layout.folder_item, null);
            }
            getCursor().moveToPosition(position);
            folderItemView.bind(new Folder(getCursor()), null);
            return folderItemView;
        }
    }
}
