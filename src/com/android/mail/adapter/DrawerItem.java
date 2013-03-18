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
    public int mPosition;
    public final Folder mFolder;
    public final Account mAccount;
    public final int mResource;
    /** True if expand item view for expanding accounts. False otherwise */
    public final boolean mIsExpandForAccount;
    /** Either {@link #VIEW_ACCOUNT}, {@link #VIEW_FOLDER} or {@link #VIEW_HEADER} */
    public final int mType;
    /** A normal folder, also a child, if a parent is specified. */
    public static final int VIEW_FOLDER = 0;
    /** A text-label which serves as a header in sectioned lists. */
    public static final int VIEW_HEADER = 1;
    /** An account object, which allows switching accounts rather than folders. */
    public static final int VIEW_ACCOUNT = 2;
    /** An expandable object for expanding/collapsing more of the list */
    public static final int VIEW_MORE = 3;
    /** TODO: On adding another type, be sure to change getViewTypes() */

    /** The parent activity */
    private final ControllableActivity mActivity;
    private final LayoutInflater mInflater;

    /**
     * Either {@link #FOLDER_SYSTEM}, {@link #FOLDER_RECENT} or {@link #FOLDER_USER} when
     * {@link #mType} is {@link #VIEW_FOLDER}, or an {@link #ACCOUNT} in the case of
     * accounts, {@link #EXPAND} for expand blocks, and {@link #INERT_HEADER} otherwise.
     */
    public final int mFolderType;
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
    /** A clickable block to expand list as requested */
    public static final int EXPAND = 5;

    /** True if this view is enabled, false otherwise. */
    private boolean isEnabled = false;

    /**
     * Create a folder item with the given type.
     * @param folder a folder that this item represents
     * @param folderType one of {@link #FOLDER_SYSTEM}, {@link #FOLDER_RECENT} or
     * {@link #FOLDER_USER}
     */
    public DrawerItem(ControllableActivity activity, Folder folder, int folderType,
            int cursorPosition) {
        mActivity = activity;
        mInflater = LayoutInflater.from(mActivity.getActivityContext());
        mFolder = folder;
        mAccount = null;
        mResource = -1;
        mType = VIEW_FOLDER;
        mFolderType = folderType;
        mPosition = cursorPosition;
        mIsExpandForAccount = false;
    }

    /**
     * Creates an item from an account.
     * @param account an account that this item represents.
     */
    public DrawerItem(ControllableActivity activity, Account account, int count) {
        mActivity = activity;
        mInflater = LayoutInflater.from(mActivity.getActivityContext());
        mFolder = null;
        mType = VIEW_ACCOUNT;
        mResource = count;
        mFolderType = ACCOUNT;
        mAccount = account;
        mIsExpandForAccount = false;
    }

    /**
     * Create a header item with a string resource.
     * @param resource the string resource: R.string.all_folders_heading
     */
    public DrawerItem(ControllableActivity activity, int resource) {
        mActivity = activity;
        mInflater = LayoutInflater.from(mActivity.getActivityContext());
        mFolder = null;
        mResource = resource;
        mType = VIEW_HEADER;
        mFolderType = INERT_HEADER;
        mAccount = null;
        mIsExpandForAccount = false;
    }

    /**
     * Creates an item for expanding or contracting for emails/items
     * @param resource the string resource: R.string.folder_list_*
     * @param isExpand true if "more" and false if "less"
     */
    public DrawerItem(ControllableActivity activity, int resource, boolean isExpandForAccount) {
        mActivity = activity;
        mInflater = LayoutInflater.from(mActivity.getActivityContext());
        mFolder = null;
        mType = VIEW_MORE;
        mResource = resource;
        mFolderType = EXPAND;
        mAccount = null;
        mIsExpandForAccount = isExpandForAccount;
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
            case VIEW_MORE:
                result = getExpandView(position, convertView, parent);
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
        return VIEW_MORE + 1;
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
            case VIEW_MORE:
                // 'Expand/Collapse' items are always enabled.
                return true;
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
            case VIEW_FOLDER :
                return (mFolderType == currentType) && mFolder.uri.equals(currentFolder.uri);
            case VIEW_ACCOUNT:
                // Accounts are never highlighted
                return false;
            case VIEW_MORE:
                // Expand/Collapse items are never highlighted
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
        // Shoe-horn an account object into a Folder DrawerItem for now.
        // TODO(viki): Stop this ugly shoe-horning and use a real layout.
        final FolderItemView folderItemView;
        if (convertView != null) {
            folderItemView = (FolderItemView) convertView;
        } else {
            folderItemView =
                    (FolderItemView) mInflater.inflate(R.layout.folder_item, null, false);
        }
        // Temporary. Ideally we want a totally different item.
        folderItemView.bind(mAccount, mActivity, mResource);
        View v = folderItemView.findViewById(R.id.color_block);
        v.setBackgroundColor(mAccount.color);
        v = folderItemView.findViewById(R.id.folder_icon);
        v.setVisibility(View.GONE);
        return folderItemView;
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
     * Return a view for the 'Expand/Collapse' item.
     * @param position a zero indexed position into the top level list.
     * @param convertView a view, possibly null, to be recycled.
     * @param parent the parent hosting this view.
     * @return a view showing an item for folder/account expansion at given position.
     */
    private View getExpandView(int position, View convertView, ViewGroup parent) {
        final ViewGroup headerView;
        if (convertView != null) {
            headerView = (ViewGroup) convertView;
        } else {
            headerView = (ViewGroup) mInflater.inflate(
                    R.layout.folder_expand_item, parent, false);
        }
        TextView direction =
                (TextView)headerView.findViewById(R.id.folder_expand_text);
        if(direction != null) {
            direction.setText(mResource);
        }
        return headerView;
    }
}

