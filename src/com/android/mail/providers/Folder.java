/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.providers;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A folder is a collection of conversations, and perhaps other folders.
 */
public class Folder implements Parcelable, Comparable<Folder> {
    /**
     *
     */
    private static final String FOLDER_UNINITIALIZED = "Uninitialized!";

    // Try to match the order of members with the order of constants in UIProvider.

    /**
     * Unique id of this folder.
     */
    public int id;

    /**
     * The content provider URI that returns this folder for this account.
     */
    public Uri uri;

    /**
     * The human visible name for this folder.
     */
    public String name;

    /**
     * The possible capabilities that this folder supports.
     */
    public int capabilities;

    /**
     * Whether or not this folder has children folders.
     */
    public boolean hasChildren;

    /**
     * How large the synchronization window is: how many days worth of data is retained on the
     * device.
     */
    public int syncWindow;

    /**
     * The content provider URI to return the list of conversations in this
     * folder.
     */
    public Uri conversationListUri;

    /**
     * The content provider URI to return the list of child folders of this folder.
     */
    public Uri childFoldersListUri;

    /**
     * The number of messages that are unread in this folder.
     */
    public int unreadCount;

    /**
     * The total number of messages in this folder.
     */
    public int totalCount;

    /**
     * The content provider URI to force a refresh of this folder.
     */
    public Uri refreshUri;

    /**
     * The current sync status of the folder
     */
    public int syncStatus;

    /**
     * The result of the last sync for this folder
     */
    public int lastSyncResult;

    /**
     * Folder type. 0 is default.
     */
    public int type;

    /**
     * Icon for this folder; 0 implies no icon.
     */
    public long iconResId;

    public String bgColor;
    public String fgColor;

    /**
     * The content provider URI to request additional conversations
     */
    public Uri loadMoreUri;

    /**
     * The possibly empty name of this folder with full hierarchy.
     * The expected format is: parent/folder1/folder2/folder3/folder4
     */
    public String hierarchicalDesc;

    /**
     * Parent folder of this folder, or null if there is none. This is set as
     * part of the execution of the application and not obtained or stored via
     * the provider.
     */
    public Folder parent;

    /** An immutable, empty conversation list */
    public static final Collection<Folder> EMPTY = Collections.emptyList();

    public static final String SPLITTER = "^*^";
    private static final Pattern SPLITTER_REGEX = Pattern.compile("\\^\\*\\^");
    public static final String FOLDERS_SPLIT = "^**^";
    private static final Pattern FOLDERS_SPLIT_REGEX = Pattern.compile("\\^\\*\\*\\^");

    public Folder(Parcel in) {
        id = in.readInt();
        uri = in.readParcelable(null);
        name = in.readString();
        capabilities = in.readInt();
        // 1 for true, 0 for false.
        hasChildren = in.readInt() == 1;
        syncWindow = in.readInt();
        conversationListUri = in.readParcelable(null);
        childFoldersListUri = in.readParcelable(null);
        unreadCount = in.readInt();
        totalCount = in.readInt();
        refreshUri = in.readParcelable(null);
        syncStatus = in.readInt();
        lastSyncResult = in.readInt();
        type = in.readInt();
        iconResId = in.readLong();
        bgColor = in.readString();
        fgColor = in.readString();
        loadMoreUri = in.readParcelable(null);
        hierarchicalDesc = in.readString();
        parent = in.readParcelable(null);
     }

    public Folder(Cursor cursor) {
        id = cursor.getInt(UIProvider.FOLDER_ID_COLUMN);
        uri = Uri.parse(cursor.getString(UIProvider.FOLDER_URI_COLUMN));
        name = cursor.getString(UIProvider.FOLDER_NAME_COLUMN);
        capabilities = cursor.getInt(UIProvider.FOLDER_CAPABILITIES_COLUMN);
        // 1 for true, 0 for false.
        hasChildren = cursor.getInt(UIProvider.FOLDER_HAS_CHILDREN_COLUMN) == 1;
        syncWindow = cursor.getInt(UIProvider.FOLDER_SYNC_WINDOW_COLUMN);
        String convList = cursor.getString(UIProvider.FOLDER_CONVERSATION_LIST_URI_COLUMN);
        conversationListUri = !TextUtils.isEmpty(convList) ? Uri.parse(convList) : null;
        String childList = cursor.getString(UIProvider.FOLDER_CHILD_FOLDERS_LIST_COLUMN);
        childFoldersListUri = (hasChildren && !TextUtils.isEmpty(childList)) ? Uri.parse(childList)
                : null;
        unreadCount = cursor.getInt(UIProvider.FOLDER_UNREAD_COUNT_COLUMN);
        totalCount = cursor.getInt(UIProvider.FOLDER_TOTAL_COUNT_COLUMN);
        String refresh = cursor.getString(UIProvider.FOLDER_REFRESH_URI_COLUMN);
        refreshUri = !TextUtils.isEmpty(refresh) ? Uri.parse(refresh) : null;
        syncStatus = cursor.getInt(UIProvider.FOLDER_SYNC_STATUS_COLUMN);
        lastSyncResult = cursor.getInt(UIProvider.FOLDER_LAST_SYNC_RESULT_COLUMN);
        type = cursor.getInt(UIProvider.FOLDER_TYPE_COLUMN);
        iconResId = cursor.getLong(UIProvider.FOLDER_ICON_RES_ID_COLUMN);
        bgColor = cursor.getString(UIProvider.FOLDER_BG_COLOR_COLUMN);
        fgColor = cursor.getString(UIProvider.FOLDER_FG_COLOR_COLUMN);
        String loadMore = cursor.getString(UIProvider.FOLDER_LOAD_MORE_URI_COLUMN);
        loadMoreUri = !TextUtils.isEmpty(loadMore) ? Uri.parse(loadMore) : null;
        hierarchicalDesc = cursor.getString(UIProvider.FOLDER_HIERARCHICAL_DESC_COLUMN);
        parent = null;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeParcelable(uri, 0);
        dest.writeString(name);
        dest.writeInt(capabilities);
        // 1 for true, 0 for false.
        dest.writeInt(hasChildren ? 1 : 0);
        dest.writeInt(syncWindow);
        dest.writeParcelable(conversationListUri, 0);
        dest.writeParcelable(childFoldersListUri, 0);
        dest.writeInt(unreadCount);
        dest.writeInt(totalCount);
        dest.writeParcelable(refreshUri, 0);
        dest.writeInt(syncStatus);
        dest.writeInt(lastSyncResult);
        dest.writeInt(type);
        dest.writeLong(iconResId);
        dest.writeString(bgColor);
        dest.writeString(fgColor);
        dest.writeParcelable(loadMoreUri, 0);
        dest.writeString(hierarchicalDesc);
        dest.writeParcelable(parent, 0);
    }

    /**
     * Construct a folder that queries for search results. Do not call on the UI
     * thread.
     */
    public static CursorLoader forSearchResults(Account account, String query, Context context) {
        if (account.searchUri != null) {
            Builder searchBuilder = account.searchUri.buildUpon();
            searchBuilder.appendQueryParameter(UIProvider.SearchQueryParameters.QUERY, query);
            Uri searchUri = searchBuilder.build();
            return new CursorLoader(context, searchUri, UIProvider.FOLDERS_PROJECTION, null, null,
                    null);
        }
        return null;
    }

    public static HashMap<Uri, Folder> hashMapForFolders(ArrayList<Folder> rawFolders) {
        final HashMap<Uri, Folder> folders = new HashMap<Uri, Folder>();
        for (Folder f : rawFolders) {
            folders.put(f.uri, f);
        }
        return folders;
    }

    private static Uri getValidUri(String uri) {
        if (TextUtils.isEmpty(uri)) {
            return null;
        }
        return Uri.parse(uri);
    }

    /**
     * Constructor that leaves everything uninitialized. For use only by {@link #serialize()}
     * which is responsible for filling in all the fields
     */
    public Folder() {
        name = FOLDER_UNINITIALIZED;
    }

    @SuppressWarnings("hiding")
    public static final Creator<Folder> CREATOR = new Creator<Folder>() {
        @Override
        public Folder createFromParcel(Parcel source) {
            return new Folder(source);
        }

        @Override
        public Folder[] newArray(int size) {
            return new Folder[size];
        }
    };

    @Override
    public int describeContents() {
        // Return a sort of version number for this parcelable folder. Starting with zero.
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Folder)) {
            return false;
        }
        return Objects.equal(uri, ((Folder) o).uri);
    }

    @Override
    public int hashCode() {
        return uri == null ? 0 : uri.hashCode();
    }

    @Override
    public int compareTo(Folder other) {
        return name.compareToIgnoreCase(other.name);
    }

    /**
     * Create a Folder map from a string of serialized folders. This can only be done on the output
     * of {@link #serialize(Map)}.
     * @param serializedFolder A string obtained from {@link #serialize(Map)}
     * @return a Map of folder name to folder.
     */
    public static Map<String, Folder> parseFoldersFromString(String serializedFoldersString) {
        if (TextUtils.isEmpty(serializedFoldersString)) {
            return null;
        }
        Map<String, Folder> folderMap = Maps.newHashMap();
        ArrayList<Folder> folders = Folder.getFoldersArray(serializedFoldersString);
        for (Folder folder : folders) {
            if (folder.name != FOLDER_UNINITIALIZED) {
                folderMap.put(folder.name, folder);
            }
        }
        return folderMap;
    }

    /**
     * Returns a boolean indicating whether network activity (sync) is occuring for this folder.
     */
    public boolean isSyncInProgress() {
        return UIProvider.SyncStatus.isSyncInProgress(syncStatus);
    }

    /**
     * Serialize the given list of folders
     * @param folderMap A valid map of folder names to Folders
     * @return a string containing a serialized output of folder maps.
     */
    public static String serialize(Map<String, Folder> folderMap) {
        return getSerializedFolderString(folderMap.values());
    }

    public boolean supportsCapability(int capability) {
        return (capabilities & capability) != 0;
    }

    // Show black text on a transparent swatch for system folders, effectively hiding the
    // swatch (see bug 2431925).
    public static void setFolderBlockColor(Folder folder, View colorBlock) {
        if (colorBlock == null) {
            return;
        }
        final boolean showBg = !TextUtils.isEmpty(folder.bgColor);
        final int backgroundColor = showBg ? Integer.parseInt(folder.bgColor) : 0;
        if (!showBg) {
            colorBlock.setBackgroundDrawable(null);
            colorBlock.setVisibility(View.GONE);
        } else {
            PaintDrawable paintDrawable = new PaintDrawable();
            paintDrawable.getPaint().setColor(backgroundColor);
            colorBlock.setBackgroundDrawable(paintDrawable);
            colorBlock.setVisibility(View.VISIBLE);
        }
    }

    public static void setIcon(Folder folder, ImageView iconView) {
        if (iconView == null) {
            return;
        }
        final long icon = folder.iconResId;
        if (icon > 0) {
            iconView.setImageResource((int)icon);
            iconView.setVisibility(View.VISIBLE);
        } else {
            iconView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Return if the type of the folder matches a provider defined folder.
     */
    public boolean isProviderFolder() {
        return type != UIProvider.FolderType.DEFAULT;
    }

    public int getBackgroundColor(int defaultColor) {
        return TextUtils.isEmpty(bgColor) ? defaultColor : Integer.parseInt(bgColor);
    }

    public int getForegroundColor(int defaultColor) {
        return TextUtils.isEmpty(fgColor) ? defaultColor : Integer.parseInt(fgColor);
    }

    public static String getSerializedFolderString(Collection<Folder> folders) {
        final StringBuilder folderList = new StringBuilder();
        int i = 0;
        for (Folder folderEntry : folders) {
            folderList.append(Folder.toString(folderEntry));
            if (i < folders.size()) {
                folderList.append(FOLDERS_SPLIT);
            }
            i++;
        }
        return folderList.toString();
    }


    public static Folder fromString(String inString) {
        if (TextUtils.isEmpty(inString)) {
            return null;
        }
        Folder f = new Folder();
        String[] split = TextUtils.split(inString, SPLITTER_REGEX);
        if (split.length < 20) {
            return null;
        }
        f.id = Integer.parseInt(split[0]);
        f.uri = Folder.getValidUri(split[1]);
        f.name = split[2];
        f.hasChildren = Integer.parseInt(split[3]) != 0;
        f.capabilities = Integer.parseInt(split[4]);
        f.syncWindow = Integer.parseInt(split[5]);
        f.conversationListUri = getValidUri(split[6]);
        f.childFoldersListUri = getValidUri(split[7]);
        f.unreadCount = Integer.parseInt(split[8]);
        f.totalCount = Integer.parseInt(split[9]);
        f.refreshUri = getValidUri(split[10]);
        f.syncStatus = Integer.parseInt(split[11]);
        f.lastSyncResult = Integer.parseInt(split[12]);
        f.type = Integer.parseInt(split[13]);
        f.iconResId = Integer.parseInt(split[14]);
        f.bgColor = split[15];
        f.fgColor = split[16];
        f.loadMoreUri = getValidUri(split[17]);
        f.hierarchicalDesc = split[18];
        f.parent = Folder.fromString(split[19]);
        return f;
    }

    public static String toString(Folder folderEntry) {
        StringBuilder builder = new StringBuilder();
        builder.append(folderEntry.id);
        builder.append(SPLITTER);
        builder.append(folderEntry.uri);
        builder.append(SPLITTER);
        builder.append(folderEntry.name);
        builder.append(SPLITTER);
        builder.append(folderEntry.hasChildren ? 1 : 0);
        builder.append(SPLITTER);
        builder.append(folderEntry.capabilities);
        builder.append(SPLITTER);
        builder.append(folderEntry.syncWindow);
        builder.append(SPLITTER);
        builder.append(folderEntry.conversationListUri);
        builder.append(SPLITTER);
        builder.append(folderEntry.childFoldersListUri);
        builder.append(SPLITTER);
        builder.append(folderEntry.unreadCount);
        builder.append(SPLITTER);
        builder.append(folderEntry.totalCount);
        builder.append(SPLITTER);
        builder.append(folderEntry.refreshUri);
        builder.append(SPLITTER);
        builder.append(folderEntry.syncStatus);
        builder.append(SPLITTER);
        builder.append(folderEntry.lastSyncResult);
        builder.append(SPLITTER);
        builder.append(folderEntry.type);
        builder.append(SPLITTER);
        builder.append(folderEntry.iconResId);
        builder.append(SPLITTER);
        builder.append(folderEntry.bgColor);
        builder.append(SPLITTER);
        builder.append(folderEntry.fgColor);
        builder.append(SPLITTER);
        builder.append(folderEntry.loadMoreUri);
        builder.append(SPLITTER);
        builder.append(folderEntry.hierarchicalDesc);
        builder.append(SPLITTER);
        if (folderEntry.parent != null) {
            builder.append(Folder.toString(folderEntry.parent));
        } else {
            builder.append("");
        }
        return builder.toString();
    }

    /**
     * Returns a comma separated list of folder URIs for all the folders in the collection.
     * @param folders
     * @return
     */
    public final static String getUriString(Collection<Folder> folders) {
        final StringBuilder uris = new StringBuilder();
        boolean first = true;
        for (Folder f : folders) {
            if (first) {
                first = false;
            } else {
                uris.append(',');
            }
            uris.append(f.uri.toString());
        }
        return uris.toString();
    }


    /**
     * Get an array of folders from a rawFolders string.
     */
    public static ArrayList<Folder> getFoldersArray(String rawFolders) {
        if (TextUtils.isEmpty(rawFolders)) {
            return null;
        }
        ArrayList<Folder> folders = new ArrayList<Folder>();
        String[] split = TextUtils.split(rawFolders, FOLDERS_SPLIT_REGEX);
        Folder f;
        for (String folder : split) {
            f = Folder.fromString(folder);
            if (f != null) {
                folders.add(f);
            }
        }
        return folders;
    }

    /**
     * Get just the uri's from an arraylist of folders.
     */
    public final static String[] getUriArray(ArrayList<Folder> folders) {
        if (folders == null || folders.size() == 0) {
            return new String[0];
        }
        String[] folderUris = new String[folders.size()];
        int i = 0;
        for (Folder folder : folders) {
            folderUris[i] = folder.uri.toString();
            i++;
        }
        return folderUris;
    }

    /**
     * Returns true if a conversation assigned to the needle will be assigned to the collection of
     * folders in the haystack. False otherwise. This method is safe to call with null
     * arguments.
     * This method returns true under two circumstances
     * <ul><li> If the URI of the needle was found in the collection of URIs that comprise the
     * haystack.
     * </li><li> If the needle is of the type Inbox, and at least one of the folders in the haystack
     * are of type Inbox. <em>Rationale</em>: there are special folders that are marked as inbox,
     * and the user might not have the control to assign conversations to them. This happens for
     * the Priority Inbox in Gmail. When you assign a conversation to an Inbox folder, it will
     * continue to appear in the Priority Inbox. However, the URI of Priority Inbox and Inbox will
     * be different. So a direct equality check is insufficient.
     * </li></ul>
     * @param haystack a collection of folders, possibly overlapping
     * @param needle a folder
     * @return true if a conversation inside the needle will be in the folders in the haystack.
     */
    public final static boolean containerIncludes(Collection<Folder> haystack, Folder needle) {
        // If the haystack is empty, it cannot contain anything.
        if (haystack == null || haystack.size() <= 0) {
            return false;
        }
        // The null folder exists everywhere.
        if (needle == null) {
            return true;
        }
        boolean hasInbox = false;
        // Get currently active folder info and compare it to the list
        // these conversations have been given; if they no longer contain
        // the selected folder, delete them from the list.
        final Uri toFind = needle.uri;
        for (Folder f : haystack) {
            if (toFind.equals(f.uri)) {
                return true;
            }
            hasInbox |= (f.type == UIProvider.FolderType.INBOX);
        }
        // Did not find the URI of needle directly. If the needle is an Inbox and one of the folders
        // was an inbox, then the needle is contained (check Javadoc for explanation).
        final boolean needleIsInbox = (needle.type == UIProvider.FolderType.INBOX);
        return needleIsInbox ? hasInbox : false;
    }

    /**
     * Returns a collection of a single folder. This method always returns a valid collection
     * even if the input folder is null.
     * @param in a folder, possibly null.
     * @return a collection of the folder.
     */
    public static Collection<Folder> listOf(Folder in) {
        final Collection<Folder> target = (in == null) ? EMPTY : ImmutableList.of(in);
        return target;
    }
}
