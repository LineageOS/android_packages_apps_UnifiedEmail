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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;

/**
 * The folder list UI component.
 */
public final class FolderListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor>, ViewMode.ModeChangeListener, OnClickListener {
    private static final String LOG_TAG = new LogUtils().getLogTag();

    private ControllableActivity mActivity;

    // Control state.
    private Cursor mFolderListCursor;

    // The internal view objects.
    private ListView mListView;

    private Uri mFolderListUri;

    private FolderListSelectionListener mListener;

    private Folder mSelectedFolder;

    private Folder mParentFolder;

    private static final int FOLDER_LOADER_ID = 0;
    public static final int MODE_DEFAULT = 0;
    public static final int MODE_PICK = 1;
    private static final String ARG_PARENT_FOLDER = "arg-parent-folder";
    private static final String ARG_FOLDER_URI = "arg-folder-list-uri";

    /**
     * Constructor needs to be public to handle orientation changes and activity lifecycle events.
     * @param listener
     */
    public FolderListFragment(FolderListSelectionListener listener) {
        super();
        mListener = listener;
    }

    /**
     * Creates a new instance of {@link ConversationListFragment}, initialized
     * to display conversation list context.
     */
    public static FolderListFragment newInstance(FolderListSelectionListener listener,
            Folder parentFolder, Uri folderUri) {
        FolderListFragment fragment = new FolderListFragment(listener);
        Bundle args = new Bundle();
        if (parentFolder != null) {
            args.putParcelable(ARG_PARENT_FOLDER, parentFolder);
        }
        args.putString(ARG_FOLDER_URI, folderUri.toString());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onActivityCreated(Bundle savedState) {
        super.onActivityCreated(savedState);
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
        if (mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }
        getLoaderManager().initLoader(FOLDER_LOADER_ID, Bundle.EMPTY, this);
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final Bundle args = getArguments();
        mFolderListUri = Uri.parse(args.getString(ARG_FOLDER_URI));
        mParentFolder = (Folder) args.getParcelable(ARG_PARENT_FOLDER);
        View rootView = inflater.inflate(mParentFolder != null ?
                R.layout.folder_list_with_parent : R.layout.folder_list, null);
        mListView = (ListView) rootView.findViewById(android.R.id.list);
        mListView.setHeaderDividersEnabled(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mListView.setEmptyView(null);
        // Note - we manually save/restore the listview state.
        mListView.setSaveEnabled(false);

        if (mParentFolder != null) {
            TextView parentFolderView = (TextView) rootView.findViewById(R.id.parent_folder_name);
            parentFolderView.setText(mParentFolder.name);
            parentFolderView.setOnClickListener(this);
        }
        return rootView;
    }

    @Override
    public void onDestroyView() {
        // Clear the adapter.
        setListAdapter(null);

        mActivity.attachFolderList(null);

        super.onDestroyView();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        viewFolder(position);
    }

    public void viewFolder(int position) {
        mFolderListCursor.moveToPosition(position);
        mSelectedFolder = new Folder(mFolderListCursor);
        // Go to the conversation list for this folder.
        mListener.onFolderSelected(mSelectedFolder, mParentFolder != null);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(mActivity.getActivityContext(), mFolderListUri,
                UIProvider.FOLDERS_PROJECTION, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mFolderListCursor = data;
        setListAdapter(new FolderListAdapter(mActivity.getActivityContext(),
                R.layout.folder_item, mFolderListCursor, null, null));
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Do nothing.
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
            Folder folder = new Folder(getCursor());
            folderItemView.bind(folder, null);
            if (mSelectedFolder != null && folder.uri.equals(mSelectedFolder.uri)) {
                getListView().setItemChecked(position, true);
            }
            Folder.setFolderBlockColor(folder, folderItemView.findViewById(R.id.folder_box));
            return folderItemView;
        }
    }

    @Override
    public void onViewModeChanged(int newMode) {
        // Listen on mode changes, when we move to Folder list mode, change accordingly.
    }

    public void selectFolder(Folder folder) {
        mSelectedFolder = folder;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.parent_folder_name && mParentFolder != null) {
            mSelectedFolder = mParentFolder;
            mListener.onFolderSelected(mSelectedFolder, mParentFolder != null);
        }
    }

    interface FolderListSelectionListener {
        public void onFolderSelected(Folder folder, boolean viewingChildren);
    }
}