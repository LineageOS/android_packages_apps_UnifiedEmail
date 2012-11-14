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

package com.android.mail;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.Folder;
import com.android.mail.providers.FolderWatcher;
import com.android.mail.providers.RecentFolderObserver;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.RecentFolderController;
import com.android.mail.ui.RecentFolderList;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import java.util.ArrayList;
import java.util.Vector;

/**
 * An adapter to return the list of accounts and folders for the Account Spinner.
 * This class keeps the account and folder information and returns appropriate views.
 */
public class AccountSpinnerAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    /**
     * The position of the current account being viewed.
     */
    private Account mCurrentAccount = null;
    /**
     * Total number of accounts.
     */
    private int mNumAccounts = 0;
    /**
     * Array of all the accounts on the device.
     */
    private Account[] mAllAccounts = new Account[0];
    /**
     * An object that provides a collection of recent folders, per account.
     */
    private RecentFolderList mRecentFolders;
    /**
     * The actual collection of sorted recent folders obtained from {@link #mRecentFolders}
     */
    private ArrayList<Folder> mRecentFolderList = new ArrayList<Folder>();
    /**
     * Boolean indicating whether the "Show All Folders" items should be shown
     */
    private final boolean mShowAllFoldersItem;
    /**
     * Set to true to enable recent folders, false to disable.
     */
    private boolean mRecentFoldersVisible;

    /** The folder currently being viewed */
    private Folder mCurrentFolder;
    private final Context mContext;
    /** Maintains the most fresh default inbox folder for each account.  Used for unread counts. */
    private final FolderWatcher mFolderWatcher;

    /** Type indicating a view that separates the account list from the recent folder list. */
    public static final int TYPE_HEADER = AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER;
    /** Type indicating an account (user@example.com). */
    public static final int TYPE_ACCOUNT = 0;
    /** Type indicating a view containing a recent folder (Sent, Outbox). */
    public static final int TYPE_FOLDER = 1;
    /** Type indicating the "Show All Folders" view. */
    public static final int TYPE_ALL_FOLDERS = 2;
    /**
     * Cache of {@link #TYPE_ACCOUNT} views returned from {@link #getView(int, View, ViewGroup)}.
     * The entry at position i is the view shown in the spinner at position i, possibly null. If the
     * entry is null, then a view should be created and stored in this cache.
     */
    private final Vector<View> mAccountViews = new Vector<View>();
    /**
     * Cache of {@link #TYPE_FOLDER} views returned from {@link #getView(int, View, ViewGroup)}. The
     * entry at position i is the view shown in the spinner at position num_accounts + 1 + i,
     * possibly null. If the entry is null, then a view should be created and stored in this cache.
     */
    private final Vector<View> mFolderViews = new Vector<View>();
    /**
     * Cache of {@link #TYPE_HEADER} view returned from {@link #getView(int, View, ViewGroup)}.
     */
    private View mHeaderView = null;
    /**
     * Cache of {@link #TYPE_ALL_FOLDERS} view returned from {@link #getView(int, View, ViewGroup)}.
     */
    private View mFooterView = null;

    private RecentFolderObserver mRecentFolderObserver;
    private RecentFolderObserver mSpinnerRecentFolderObserver = new RecentFolderObserver() {
        @Override
        public void onChanged() {
            requestRecentFolders();
        }
    };
    private static final String LOG_TAG = LogTag.getLogTag();

    final AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount){
            // If the account is missing or we have no accounts array, we cannot
            // proceed.
            if (newAccount == null) {
                return;
            }
            if (newAccount.uri.equals(getCurrentAccountUri())) {
                // The current account matches what is being given, get out.
                return;
            }
            mCurrentAccount = newAccount;
            if (mRecentFoldersVisible) {
                final int pos = Account.findPosition(mAllAccounts, newAccount.uri);
                LogUtils.d(LOG_TAG, "setCurrentAccount: mCurrentAccountPos = %d", pos);
                if (pos >= 0) {
                    requestRecentFolders();
                }
            }
            notifyDataSetChanged();
        }
    };
    private RecentFolderController mRecentFolderController;

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    /**
     * There can be three types of views: Accounts (test@android.com, fifi@example.com), folders
     * (Inbox, Outbox) or header and footer. This method returns the type of account at given
     * position in the drop down list.
     * @param position
     * @return the type of account: one of {@link #TYPE_ACCOUNT}, {@link #TYPE_HEADER}, or
     * {@link #TYPE_FOLDER}.
     */
    @Override
    public int getItemViewType(int position) {
        // First the accounts
        if (position < mNumAccounts) {
            return TYPE_ACCOUNT;
        }
        // Then the header
        if (position == mNumAccounts) {
            return TYPE_HEADER;
        }
        if (mShowAllFoldersItem && getRecentOffset(position) >= mRecentFolderList.size()) {
            return TYPE_ALL_FOLDERS;
        }
        // Finally, the recent folders.
        return TYPE_FOLDER;
    }

    /**
     * Given a position in the list, what offset does it correspond to in the Recent Folders
     * list?
     * @param position
     * @return
     */
    private final int getRecentOffset(int position) {
        return position - mNumAccounts - 1;
    }

    /**
     * Create a spinner adapter with the context and the list of recent folders.
     * @param activity
     * @param context
     * @param showAllFolders
     */
    public AccountSpinnerAdapter(ControllableActivity activity, Context context,
            boolean showAllFolders) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mShowAllFoldersItem = showAllFolders;
        // Owned by the AccountSpinnerAdapter since nobody else needed it. Move
        // to controller if required. The folder watcher tells us directly when
        // new data is available. We are only interested in unread counts at this point.
        mFolderWatcher = new FolderWatcher(activity, this);
        mCurrentAccount = mAccountObserver.initialize(activity.getAccountController());
        mRecentFolderController = activity.getRecentFolderController();
    }

    /**
     * Set the accounts for this spinner.
     * @param accounts
     */
    public void setAccountArray(Account[] accounts) {
        final Uri currentAccount = getCurrentAccountUri();
        mAllAccounts = accounts;
        mNumAccounts = accounts.length;
        mAccountViews.setSize(mNumAccounts);
        if (!isCurrentAccountInvalid()) {
            final int pos = Account.findPosition(accounts, currentAccount);
            LogUtils.d(LOG_TAG, "setAccountArray: mCurrentAccountPos = %d", pos);
        }
        // Go through all the accounts and add the default inbox to our watcher.
        for (int i=0; i < mNumAccounts; i++) {
            final Uri uri = mAllAccounts[i].settings.defaultInbox;
            mFolderWatcher.startWatching(uri);
        }
        notifyDataSetChanged();
    }

    /**
     * Set the current folder.
     * @param folder
     * @return if changed.
     */
    public boolean setCurrentFolder(Folder folder) {
        if (folder != null && folder != mCurrentFolder) {
            mCurrentFolder = folder;
            // This calls notifyDataSetChanged() so another call is unnecessary.
            requestRecentFolders();
            return true;
        }
        return false;
    }

    @Override
    public int getCount() {
        // If the recent folders are visible, then one header, recent folders, plus one if the
        // "show all folders" item should be shown
        final int numRecents = mRecentFolderList == null? 0 : mRecentFolderList.size();
        final int numFolders = (mRecentFoldersVisible && numRecents > 0) ?
                (1 + numRecents + (mShowAllFoldersItem ? 1 : 0)) : 0;
        return mNumAccounts + numFolders;
    }

    @Override
    public Object getItem(int position) {
        switch (getItemViewType(position)){
            case TYPE_ACCOUNT:
                return getAccount(position);
            case TYPE_HEADER:
                return "account spinner header";
            case TYPE_ALL_FOLDERS:
                return "show all folders";
            default:
                // Return the folder at this location.
                return mRecentFolderList.get(getRecentOffset(position));
        }
    }

    @Override
    public long getItemId(int position) {
        final int type = getItemViewType(position);
        switch (type) {
            case TYPE_HEADER:
                // Fall-through
            case TYPE_ALL_FOLDERS:
                return type;
            case TYPE_ACCOUNT:
                return getAccount(position).uri.hashCode();
            default:
                return mRecentFolderList.get(getRecentOffset(position)).uri.hashCode();
        }
    }

    /**
     * Returns whether the current account is an invalid offset into the array.
     * @return true if the current account is invalid, and false otherwise.
     */
    private boolean isCurrentAccountInvalid() {
        return mCurrentAccount == null;
    }

    private String getCurrentAccountName() {
        if (isCurrentAccountInvalid()) {
            return "";
        }
        return mCurrentAccount.name;
    }

    private Uri getCurrentAccountUri() {
        if (isCurrentAccountInvalid()) {
            return Uri.EMPTY;
        }
        return mCurrentAccount.uri;
    }

    @Override
    public boolean hasStableIds() {
        // The ID is the hash of the URI of the object.
        return true;
    }

    @Override
    public boolean isEmpty() {
        // No item will be empty.
        return false;
    }

    /**
     * Returns a view (perhaps cached) at the given position.
     * @param position
     * @param parent
     * @param type
     * @return
     */
    final View getCachedView(int position, ViewGroup parent, int type) {
        switch(type) {
            case TYPE_ACCOUNT:
                final View cachedAccount = mAccountViews.get(position);
                if (cachedAccount != null) {
                    return cachedAccount;
                }
                final View newAccount = mInflater.inflate(
                        R.layout.account_switch_spinner_dropdown_account, parent, false);
                mAccountViews.set(position, newAccount);
                return newAccount;
            case TYPE_HEADER:
                if (mHeaderView == null) {
                    mHeaderView = mInflater.inflate(
                            R.layout.account_switch_spinner_dropdown_header, parent, false);
                }
                return mHeaderView;
            case TYPE_FOLDER:
                final int offset = getRecentOffset(position);
                final View cachedFolder = mFolderViews.get(offset);
                if (cachedFolder != null) {
                    return cachedFolder;
                }
                final View newFolder = mInflater.inflate(
                        R.layout.account_switch_spinner_dropdown_folder, parent, false);
                mFolderViews.set(offset, newFolder);
                return newFolder;
            case TYPE_ALL_FOLDERS:
                if (mFooterView == null) {
                    mFooterView = mInflater.inflate(
                            R.layout.account_switch_spinner_dropdown_footer, parent, false);
                }
                return mFooterView;
        }
        return null;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        final int type = getItemViewType(position);
        view = getCachedView(position, parent, type);
        switch (type) {
            case TYPE_ACCOUNT:
                final Account account = getAccount(position);
                if (account == null) {
                    LogUtils.e(LOG_TAG, "AccountSpinnerAdapter(%d): Null account at position.",
                            position);
                    return null;
                }
                final int color = account.color;
                final View colorView = view.findViewById(R.id.dropdown_color);
                if (color != 0) {
                    colorView.setVisibility(View.VISIBLE);
                    colorView.setBackgroundColor(color);
                } else {
                    colorView.setVisibility(View.GONE);
                }
                final Folder inbox = mFolderWatcher.get(account.settings.defaultInbox);
                final int unreadCount = (inbox != null) ? inbox.unreadCount : 0;
                setText(view, R.id.dropdown_first, account.settings.defaultInboxName);
                setText(view, R.id.dropdown_second, account.name);
                setUnreadCount(view, R.id.dropdown_unread, unreadCount);
                break;
            case TYPE_HEADER:
                setText(view, R.id.account_spinner_header_account, getCurrentAccountName());
                break;
            case TYPE_FOLDER:
                final Folder folder = mRecentFolderList.get(getRecentOffset(position));
                Folder.setFolderBlockColor(folder, view.findViewById(R.id.dropdown_color));
                setText(view, R.id.dropdown_first, folder.name);
                setUnreadCount(view, R.id.dropdown_unread, folder.unreadCount);
                break;
            case TYPE_ALL_FOLDERS:
                break;
            default:
                LogUtils.e(LOG_TAG, "AccountSpinnerAdapter.getView(): Unknown type: %d", type);
                break;
        }
        return view;
    }

    /**
     * Sets the text of the TextView to the given text.
     * @param v
     * @param resourceId
     * @param toDisplay the given text
     */
    static private void setText(View v, int resourceId, String toDisplay) {
        final TextView target = (TextView) v.findViewById(resourceId);
        if (target == null) {
            return;
        }
        target.setText(toDisplay);
    }

    /**
     * Sets the unread count, which is a nonzero number or an empty string if there are no unread
     * messages.
     * @param v
     * @param resourceId
     * @param unreadCount
     */
    private final void setUnreadCount(View v, int resourceId, int unreadCount) {
        final TextView target = (TextView) v.findViewById(resourceId);
        if (target == null) {
            return;
        }
        if (unreadCount <= 0) {
            target.setVisibility(View.GONE);
            return;
        }
        target.setVisibility(View.VISIBLE);
        target.setText(Utils.getUnreadCountString(mContext, unreadCount));
    }

    /**
     * Returns the account given position in the spinner.
     * @param position
     * @return the account at the given position.
     */
    private Account getAccount(int position) {
        if (position >= mNumAccounts) {
            return null;
        }
        return mAllAccounts[position];
    }


    @Override
    public boolean isEnabled(int position) {
        // Don't want the user selecting the header.
        final int type = getItemViewType(position);
        return type != TYPE_HEADER;
    }

    @Override
    public boolean areAllItemsEnabled() {
        // The header is not enabled, so return false here.
        return false;
    }

    /**
     * Notify that the folder has changed.
     */
    public void onFolderUpdated(Folder folder) {
        mCurrentFolder = folder;
        notifyDataSetChanged();
    }

    /**
     * Cause a refresh of the recent folders for the current folder and redraw the spinner with
     * the new information.
     */
    private void requestRecentFolders() {
        final Uri uri = mCurrentFolder == null ? null : mCurrentFolder.uri;
        if (mRecentFoldersVisible) {
            mRecentFolderList = mRecentFolders.getRecentFolderList(uri);
            mFolderViews.setSize(mRecentFolderList.size());
            notifyDataSetChanged();
        } else {
            mRecentFolderList = null;
        }
    }

    /**
     * Disable recent folders. Can be enabled again with {@link #enableRecentFolders()}
     */
    public void disableRecentFolders() {
        if (mRecentFoldersVisible) {
            if (mRecentFolderObserver != null) {
                mRecentFolderObserver.unregisterAndDestroy();
                mRecentFolderObserver = null;
            }
            mRecentFolders = null;
            notifyDataSetChanged();
            mRecentFoldersVisible = false;
        }
    }

    /**
     * Enable recent folders. Can be disabled again with {@link #disableRecentFolders()}
     */
    public void enableRecentFolders() {
        if (!mRecentFoldersVisible) {
            mRecentFolderObserver = mSpinnerRecentFolderObserver;
            mRecentFolders = mRecentFolderObserver.initialize(mRecentFolderController);
            mRecentFoldersVisible = true;
            // If we don't have any recent folders, request them now. Otherwise, we'll go with
            // whatever we have, and hope that the freshest ones are received through
            // requestRecentFolders().
            if (mRecentFolderList == null || mRecentFolderList.size() <= 0) {
                // This calls notifyDataSetChanged if a fresh list is available.
                requestRecentFolders();
            } else {
                notifyDataSetChanged();
            }
        }
    }

    /**
     * Destroys the spinner adapter
     */
    public void destroy() {
        mAccountObserver.unregisterAndDestroy();
        if (mRecentFolderObserver != null) {
            mRecentFolderObserver.unregisterAndDestroy();
            mRecentFolderObserver = null;
        }
    }

    /**
     * Returns true if we have any recent folders, false otherwise.
     * @return
     */
    public final boolean hasRecentFolders() {
        return mRecentFolderList != null && mRecentFolderList.size() > 0;
    }
}
