/*
 * Copyright (C) 2013 The Android Open Source Project
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


package com.android.mail.adapter;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.ui.AccountItemView;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.FolderItemView;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/** An account, a system folder, a recent folder, or a header (a resource string) */
public class DrawerItem {
    private static final String LOG_TAG = LogTag.getLogTag();
    // TODO(viki): Remove this: http://b/8478715
    public final int mPosition;
    public final Folder mFolder;
    public final Account mAccount;
    public final int mResource;
    /** True if the drawer item represents the current account, false otherwise */
    public final boolean mIsCurrentAccount;
    /** Either {@link #VIEW_ACCOUNT}, {@link #VIEW_FOLDER} or {@link #VIEW_HEADER} */
    public final int mType;
    /** A normal folder, also a child, if a parent is specified. */
    public static final int VIEW_FOLDER = 0;
    /** A text-label which serves as a header in sectioned lists. */
    public static final int VIEW_HEADER = 1;
    /** An account object, which allows switching accounts rather than folders. */
    public static final int VIEW_ACCOUNT = 2;
    /** An expandable object for expanding/collapsing more of the list */
    public static final int VIEW_WAITING_FOR_SYNC = 3;
    /** The value (1-indexed) of the last View type.  Useful when returning the number of types. */
    private static final int LAST_FIELD = VIEW_WAITING_FOR_SYNC + 1;

    /** TODO: On adding another type, be sure to change getViewTypes() */

    /** The parent activity */
    private final ControllableActivity mActivity;
    private final LayoutInflater mInflater;

    /**
     * Either {@link #FOLDER_SYSTEM}, {@link #FOLDER_RECENT} or {@link #FOLDER_USER} when
     * {@link #mType} is {@link #VIEW_FOLDER}, or an {@link #ACCOUNT} in the case of
     * accounts, and {@link #INERT_HEADER} otherwise.
     */
    public final int mFolderType;
    /** Non existent item or folder type not yet set */
    public static final int UNSET = 0;
    /** An unclickable text-header visually separating the different types. */
    public static final int INERT_HEADER = 0;
    /** A system-defined folder: Inbox/Drafts, ...*/
    public static final int FOLDER_SYSTEM = 1;
    /** A folder from whom a conversation was recently viewed */
    public static final int FOLDER_RECENT = 2;
    /** A user created folder */
    public static final int FOLDER_USER = 3;
    /** An entry for the accounts the user has on the device. */
    public static final int ACCOUNT = 4;

    /** True if this view is enabled, false otherwise. */
    private boolean isEnabled = false;

    @Override
    public String toString() {
        switch(mType) {
            case VIEW_FOLDER:
                return folderToString();
            case VIEW_HEADER:
                return headerToString();
            case VIEW_ACCOUNT:
                return accountToString();
            case VIEW_WAITING_FOR_SYNC:
                return waitToString();
        }
        // Should never come here.
        return null;
    }

    /**
     * Creates a drawer item with every instance variable specified.
     *
     * @param type the type of the item. This must be a VIEW_* element
     * @param activity the underlying activity
     * @param folder a non-null folder, if this is a folder type
     * @param folderType the type of the folder. For folders this is:
     *            {@link #FOLDER_SYSTEM}, {@link #FOLDER_RECENT},
     *            {@link #FOLDER_USER}, or for non-folders this is
     *            {@link #ACCOUNT}, or {@link #INERT_HEADER}
     * @param account the account object, for an account drawer element
     * @param resource either the string resource for a header, or the unread
     *            count for an account.
     * @param isCurrentAccount true if this item is the current account
     * @param position the cursor position for a folder object, -1 otherwise.
     */
    private DrawerItem(int type, ControllableActivity activity, Folder folder, int folderType,
            Account account, int resource, boolean isCurrentAccount, int position) {
        mActivity = activity;
        mFolder = folder;
        mFolderType = folderType;
        mAccount = account;
        mResource = resource;
        mIsCurrentAccount = isCurrentAccount;
        mInflater = LayoutInflater.from(activity.getActivityContext());
        mType = type;
        mPosition = position;
    }

    /**
     * Create a folder item with the given type.
     * @param activity the underlying activity
     * @param folder a folder that this item represents
     * @param folderType one of {@link #FOLDER_SYSTEM}, {@link #FOLDER_RECENT} or
     * {@link #FOLDER_USER}
     * @param cursorPosition the position of the folder in the underlying cursor.
     * @return a drawer item for the folder.
     */
    public static DrawerItem ofFolder(ControllableActivity activity, Folder folder,
            int folderType, int cursorPosition) {
        return new DrawerItem(VIEW_FOLDER, activity, folder,  folderType, null, -1, false,
                cursorPosition);
    }

    private String folderToString() {
        final StringBuilder sb = new StringBuilder("[DrawerItem ");
        sb.append(" VIEW_FOLDER ");
        sb.append(", mFolder=");
        sb.append(mFolder);
        sb.append(", mFolderType=");
        sb.append(mFolderType);
        sb.append("]");
        return sb.toString();
    }

    /**
     * Creates an item from an account.
     * @param activity the underlying activity
     * @param account the account to create a drawer item for
     * @param unreadCount the unread count of the account, pass zero if
     * @param isCurrentAccount true if the account is the current account, false otherwise
     * @return a drawer item for the account.
     */
    public static DrawerItem ofAccount(ControllableActivity activity, Account account,
            int unreadCount, boolean isCurrentAccount) {
        return new DrawerItem(VIEW_ACCOUNT, activity, null, ACCOUNT, account, unreadCount,
                isCurrentAccount, -1);
    }

    private String accountToString() {
        final StringBuilder sb = new StringBuilder("[DrawerItem ");
        sb.append(" VIEW_ACCOUNT ");
        sb.append(", mAccount=");
        sb.append(mAccount);
        sb.append("]");
        return sb.toString();
    }

    /**
     * Create a header item with a string resource.
     * @param activity the underlying activity
     * @param resource the string resource: R.string.all_folders_heading
     * @return a drawer item for the header.
     */
    public static DrawerItem ofHeader(ControllableActivity activity, int resource) {
        return new DrawerItem(VIEW_HEADER, activity, null, INERT_HEADER, null, resource, false, -1);
    }

    private String headerToString() {
        final StringBuilder sb = new StringBuilder("[DrawerItem ");
        sb.append(" VIEW_HEADER ");
        sb.append(", mResource=");
        sb.append(mResource);
        sb.append("]");
        return sb.toString();
    }

    /**
     * Create a "waiting for initialization" item.
     * @param activity the underlying activity
     * @return a drawer item with an indeterminate progress indicator.
     */
    public static DrawerItem forWaitView(ControllableActivity activity) {
        return new DrawerItem(VIEW_WAITING_FOR_SYNC, activity, null, INERT_HEADER, null, -1,
                false, -1);
    }

    private String waitToString() {
        return "[DrawerItem VIEW_WAITING_FOR_SYNC ]";
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        final View result;
        switch (mType) {
            case VIEW_FOLDER:
                result = getFolderView(position, convertView, parent);
                break;
            case VIEW_HEADER:
                result = getHeaderView(position, convertView, parent);
                break;
            case VIEW_ACCOUNT:
                result = getAccountView(position, convertView, parent);
                break;
            case VIEW_WAITING_FOR_SYNC:
                result = getEmptyView(position, convertView, parent);
                break;
            default:
                LogUtils.wtf(LOG_TAG, "DrawerItem.getView(%d) for an invalid type!", mType);
                result = null;
        }
        return result;
    }

    /**
     * Book-keeping for how many different view types there are. Be sure to
     * increment this appropriately once adding more types as drawer items
     * @return number of different types of view items
     */
    public static int getViewTypes() {
        return LAST_FIELD;
    }

    /**
     * Returns whether this view is enabled or not.
     * @return
     */
    public boolean isItemEnabled(Uri currentAccountUri) {
        switch (mType) {
            case VIEW_HEADER :
                // Headers are never enabled.
                return false;
            case VIEW_FOLDER :
                // Folders are always enabled.
                return true;
            case VIEW_ACCOUNT:
                // Accounts are only enabled if they are not the current account.
                return !currentAccountUri.equals(mAccount.uri);
            case VIEW_WAITING_FOR_SYNC:
                // Waiting for sync cannot be tapped, so never enabled.
                return false;
            default:
                LogUtils.wtf(LOG_TAG, "DrawerItem.isItemEnabled() for invalid type %d", mType);
                return false;
        }
    }

    /**
     * Returns whether this view is highlighted or not.
     *
     * @param currentFolder
     * @param currentType
     * @return
     */
    public boolean isHighlighted(Folder currentFolder, int currentType){
        switch (mType) {
            case VIEW_HEADER :
                // Headers are never highlighted
                return false;
            case VIEW_FOLDER:
                // True if folder types and URIs are the same
                if (currentFolder != null && mFolder != null) {
                    return (mFolderType == currentType) && mFolder.uri.equals(currentFolder.uri);
                }
                return false;
            case VIEW_ACCOUNT:
                // Accounts are never highlighted
                return false;
            case VIEW_WAITING_FOR_SYNC:
                // Waiting for sync cannot be tapped, so never highlighted.
                return false;
            default:
                LogUtils.wtf(LOG_TAG, "DrawerItem.isHighlighted() for invalid type %d", mType);
                return false;
        }
    }

    /**
     * Return a view for an account object.
     * @param position a zero indexed position in to the list.
     * @param convertView a view, possibly null, to be recycled.
     * @param parent the parent viewgroup to attach to.
     * @return a view to display at this position.
     */
    private View getAccountView(int position, View convertView, ViewGroup parent) {
        final AccountItemView accountItemView;
        if (convertView != null) {
            accountItemView = (AccountItemView) convertView;
        } else {
            accountItemView =
                    (AccountItemView) mInflater.inflate(R.layout.account_item, null, false);
        }
        accountItemView.bind(mAccount, mResource);
        accountItemView.setCurrentAccount(mIsCurrentAccount);
        View v = accountItemView.findViewById(R.id.color_block);
        v.setBackgroundColor(mAccount.color);
        return accountItemView;
    }

    /**
     * Returns a text divider between sections.
     * @param convertView a previous view, perhaps null
     * @param parent the parent of this view
     * @return a text header at the given position.
     */
    private View getHeaderView(int position, View convertView, ViewGroup parent) {
        final TextView headerView;
        if (convertView != null) {
            headerView = (TextView) convertView;
        } else {
            headerView = (TextView) mInflater.inflate(
                    R.layout.folder_list_header, parent, false);
        }
        headerView.setText(mResource);
        return headerView;
    }

    /**
     * Return a folder: either a parent folder or a normal (child or flat)
     * folder.
     * @param position a zero indexed position into the top level list.
     * @param convertView a view, possibly null, to be recycled.
     * @param parent the parent hosting this view.
     * @return a view showing a folder at the given position.
     */
    private View getFolderView(int position, View convertView, ViewGroup parent) {
        final FolderItemView folderItemView;
        if (convertView != null) {
            folderItemView = (FolderItemView) convertView;
        } else {
            folderItemView =
                    (FolderItemView) mInflater.inflate(R.layout.folder_item, null, false);
        }
        folderItemView.bind(mFolder, mActivity);
        Folder.setFolderBlockColor(mFolder, folderItemView.findViewById(R.id.color_block));
        Folder.setIcon(mFolder, (ImageView) folderItemView.findViewById(R.id.folder_icon));
        return folderItemView;
    }

    /**
     * Return a view for the 'Waiting for sync' item with the indeterminate progress indicator.
     * @param position a zero indexed position into the top level list.
     * @param convertView a view, possibly null, to be recycled.
     * @param parent the parent hosting this view.
     * @return a view for "Waiting for sync..." at given position.
     */
    private View getEmptyView(int position, View convertView, ViewGroup parent) {
        final ViewGroup emptyView;
        if (convertView != null) {
            emptyView = (ViewGroup) convertView;
        } else {
            emptyView = (ViewGroup) mInflater.inflate(R.layout.drawer_empty_view, parent, false);
        }
        return emptyView;
    }

}

