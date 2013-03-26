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
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.android.mail.R;
import com.android.mail.adapter.DrawerItem;
import com.android.mail.providers.*;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The folder list UI component.
 */
public final class FolderListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String LOG_TAG = LogTag.getLogTag();
    /** The parent activity */
    private ControllableActivity mActivity;
    /** The underlying list view */
    private ListView mListView;
    /** True if you want a sectioned FolderList, false otherwise. */
    private boolean mIsSectioned;
    /** Is the current device using tablet UI (true if 2-pane, false if 1-pane) */
    private boolean mIsTabletUI;
    /** An {@link ArrayList} of {@link FolderType}s to exclude from displaying. */
    private ArrayList<Integer> mExcludedFolderTypes;
    /** Object that changes folders on our behalf. */
    private FolderListSelectionListener mFolderChanger;
    /** Object that changes accounts on our behalf */
    private AccountController mAccountChanger;

    /** The currently selected folder (the folder being viewed).  This is never null. */
    private Uri mSelectedFolderUri = Uri.EMPTY;
    /**
     * The current folder from the controller.  This is meant only to check when the unread count
     * goes out of sync and fixing it. This should NOT be serialized and stored.
     */
    private Folder mCurrentFolderForUnreadCheck;
    /** Parent of the current folder, or null if the current folder is not a child. */
    private Folder mParentFolder;

    private static final int FOLDER_LOADER_ID = 0;
    /** Key to store {@link #mParentFolder}. */
    private static final String ARG_PARENT_FOLDER = "arg-parent-folder";
    /** Key to store {@link #mIsSectioned} */
    private static final String ARG_IS_SECTIONED = "arg-is-sectioned";
    /** Key to store {@link #mIsTabletUI} */
    private static final String ARG_IS_TABLET_UI = "arg-is-tablet-ui";
    /** Key to store {@link #mExcludedFolderTypes} */
    private static final String ARG_EXCLUDED_FOLDER_TYPES = "arg-excluded-folder-types";
    //TODO(shahrk): Disabled collapsed items - Bug: 8449121
    /** Should the {@link FolderListFragment} show less accounts to begin with? */
    private static final boolean ARE_ACCOUNT_ITEMS_COLLAPSED = false;
    /** Should the {@link FolderListFragment} show less labels to begin with? */
    private static final boolean ARE_FOLDER_ITEMS_COLLAPSED = false;

    private static final String BUNDLE_LIST_STATE = "flf-list-state";
    private static final String BUNDLE_SELECTED_FOLDER = "flf-selected-folder";
    private static final String BUNDLE_SELECTED_TYPE = "flf-selected-type";

    private FolderListFragmentCursorAdapter mCursorAdapter;
    /** Observer to wait for changes to the current folder so we can change the selected folder */
    private FolderObserver mFolderObserver = null;
    /** Listen for account changes. */
    private AccountObserver mAccountObserver = null;

    /** Listen to changes to list of all accounts */
    private AllAccountObserver mAllAccountObserver = null;
    /**
     * Type of currently selected folder: {@link DrawerItem#FOLDER_SYSTEM},
     * {@link DrawerItem#FOLDER_RECENT} or {@link DrawerItem#FOLDER_USER}.
     */
    // Setting to INERT_HEADER = leaving uninitialized.
    private int mSelectedFolderType = DrawerItem.UNSET;
    private Cursor mFutureData;
    private ConversationListCallbacks mConversationListCallback;
    /** The current account according to the controller */
    private Account mCurrentAccount;

    /** List of all accounts currently known */
    private Account[] mAllAccounts;

    /**
     * Constructor needs to be public to handle orientation changes and activity lifecycle events.
     */
    public FolderListFragment() {
        super();
    }

    /**
     * Creates a new instance of {@link ConversationListFragment}, initialized
     * to display conversation list context.
     * @param isSectioned True if sections should be shown for folder list
     * @param isTabletUI True if two-pane layout, false if not
     */
    public static FolderListFragment newInstance(Folder parentFolder,
            boolean isSectioned, boolean isTabletUI) {
        return newInstance(parentFolder, isSectioned, null, isTabletUI);
    }

    /**
     * Creates a new instance of {@link ConversationListFragment}, initialized
     * to display conversation list context.
     * @param isSectioned True if sections should be shown for folder list
     * @param excludedFolderTypes A list of {@link FolderType}s to exclude from displaying
     * @param isTabletUI True if two-pane layout, false if not
     */
    public static FolderListFragment newInstance(Folder parentFolder,
            boolean isSectioned, final ArrayList<Integer> excludedFolderTypes,
            boolean isTabletUI) {
        final FolderListFragment fragment = new FolderListFragment();
        fragment.setArguments(getBundleFromArgs(parentFolder, isSectioned,
                excludedFolderTypes, isTabletUI));
        return fragment;
    }

    /**
     * Construct a bundle that represents the state of this fragment.
     * @param parentFolder
     * @param isSectioned
     * @param excludedFolderTypes
     * @param isTabletUI
     * @return
     */
    private static Bundle getBundleFromArgs(Folder parentFolder,
            boolean isSectioned, final ArrayList<Integer> excludedFolderTypes,
            boolean isTabletUI) {
        final Bundle args = new Bundle();
        if (parentFolder != null) {
            args.putParcelable(ARG_PARENT_FOLDER, parentFolder);
        }
        args.putBoolean(ARG_IS_SECTIONED, isSectioned);
        args.putBoolean(ARG_IS_TABLET_UI, isTabletUI);
        if (excludedFolderTypes != null) {
            args.putIntegerArrayList(ARG_EXCLUDED_FOLDER_TYPES, excludedFolderTypes);
        }
        return args;
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
        mConversationListCallback = mActivity.getListHandler();
        final FolderController controller = mActivity.getFolderController();
        // Listen to folder changes in the future
        mFolderObserver = new FolderObserver() {
            @Override
            public void onChanged(Folder newFolder) {
                setSelectedFolder(newFolder);
            }
        };
        if (controller != null) {
            // Only register for selected folder updates if we have a controller.
            mCurrentFolderForUnreadCheck = mFolderObserver.initialize(controller);
        }
        final AccountController accountController = mActivity.getAccountController();
        mAccountObserver = new AccountObserver() {
            @Override
            public void onChanged(Account newAccount) {
                setSelectedAccount(newAccount);
            }
        };
        if (accountController != null) {
            // Current account and its observer.
            setSelectedAccount(mAccountObserver.initialize(accountController));
            // List of all accounts and its observer.
            mAllAccountObserver = new AllAccountObserver(){
                @Override
                public void onChanged(Account[] allAccounts) {
                    mAllAccounts = allAccounts;
                }
            };
            mAllAccounts = mAllAccountObserver.initialize(accountController);
            mAccountChanger = accountController;
        }

        mFolderChanger = mActivity.getFolderListSelectionListener();
        if (mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }

        final Folder selectedFolder;
        if (mParentFolder != null) {
            mCursorAdapter = new HierarchicalFolderListAdapter(null, mParentFolder);
            selectedFolder = mActivity.getHierarchyFolder();
        } else {
            // Initiate FLA with accounts and folders collapsed in the list
            // The second param is for whether folders should be collapsed
            // The third param is for whether accounts should be collapsed
            mCursorAdapter = new FolderListAdapter(mIsSectioned,
                    !mIsTabletUI && ARE_FOLDER_ITEMS_COLLAPSED,
                    !mIsTabletUI && ARE_ACCOUNT_ITEMS_COLLAPSED);
            selectedFolder = controller == null ? null : controller.getFolder();
        }
        // Is the selected folder fresher than the one we have restored from a bundle?
        if (selectedFolder != null && !selectedFolder.uri.equals(mSelectedFolderUri)) {
            setSelectedFolder(selectedFolder);
        }
        setListAdapter(mCursorAdapter);
    }

    /**
     * Set the instance variables from the arguments provided here.
     * @param args
     */
    private void setInstanceFromBundle(Bundle args) {
        mParentFolder = (Folder) args.getParcelable(ARG_PARENT_FOLDER);
        mIsSectioned = args.getBoolean(ARG_IS_SECTIONED);
        mIsTabletUI = args.getBoolean(ARG_IS_TABLET_UI);
        mExcludedFolderTypes = args.getIntegerArrayList(ARG_EXCLUDED_FOLDER_TYPES);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedState) {
        setInstanceFromBundle(getArguments());
        final View rootView = inflater.inflate(R.layout.folder_list, null);
        mListView = (ListView) rootView.findViewById(android.R.id.list);
        mListView.setHeaderDividersEnabled(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        mListView.setEmptyView(null);
        if (savedState != null && savedState.containsKey(BUNDLE_LIST_STATE)) {
            mListView.onRestoreInstanceState(savedState.getParcelable(BUNDLE_LIST_STATE));
        }
        if (savedState != null && savedState.containsKey(BUNDLE_SELECTED_FOLDER)) {
            mSelectedFolderUri = Uri.parse(savedState.getString(BUNDLE_SELECTED_FOLDER));
            mSelectedFolderType = savedState.getInt(BUNDLE_SELECTED_TYPE);
        } else if (mParentFolder != null) {
            mSelectedFolderUri = mParentFolder.uri;
            // No selected folder type required for hierarchical lists.
        }

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mListView != null) {
            outState.putParcelable(BUNDLE_LIST_STATE, mListView.onSaveInstanceState());
        }
        if (mSelectedFolderUri != null) {
            outState.putString(BUNDLE_SELECTED_FOLDER, mSelectedFolderUri.toString());
        }
        outState.putInt(BUNDLE_SELECTED_TYPE, mSelectedFolderType);
    }

    @Override
    public void onDestroyView() {
        if (mCursorAdapter != null) {
            mCursorAdapter.destroy();
        }
        // Clear the adapter.
        setListAdapter(null);
        if (mFolderObserver != null) {
            mFolderObserver.unregisterAndDestroy();
            mFolderObserver = null;
        }
        if (mAccountObserver != null) {
            mAccountObserver.unregisterAndDestroy();
            mAccountObserver = null;
        }
        if (mAllAccountObserver != null) {
            mAllAccountObserver.unregisterAndDestroy();
            mAllAccountObserver = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        viewFolderOrChangeAccount(position);
    }

    /**
     * Display the conversation list from the folder at the position given.
     * @param position a zero indexed position into the list.
     */
    private void viewFolderOrChangeAccount(int position) {
        final Object item = getListAdapter().getItem(position);
        final Folder folder;
        if (item instanceof DrawerItem) {
            final DrawerItem folderItem = (DrawerItem) item;
            // Could be a folder, account, or expand block.
            final int itemType = mCursorAdapter.getItemType(folderItem);
            if (itemType == DrawerItem.VIEW_ACCOUNT) {
                // Account, so switch.
                folder = null;
                final Account account = mCursorAdapter.getFullAccount(folderItem);
                mAccountChanger.changeAccount(account);
            } else if (itemType == DrawerItem.VIEW_FOLDER) {
                // Folder type, so change folders only.
                folder = mCursorAdapter.getFullFolder(folderItem);
                mSelectedFolderType = folderItem.mFolderType;
            } else {
                // Block for expanding/contracting labels/accounts
                folder = null;
                if(!folderItem.mIsCurrAcctOrExpandAccount) {
                    mCursorAdapter.toggleShowLessFolders();
                } else {
                    mCursorAdapter.toggleShowLessAccounts();
                }
            }
        } else if (item instanceof Folder) {
            folder = (Folder) item;
        } else {
            folder = new Folder((Cursor) item);
        }
        if (folder != null) {
            // Since we may be looking at hierarchical views, if we can
            // determine the parent of the folder we have tapped, set it here.
            // If we are looking at the folder we are already viewing, don't
            // update its parent!
            folder.parent = folder.equals(mParentFolder) ? null : mParentFolder;
            // Go to the conversation list for this folder.
            mFolderChanger.onFolderSelected(folder);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        mListView.setEmptyView(null);
        // This introduces a bug in Email where Hierarchical folders won't work anymore.
        // http://b/8473060 assigned to Viki for a quick fix (before MR2 or Bazaar release)
        return new CursorLoader(mActivity.getActivityContext(), mCurrentAccount.folderListUri,
                UIProvider.FOLDERS_PROJECTION, null, null, null);
    }

    public void onAnimationEnd() {
        if (mFutureData != null) {
            mCursorAdapter.setCursor(mFutureData);
            mFutureData = null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (mConversationListCallback == null || !mConversationListCallback.isAnimating()) {
            mCursorAdapter.setCursor(data);
        } else {
            mFutureData = data;
            mCursorAdapter.setCursor(null);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mCursorAdapter.setCursor(null);
    }

    /**
     * Interface for all cursor adapters that allow setting a cursor and being destroyed.
     */
    private interface FolderListFragmentCursorAdapter extends ListAdapter {
        /** Update the folder list cursor with the cursor given here. */
        void setCursor(Cursor cursor);
        /** Toggles showing more accounts or less accounts. */
        boolean toggleShowLessAccounts();
        /** Toggles showing more folders or less. */
        boolean toggleShowLessFolders();
        /**
         * Given an item, find the type of the item, which is {@link
         * DrawerItem#VIEW_FOLDER}, {@link DrawerItem#VIEW_ACCOUNT} or
         * {@link DrawerItem#VIEW_MORE}
         * @return the type of the item.
         */
        int getItemType(DrawerItem item);
        /** Get the folder associated with this item. **/
        Folder getFullFolder(DrawerItem item);
        /** Get the account associated with this item. **/
        Account getFullAccount(DrawerItem item);
        /** Remove all observers and destroy the object. */
        void destroy();
        /** Notifies the adapter that the data has changed. */
        void notifyDataSetChanged();
    }

    /**
     * An adapter for flat folder lists.
     */
    private class FolderListAdapter extends BaseAdapter implements FolderListFragmentCursorAdapter {

        private final RecentFolderObserver mRecentFolderObserver = new RecentFolderObserver() {
            @Override
            public void onChanged() {
                recalculateList();
            }
        };

        /** After given number of accounts, show "more" until expanded. */
        private static final int MAX_ACCOUNTS = 2;
        /** After the given number of labels, show "more" until expanded. */
        private static final int MAX_FOLDERS = 7;

        private final RecentFolderList mRecentFolders;
        /** True if the list is sectioned, false otherwise */
        private final boolean mIsSectioned;
        /** All the items */
        private final List<DrawerItem> mItemList = new ArrayList<DrawerItem>();
        /** Cursor into the folder list. This might be null. */
        private Cursor mCursor = null;
        /** Watcher for tracking and receiving unread counts for mail */
        private FolderWatcher mFolderWatcher = null;
        private boolean mShowLessFolders;
        private boolean mShowLessAccounts;

        /** Track whether the accounts have folder watchers added to them yet */
        private boolean mAccountsWatched;

        /**
         * Creates a {@link FolderListAdapter}.This is a flat folder list of all the folders for the
         * given account.
         * @param isSectioned TODO(viki):
         */
        public FolderListAdapter(boolean isSectioned, boolean showLess, boolean showLessAccounts) {
            super();
            mIsSectioned = isSectioned;
            final RecentFolderController controller = mActivity.getRecentFolderController();
            if (controller != null && mIsSectioned) {
                mRecentFolders = mRecentFolderObserver.initialize(controller);
            } else {
                mRecentFolders = null;
            }
            mFolderWatcher = new FolderWatcher(mActivity, this);
            mShowLessFolders = showLess;
            mShowLessAccounts = showLessAccounts;
            mAccountsWatched = false;
            initFolderWatcher();
        }

        /**
         * If accounts have not yet been added to folder watcher due to various
         * null pointer issues, add them.
         */
        public void initFolderWatcher() {
            if (!mAccountsWatched && mAllAccounts != null) {
                for (int i = 0; i < mAllAccounts.length; i++) {
                    final Uri uri = mAllAccounts[i].settings.defaultInbox;
                    mFolderWatcher.startWatching(uri);
                }
                mAccountsWatched = true;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final DrawerItem item = (DrawerItem) getItem(position);
            final View view = item.getView(position, convertView, parent);
            final int type = item.mType;
            if (mListView!= null) {
                final boolean isSelected =
                        item.isHighlighted(mCurrentFolderForUnreadCheck, mSelectedFolderType);
                if (type == DrawerItem.VIEW_FOLDER) {
                    mListView.setItemChecked(position, isSelected);
                }
                // If this is the current folder, also check to verify that the unread count
                // matches what the action bar shows.
                if (type == DrawerItem.VIEW_FOLDER
                        && isSelected
                        && (mCurrentFolderForUnreadCheck != null)
                        && item.mFolder.unreadCount != mCurrentFolderForUnreadCheck.unreadCount) {
                    ((FolderItemView) view).overrideUnreadCount(
                            mCurrentFolderForUnreadCheck.unreadCount);
                }
            }
            return view;
        }

        @Override
        public int getViewTypeCount() {
            // Accounts, headers, folders (all parts of drawer view types)
            return DrawerItem.getViewTypes();
        }

        @Override
        public int getItemViewType(int position) {
            return ((DrawerItem) getItem(position)).mType;
        }

        @Override
        public int getCount() {
            return mItemList.size();
        }

        @Override
        public boolean isEnabled(int position) {
            final DrawerItem item = (DrawerItem) getItem(position);
            return item.isItemEnabled(getCurrentAccountUri());

        }

        private Uri getCurrentAccountUri() {
            return mCurrentAccount == null ? Uri.EMPTY : mCurrentAccount.uri;
        }

        @Override
        public boolean areAllItemsEnabled() {
            // The headers and current accounts are not enabled.
            return false;
        }

        /**
         * Returns all the recent folders from the list given here. Safe to call with a null list.
         * @param recentList a list of all recently accessed folders.
         * @return a valid list of folders, which are all recent folders.
         */
        private List<Folder> getRecentFolders(RecentFolderList recentList) {
            final List<Folder> folderList = new ArrayList<Folder>();
            if (recentList == null) {
                return folderList;
            }
            // Get all recent folders, after removing system folders.
            for (final Folder f : recentList.getRecentFolderList(null)) {
                if (!f.isProviderFolder()) {
                    folderList.add(f);
                }
            }
            return folderList;
        }

        /**
         * Toggle boolean for what folders are shown and which ones are
         * hidden. Redraws list after toggling to show changes.
         * @return true if folders are hidden, false if all are shown
         */
        @Override
        public boolean toggleShowLessFolders() {
            mShowLessFolders = !mShowLessFolders;
            recalculateList();
            return mShowLessFolders;
        }

        /**
         * Toggle boolean for what accounts are shown and which ones are
         * hidden. Redraws list after toggling to show changes.
         * @return true if accounts are hidden, false if all are shown
         */
        @Override
        public boolean toggleShowLessAccounts() {
            mShowLessAccounts = !mShowLessAccounts;
            recalculateList();
            return mShowLessAccounts;
        }

        /**
         * Responsible for verifying mCursor, adding collapsed view items
         * when necessary, and notifying the data set has changed.
         */
        private void recalculateList() {
            final boolean haveAccount = (mAllAccounts != null && mAllAccounts.length > 0);
            if (!haveAccount) {
                // TODO(viki): How do we get a notification that we have accounts now? Currently
                // we don't, and we should.
                return;
            }
            recalculateListFolders();
            if(mShowLessFolders) {
                mItemList.add(DrawerItem.ofMore(mActivity, R.string.folder_list_more, false));
            }
            // Ask the list to invalidate its views.
            notifyDataSetChanged();
        }

        /**
         * Recalculates the system, recent and user label lists.
         * This method modifies all the three lists on every single invocation.
         */
        private void recalculateListFolders() {
            mItemList.clear();

            if (mAllAccounts != null) {
                // Add the accounts at the top. Tell mFolderWatcher which
                // folders to track in case accounts were null earlier on.
                // TODO(shahrk): The logic here is messy and will be changed
                // to properly add/reflect on LRU/MRU account
                // changes similar to RecentFoldersList
                int unreadCount;
                initFolderWatcher();

                if (mShowLessAccounts && mAllAccounts.length > MAX_ACCOUNTS) {
                    // Add show all accounts block along with current accounts
                    mItemList.add(DrawerItem.ofMore(
                            mActivity, R.string.folder_list_show_all_accounts, true));
                    mItemList.add(DrawerItem.ofAccount(mActivity, mCurrentAccount, 0, true));
                } else {
                    // Add all accounts and then the current account
                    Uri currentAccountUri = getCurrentAccountUri();
                    for (final Account account : mAllAccounts) {
                        if (!currentAccountUri.equals(account.uri)) {
                            unreadCount = getInboxUnreadCount(account);
                            mItemList.add(DrawerItem.ofAccount(mActivity, account, unreadCount,
                                    false));
                        }
                    }
                    mItemList.add(DrawerItem.ofAccount(mActivity, mCurrentAccount, 0, true));
                }
            }

            // If we are waiting for folder initialization, we don't have any kinds of folders,
            // just the "Waiting for initialization" item.
            if (isCursorInvalid(mCursor)) {
                mItemList.add(DrawerItem.forWaitView(mActivity));
                return;
            }

            if (!mIsSectioned) {
                // Adapter for a flat list. Everything is a FOLDER_USER, and there are no headers.
                do {
                    final Folder f = Folder.getDeficientDisplayOnlyFolder(mCursor);
                    if (!isFolderTypeExcluded(f)) {
                        mItemList.add(DrawerItem.ofFolder(mActivity, f, DrawerItem.FOLDER_USER,
                                mCursor.getPosition()));
                    }
                } while (mCursor.moveToNext());
                // Ask the list to invalidate its views.
                notifyDataSetChanged();
                return;
            }

            // Tracks how many folders have been added through the rest of the function
            int folderCount = 0;
            // Otherwise, this is an adapter for a sectioned list.
            // First add all the system folders.
            final List<DrawerItem> userFolderList = new ArrayList<DrawerItem>();
            do {
                final Folder f = Folder.getDeficientDisplayOnlyFolder(mCursor);
                if (!isFolderTypeExcluded(f)) {
                    if (f.isProviderFolder()) {
                        mItemList.add(DrawerItem.ofFolder(mActivity, f, DrawerItem.FOLDER_SYSTEM,
                                mCursor.getPosition()));
                        // Check if show less is enabled and we've passed max folders
                        folderCount++;
                        if(mShowLessFolders && folderCount >= MAX_FOLDERS) {
                            return;
                        }
                    } else {
                        userFolderList.add(DrawerItem.ofFolder(
                                mActivity, f, DrawerItem.FOLDER_USER, mCursor.getPosition()));
                    }
                }
            } while (mCursor.moveToNext());
            // If there are recent folders, add them and a header.
            final List<Folder> recentFolderList = getRecentFolders(mRecentFolders);

            // Remove any excluded folder types
            if (mExcludedFolderTypes != null) {
                final Iterator<Folder> iterator = recentFolderList.iterator();
                while (iterator.hasNext()) {
                    if (isFolderTypeExcluded(iterator.next())) {
                        iterator.remove();
                    }
                }
            }

            if (recentFolderList.size() > 0) {
                mItemList.add(DrawerItem.ofHeader(mActivity, R.string.recent_folders_heading));
                // Recent folders are not queried for position.
                final int position = -1;
                for (Folder f : recentFolderList) {
                    mItemList.add(DrawerItem.ofFolder(mActivity, f, DrawerItem.FOLDER_RECENT,
                            position));
                    // Check if show less is enabled and we've passed max folders
                    folderCount++;
                    if(mShowLessFolders && folderCount >= MAX_FOLDERS) {
                        return;
                    }
                }
            }
            // If there are user folders, add them and a header.
            if (userFolderList.size() > 0) {
                mItemList.add(DrawerItem.ofHeader(mActivity, R.string.all_folders_heading));
                for (final DrawerItem i : userFolderList) {
                    mItemList.add(i);
                    // Check if show less is enabled and we've passed max folders
                    folderCount++;
                    if(mShowLessFolders && folderCount >= MAX_FOLDERS) {
                        return;
                    }
                }
            }
        }

        /**
         * Given an account, get the unreadCount from the FolderWatcher.
         *
         * @param account Account to get inbox unread count from
         * @return Default inbox unread count
         */
        public int getInboxUnreadCount(Account account) {
            int unreadCount = 0;
            Folder inbox = mFolderWatcher.get(account.settings.defaultInbox);

            if (inbox != null) {
                // If inbox is found, get updated count otherwise NPE can be
                // thrown
                unreadCount = inbox.unreadCount;
            } else {
                unreadCount = 0;
            }
            return unreadCount;
        }

        /**
         * Check if the cursor provided is valid.
         * @param mCursor
         * @return
         */
        private boolean isCursorInvalid(Cursor mCursor) {
            return mCursor == null || mCursor.isClosed()|| mCursor.getCount() <= 0
                    || !mCursor.moveToFirst();

        }

        @Override
        public void setCursor(Cursor cursor) {
            mCursor = cursor;
            recalculateList();
        }

        @Override
        public Object getItem(int position) {
            return mItemList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        @Override
        public final void destroy() {
            mRecentFolderObserver.unregisterAndDestroy();
        }

        @Override
        public int getItemType(DrawerItem item) {
            return item.mType;
        }

        // TODO(viki): This is strange. We have the full folder and yet we create on from scratch.
        @Override
        public Folder getFullFolder(DrawerItem folderItem) {
            if (folderItem.mFolderType == DrawerItem.FOLDER_RECENT) {
                return folderItem.mFolder;
            } else {
                int pos = folderItem.mPosition;
                if (mFutureData != null) {
                    mCursor = mFutureData;
                    mFutureData = null;
                }
                if (pos > -1 && mCursor != null && !mCursor.isClosed()
                        && mCursor.moveToPosition(folderItem.mPosition)) {
                    return new Folder(mCursor);
                } else {
                    return null;
                }
            }
        }

        @Override
        public Account getFullAccount(DrawerItem item) {
            return item.mAccount;
        }
    }

    private class HierarchicalFolderListAdapter extends ArrayAdapter<Folder>
            implements FolderListFragmentCursorAdapter{

        private static final int PARENT = 0;
        private static final int CHILD = 1;
        private final Uri mParentUri;
        private final Folder mParent;
        private final FolderItemView.DropHandler mDropHandler;
        private Cursor mCursor;

        public HierarchicalFolderListAdapter(Cursor c, Folder parentFolder) {
            super(mActivity.getActivityContext(), R.layout.folder_item);
            mDropHandler = mActivity;
            mParent = parentFolder;
            mParentUri = parentFolder.uri;
            setCursor(c);
        }

        @Override
        public int getViewTypeCount() {
            // Child and Parent
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            final Folder f = getItem(position);
            return f.uri.equals(mParentUri) ? PARENT : CHILD;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final FolderItemView folderItemView;
            final Folder folder = getItem(position);
            boolean isParent = folder.uri.equals(mParentUri);
            if (convertView != null) {
                folderItemView = (FolderItemView) convertView;
            } else {
                int resId = isParent ? R.layout.folder_item : R.layout.child_folder_item;
                folderItemView = (FolderItemView) LayoutInflater.from(
                        mActivity.getActivityContext()).inflate(resId, null);
            }
            folderItemView.bind(folder, mDropHandler);
            if (folder.uri.equals(mSelectedFolderUri)) {
                getListView().setItemChecked(position, true);
                // If this is the current folder, also check to verify that the unread count
                // matches what the action bar shows.
                final boolean unreadCountDiffers = (mCurrentFolderForUnreadCheck != null)
                        && folder.unreadCount != mCurrentFolderForUnreadCheck.unreadCount;
                if (unreadCountDiffers) {
                    folderItemView.overrideUnreadCount(mCurrentFolderForUnreadCheck.unreadCount);
                }
            }
            Folder.setFolderBlockColor(folder, folderItemView.findViewById(R.id.color_block));
            Folder.setIcon(folder, (ImageView) folderItemView.findViewById(R.id.folder_icon));
            return folderItemView;
        }

        @Override
        public void setCursor(Cursor cursor) {
            mCursor = cursor;
            clear();
            if (mParent != null) {
                add(mParent);
            }
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                do {
                    Folder f = new Folder(cursor);
                    f.parent = mParent;
                    add(f);
                } while (cursor.moveToNext());
            }
        }

        @Override
        public void destroy() {
            // Do nothing.
        }

        @Override
        public int getItemType(DrawerItem item) {
            // Always returns folders for now.
            return DrawerItem.VIEW_FOLDER;
        }

        @Override
        public Folder getFullFolder(DrawerItem folderItem) {
            int pos = folderItem.mPosition;
            if (mCursor == null || mCursor.isClosed()) {
                // See if we have a cursor hanging out we can use
                mCursor = mFutureData;
                mFutureData = null;
            }
            if (pos > -1 && mCursor != null && !mCursor.isClosed()
                    && mCursor.moveToPosition(folderItem.mPosition)) {
                return new Folder(mCursor);
            } else {
                return null;
            }
        }

        @Override
        public Account getFullAccount(DrawerItem item) {
            return null;
        }

        @Override
        public boolean toggleShowLessFolders() {
            return false;
        }

        @Override
        public boolean toggleShowLessAccounts() {
            return false;
        }
    }

    /**
     * Sets the currently selected folder safely.
     * @param folder
     */
    private void setSelectedFolder(Folder folder) {
        if (folder == null) {
            mSelectedFolderUri = Uri.EMPTY;
            LogUtils.e(LOG_TAG, "FolderListFragment.setSelectedFolder(null) called!");
            return;
        }
        mCurrentFolderForUnreadCheck = folder;
        mSelectedFolderUri = folder.uri;
        setSelectedFolderType(folder);
        if (mCursorAdapter != null) {
            mCursorAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Sets the selected folder type safely.
     * @param folder folder to set to.
     */
    private void setSelectedFolderType(Folder folder) {
        if (mSelectedFolderType == DrawerItem.UNSET) {
            mSelectedFolderType = folder.isProviderFolder() ? DrawerItem.FOLDER_SYSTEM
                    : DrawerItem.FOLDER_USER;
        }
    }

    /**
     * Sets the current account to the one provided here.
     * @param account the current account to set to.
     */
    private void setSelectedAccount(Account account){
        mCurrentAccount = account;
        if (mCurrentAccount != null) {
            getLoaderManager().restartLoader(FOLDER_LOADER_ID, Bundle.EMPTY, this);
        }
    }

    public interface FolderListSelectionListener {
        public void onFolderSelected(Folder folder);
    }

    /**
     * Get whether the FolderListFragment is currently showing the hierarchy
     * under a single parent.
     */
    public boolean showingHierarchy() {
        return mParentFolder != null;
    }

    /**
     * Checks if the specified {@link Folder} is a type that we want to exclude from displaying.
     */
    private boolean isFolderTypeExcluded(final Folder folder) {
        if (mExcludedFolderTypes == null) {
            return false;
        }

        for (final int excludedType : mExcludedFolderTypes) {
            if (folder.isType(excludedType)) {
                return true;
            }
        }

        return false;
    }
}
