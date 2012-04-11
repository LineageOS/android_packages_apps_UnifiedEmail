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

package com.android.mail.browse;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.CursorWrapper;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.android.mail.providers.Conversation;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.ConversationListQueryParameters;
import com.android.mail.providers.UIProvider.ConversationOperations;
import com.android.mail.utils.LogUtils;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * ConversationCursor is a wrapper around a conversation list cursor that provides update/delete
 * caching for quick UI response. This is effectively a singleton class, as the cache is
 * implemented as a static HashMap.
 */
public final class ConversationCursor implements Cursor {
    private static final String TAG = "ConversationCursor";
    private static final boolean DEBUG = true;  // STOPSHIP Set to false before shipping

    // The cursor instantiator's activity
    private static Activity sActivity;
    // The cursor underlying the caching cursor
    @VisibleForTesting
    static Wrapper sUnderlyingCursor;
    // The new cursor obtained via a requery
    private static volatile Wrapper sRequeryCursor;
    // A mapping from Uri to updated ContentValues
    private static HashMap<String, ContentValues> sCacheMap = new HashMap<String, ContentValues>();
    // Cache map lock (will be used only very briefly - few ms at most)
    private static Object sCacheMapLock = new Object();
    // A deleted row is indicated by the presence of DELETED_COLUMN in the cache map
    private static final String DELETED_COLUMN = "__deleted__";
    // An row cached during a requery is indicated by the presence of REQUERY_COLUMN in the map
    private static final String REQUERY_COLUMN = "__requery__";
    // A sentinel value for the "index" of the deleted column; it's an int that is otherwise invalid
    private static final int DELETED_COLUMN_INDEX = -1;
    // Empty deletion list
    private static final ArrayList<Integer> EMPTY_DELETION_LIST = new ArrayList<Integer>();
    // The current conversation cursor
    private static ConversationCursor sConversationCursor;
    // The index of the Uri whose data is reflected in the cached row
    // Updates/Deletes to this Uri are cached
    private static int sUriColumnIndex;
    // The listeners registered for this cursor
    private static ArrayList<ConversationListener> sListeners =
        new ArrayList<ConversationListener>();
    // The ConversationProvider instance
    @VisibleForTesting
    static ConversationProvider sProvider;
    // The runnable executing a refresh (query of underlying provider)
    private static RefreshTask sRefreshTask;
    // Set when we've sent refreshReady() to listeners
    private static boolean sRefreshReady = false;
    // Set when we've sent refreshRequired() to listeners
    private static boolean sRefreshRequired = false;
    // Our sequence count (for changes sent to underlying provider)
    private static int sSequence = 0;
    // Whether our first query on this cursor should include a limit
    private static boolean sInitialConversationLimit = false;

    // Column names for this cursor
    private final String[] mColumnNames;
    // The resolver for the cursor instantiator's context
    private static ContentResolver mResolver;
    // An observer on the underlying cursor (so we can detect changes from outside the UI)
    private final CursorObserver mCursorObserver;
    // Whether our observer is currently registered with the underlying cursor
    private boolean mCursorObserverRegistered = false;

    // The current position of the cursor
    private int mPosition = -1;

    /**
     * Allow UI elements to subscribe to changes that other UI elements might make to this data.
     * This short circuits the usual DB round-trip needed for data to propagate across disparate
     * UI elements.
     * <p>
     * A UI element that receives a notification on this channel should just update its existing
     * view, and should not trigger a full refresh.
     */
    private final DataSetObservable mDataSetObservable = new DataSetObservable();

    // The number of cached deletions from this cursor (used to quickly generate an accurate count)
    private static int sDeletedCount = 0;

    // Parameters passed to the underlying query
    private static Uri qUri;
    private static String[] qProjection;

    private ConversationCursor(Wrapper cursor, Activity activity, String messageListColumn) {
        sConversationCursor = this;
        // If we have an existing underlying cursor, make sure it's closed
        if (sUnderlyingCursor != null) {
            sUnderlyingCursor.close();
        }
        sUnderlyingCursor = cursor;
        sListeners.clear();
        sRefreshRequired = false;
        sRefreshReady = false;
        sRefreshTask = null;
        mCursorObserver = new CursorObserver();
        resetCursor(null);
        mColumnNames = cursor.getColumnNames();
        sUriColumnIndex = cursor.getColumnIndex(messageListColumn);
        if (sUriColumnIndex < 0) {
            throw new IllegalArgumentException("Cursor must include a message list column");
        }
    }

    /**
     * Method to initiaze the ConversationCursor state before an instance is created
     * This is needed to workaround the crash reported in bug 6185304
     * Also, we set the flag indicating whether to use a limit on the first conversation query
     */
    public static void initialize(Activity activity, boolean initialConversationLimit) {
        sActivity = activity;
        sInitialConversationLimit = initialConversationLimit;
        mResolver = activity.getContentResolver();
    }

    /**
     * Create a ConversationCursor; this should be called by the ListActivity using that cursor
     * @param activity the activity creating the cursor
     * @param messageListColumn the column used for individual cursor items
     * @param uri the query uri
     * @param projection the query projecion
     * @param selection the query selection
     * @param selectionArgs the query selection args
     * @param sortOrder the query sort order
     * @return a ConversationCursor
     */
    public static ConversationCursor create(Activity activity, String messageListColumn, Uri uri,
            String[] projection) {
        sActivity = activity;
        mResolver = activity.getContentResolver();
        synchronized (sCacheMapLock) {
            try {
                // First, let's see if we already have a cursor
                if (sConversationCursor != null) {
                    // If it's the same, just clean up
                    if (qUri.equals(uri) && !sRefreshRequired && sRefreshTask == null) {
                        if (sRefreshReady) {
                            // If we already have a refresh ready, return
                            LogUtils.i(TAG, "Create: refreshed cursor ready, needs sync");
                        } else {
                            // We're done
                            LogUtils.i(TAG, "Create: cursor good");
                        }
                    } else {
                        // We need a new query here; cancel any existing one, ensuring that a sync
                        // from another thread won't be stalled on the query
                        cancelRefresh();
                        LogUtils.i(TAG, "Create: performing refresh()");
                        qUri = uri;
                        qProjection = projection;
                        sConversationCursor.refresh();
                    }
                    return sConversationCursor;
                }
                // Create new ConversationCursor
                LogUtils.i(TAG, "Create: initial creation");
                Wrapper c = doQuery(uri, projection, sInitialConversationLimit);
                return new ConversationCursor(c, activity, messageListColumn);
            } finally {
                // If we used a limit, queue up a query without limit
                if (sInitialConversationLimit) {
                    sInitialConversationLimit = false;
                    sConversationCursor.refresh();
                }
            }
        }
    }

    /**
     * Runnable that performs the query on the underlying provider
     */
    private static class RefreshTask extends AsyncTask<Void, Void, Void> {
        private Wrapper mCursor = null;
        private final Uri mUri;
        private final String[] mProjection;

        private RefreshTask(Uri uri, String[] projection) {
            mUri = uri;
            mProjection = projection;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (DEBUG) {
                LogUtils.i(TAG, "[Start refresh %d]", hashCode());
            }
            // Get new data
            mCursor = doQuery(mUri, mProjection, false);
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            synchronized(sCacheMapLock) {
                sRequeryCursor = mCursor;
                // Make sure window is full
                sRequeryCursor.getCount();
                sRefreshReady = true;
                if (DEBUG) {
                    LogUtils.i(TAG, "[Notify: onRefreshReady %d]", hashCode());
                }
                synchronized (sListeners) {
                    for (ConversationListener listener : sListeners) {
                        listener.onRefreshReady();
                    }
                }
            }
        }

        @Override
        protected void onCancelled() {
            if (DEBUG) {
                LogUtils.i(TAG, "[Ignoring refresh result %d]", hashCode());
            }
            if (mCursor != null) {
                mCursor.close();
            }
        }
    }

    /**
     * Wrapper that includes the Uri used to create the cursor
     */
    private static class Wrapper extends CursorWrapper {
        private final Uri mUri;

        Wrapper(Cursor cursor, Uri uri) {
            super(cursor);
            mUri = uri;
        }

        Uri getUri() {
            return mUri;
        }
    }

    private static Wrapper doQuery(Uri uri, String[] projection, boolean withLimit) {
        qProjection = projection;
        qUri = uri;
        if (mResolver == null) {
            mResolver = sActivity.getContentResolver();
        }
        if (withLimit) {
            uri = uri.buildUpon().appendQueryParameter(ConversationListQueryParameters.LIMIT,
                    ConversationListQueryParameters.DEFAULT_LIMIT).build();
        }
        long time = System.currentTimeMillis();

        Wrapper result = new Wrapper(mResolver.query(uri, qProjection, null, null, null), uri);
        if (DEBUG) {
            time = System.currentTimeMillis() - time;
            LogUtils.i(TAG, "ConversationCursor query: %s, %dms, %d results",
                    uri, time, result.getCount());
        }
        return result;
    }

    /**
     * Return whether the uri string (message list uri) is in the underlying cursor
     * @param uriString the uri string we're looking for
     * @return true if the uri string is in the cursor; false otherwise
     */
    private boolean isInUnderlyingCursor(String uriString) {
        sUnderlyingCursor.moveToPosition(-1);
        while (sUnderlyingCursor.moveToNext()) {
            if (uriString.equals(sUnderlyingCursor.getString(sUriColumnIndex))) {
                return true;
            }
        }
        return false;
    }

    static boolean offUiThread() {
        return Looper.getMainLooper().getThread() != Thread.currentThread();
    }

    /**
     * Reset the cursor; this involves clearing out our cache map and resetting our various counts
     * The cursor should be reset whenever we get fresh data from the underlying cursor. The cache
     * is locked during the reset, which will block the UI, but for only a very short time
     * (estimated at a few ms, but we can profile this; remember that the cache will usually
     * be empty or have a few entries)
     */
    private void resetCursor(Wrapper newCursor) {
        synchronized (sCacheMapLock) {
            // Walk through the cache.  Here are the cases:
            // 1) The entry isn't marked with REQUERY - remove it from the cache. If DELETED is
            //    set, decrement the deleted count
            // 2) The REQUERY entry is still in the UP
            //    2a) The REQUERY entry isn't DELETED; we're good, and the client change will remain
            //    (i.e. client wins, it's on its way to the UP)
            //    2b) The REQUERY entry is DELETED; we're good (client change remains, it's on
            //        its way to the UP)
            // 3) the REQUERY was deleted on the server (sheesh; this would be bizarre timing!) -
            //    we need to throw the item out of the cache
            // So ... the only interesting case is #3, we need to look for remaining deleted items
            // and see if they're still in the UP
            Iterator<HashMap.Entry<String, ContentValues>> iter = sCacheMap.entrySet().iterator();
            while (iter.hasNext()) {
                HashMap.Entry<String, ContentValues> entry = iter.next();
                ContentValues values = entry.getValue();
                if (values.containsKey(REQUERY_COLUMN) && isInUnderlyingCursor(entry.getKey())) {
                    // If we're in a requery and we're still around, remove the requery key
                    // We're good here, the cached change (delete/update) is on its way to UP
                    values.remove(REQUERY_COLUMN);
                    LogUtils.i(TAG, new Error(),
                            "IN resetCursor, remove requery column from %s", entry.getKey());
                } else {
                    // Keep the deleted count up-to-date; remove the cache entry
                    if (values.containsKey(DELETED_COLUMN)) {
                        sDeletedCount--;
                        LogUtils.i(TAG, new Error(),
                                "IN resetCursor, sDeletedCount decremented to: %d by %s",
                                sDeletedCount, entry.getKey());
                    }
                    // Remove the entry
                    iter.remove();
                }
            }

            // Swap cursor
            if (newCursor != null) {
                close();
                sUnderlyingCursor = newCursor;
            }

            mPosition = -1;
            sUnderlyingCursor.moveToPosition(mPosition);
            if (!mCursorObserverRegistered) {
                sUnderlyingCursor.registerContentObserver(mCursorObserver);
                mCursorObserverRegistered = true;
            }
            sRefreshRequired = false;
        }
    }

    /**
     * Add a listener for this cursor; we'll notify it when our data changes
     */
    public void addListener(ConversationListener listener) {
        synchronized (sListeners) {
            if (!sListeners.contains(listener)) {
                sListeners.add(listener);
            } else {
                LogUtils.i(TAG, "Ignoring duplicate add of listener");
            }
        }
    }

    /**
     * Remove a listener for this cursor
     */
    public void removeListener(ConversationListener listener) {
        synchronized(sListeners) {
            sListeners.remove(listener);
        }
    }

    /**
     * Generate a forwarding Uri to ConversationProvider from an original Uri.  We do this by
     * changing the authority to ours, but otherwise leaving the Uri intact.
     * NOTE: This won't handle query parameters, so the functionality will need to be added if
     * parameters are used in the future
     * @param uri the uri
     * @return a forwarding uri to ConversationProvider
     */
    private static String uriToCachingUriString (Uri uri) {
        String provider = uri.getAuthority();
        return uri.getScheme() + "://" + ConversationProvider.AUTHORITY
                + "/" + provider + uri.getPath();
    }

    /**
     * Regenerate the original Uri from a forwarding (ConversationProvider) Uri
     * NOTE: See note above for uriToCachingUri
     * @param uri the forwarding Uri
     * @return the original Uri
     */
    private static Uri uriFromCachingUri(Uri uri) {
        String authority = uri.getAuthority();
        // Don't modify uri's that aren't ours
        if (!authority.equals(ConversationProvider.AUTHORITY)) {
            return uri;
        }
        List<String> path = uri.getPathSegments();
        Uri.Builder builder = new Uri.Builder().scheme(uri.getScheme()).authority(path.get(0));
        for (int i = 1; i < path.size(); i++) {
            builder.appendPath(path.get(i));
        }
        return builder.build();
    }

    public static void setConversationColumn(String uriString, String columnName, Object value) {
        synchronized (sCacheMapLock) {
            if (sConversationCursor != null) {
                cacheValue(uriString, columnName, value);
            }
        }
        sConversationCursor.notifyDataChanged();
    }

    /**
     * Must be called on UI thread; notify listeners that data has changed
     */
    private void notifyDataChanged() {
        synchronized(sListeners) {
            for (ConversationListener listener: sListeners) {
                listener.onDataSetChanged();
            }
        }
    }

    /**
     * Cache a column name/value pair for a given Uri
     * @param uriString the Uri for which the column name/value pair applies
     * @param columnName the column name
     * @param value the value to be cached
     */
    private static void cacheValue(String uriString, String columnName, Object value) {
        // Calling this method off the UI thread will mess with ListView's reading of the cursor's
        // count
        if (offUiThread()) {
            LogUtils.e(TAG, new Error(), "cacheValue incorrectly being called from non-UI thread");
        }

        synchronized (sCacheMapLock) {
            // Get the map for our uri
            ContentValues map = sCacheMap.get(uriString);
            // Create one if necessary
            if (map == null) {
                map = new ContentValues();
                sCacheMap.put(uriString, map);
            }
            // If we're caching a deletion, add to our count
            if (columnName == DELETED_COLUMN) {
                final boolean state = (Boolean)value;
                final boolean hasValue = map.get(columnName) != null;
                if (state && !hasValue) {
                    sDeletedCount++;
                    if (DEBUG) {
                        LogUtils.i(TAG, "Deleted %s, incremented deleted count=%d", uriString,
                                sDeletedCount);
                    }
                } else if (!state && hasValue) {
                    sDeletedCount--;
                    map.remove(columnName);
                    if (DEBUG) {
                        LogUtils.i(TAG, "Undeleted %s, decremented deleted count=%d", uriString,
                                sDeletedCount);
                    }
                    return;
                } else if (!state) {
                    // Trying to undelete, but it's not deleted; just return
                    if (DEBUG) {
                        LogUtils.i(TAG, "Undeleted %s, IGNORING, deleted count=%d", uriString,
                                sDeletedCount);
                    }
                    return;
                }
            }
            // ContentValues has no generic "put", so we must test.  For now, the only classes
            // of values implemented are Boolean/Integer/String, though others are trivially
            // added
            if (value instanceof Boolean) {
                map.put(columnName, ((Boolean) value).booleanValue() ? 1 : 0);
            } else if (value instanceof Integer) {
                map.put(columnName, (Integer) value);
            } else if (value instanceof String) {
                map.put(columnName, (String) value);
            } else {
                final String cname = value.getClass().getName();
                throw new IllegalArgumentException("Value class not compatible with cache: "
                        + cname);
            }
            if (sRefreshTask != null) {
                map.put(REQUERY_COLUMN, 1);
            }
            if (DEBUG && (columnName != DELETED_COLUMN)) {
                LogUtils.i(TAG, "Caching value for %s: %s", uriString, columnName);
            }
        }
    }

    /**
     * Get the cached value for the provided column; we special case -1 as the "deleted" column
     * @param columnIndex the index of the column whose cached value we want to retrieve
     * @return the cached value for this column, or null if there is none
     */
    private Object getCachedValue(int columnIndex) {
        String uri = sUnderlyingCursor.getString(sUriColumnIndex);
        ContentValues uriMap = sCacheMap.get(uri);
        if (uriMap != null) {
            String columnName;
            if (columnIndex == DELETED_COLUMN_INDEX) {
                columnName = DELETED_COLUMN;
            } else {
                columnName = mColumnNames[columnIndex];
            }
            return uriMap.get(columnName);
        }
        return null;
    }

    /**
     * When the underlying cursor changes, we want to alert the listener
     */
    private void underlyingChanged() {
        if (mCursorObserverRegistered) {
            try {
                sUnderlyingCursor.unregisterContentObserver(mCursorObserver);
            } catch (IllegalStateException e) {
                // Maybe the cursor was GC'd?
            }
            mCursorObserverRegistered = false;
        }
        if (DEBUG) {
            LogUtils.i(TAG, "[Notify: onRefreshRequired()]");
        }
        synchronized(sListeners) {
            for (ConversationListener listener: sListeners) {
                listener.onRefreshRequired();
            }
        }
        sRefreshRequired = true;
    }

    /**
     * Put the refreshed cursor in place (called by the UI)
     */
    public void sync() {
        if (sRequeryCursor == null) {
            // This can happen during an animated deletion, if the UI isn't keeping track, or
            // if a new query intervened (i.e. user changed folders)
            if (DEBUG) {
                LogUtils.i(TAG, "[sync() called; no requery cursor]");
            }
            return;
        }
        synchronized(sCacheMapLock) {
            if (DEBUG) {
                LogUtils.i(TAG, "[sync()]");
            }
            resetCursor(sRequeryCursor);
            sRequeryCursor = null;
            sRefreshTask = null;
            sRefreshReady = false;
        }
    }

    public boolean isRefreshRequired() {
        return sRefreshRequired;
    }

    public boolean isRefreshReady() {
        return sRefreshReady;
    }

    /**
     * Cancel a refresh in progress
     */
    public static void cancelRefresh() {
        if (DEBUG) {
            LogUtils.i(TAG, "[cancelRefresh() called]");
        }
        synchronized(sCacheMapLock) {
            if (sRefreshTask != null) {
                sRefreshTask.cancel(true);
                sRefreshTask = null;
            }
            sRefreshReady = false;
            // If we have the cursor, close it; otherwise, it will get closed when the query
            // finishes (it checks sRefreshInProgress)
            if (sRequeryCursor != null) {
                sRequeryCursor.close();
                sRequeryCursor = null;
            }
        }
    }

    /**
     * Get a list of deletions from ConversationCursor to the refreshed cursor that hasn't yet
     * been swapped into place; this allows the UI to animate these away if desired
     * @return a list of positions deleted in ConversationCursor
     */
    public ArrayList<Integer> getRefreshDeletions () {
        // It's possible that the requery cursor is null in the case that loadInBackground() causes
        // ConversationCursor.create to do a sync() between the time that refreshReady() is called
        // and the subsequent call to getRefreshDeletions().  This is harmless, and an empty
        // result list is correct.
        return EMPTY_DELETION_LIST;
//        if (sRequeryCursor == null) {
//            if (DEBUG) {
//                LogUtils.i(TAG, "[getRefreshDeletions() called; no cursor]");
//            }
//            return EMPTY_DELETION_LIST;
//        } else if (!sRequeryCursor.getUri().equals(sUnderlyingCursor.getUri())) {
//            if (DEBUG) {
//                LogUtils.i(TAG, "[getRefreshDeletions(); cursors differ]");
//            }
//            return EMPTY_DELETION_LIST;
//        }
//        Cursor deviceCursor = sConversationCursor;
//        Cursor serverCursor = sRequeryCursor;
//        ArrayList<Integer> deleteList = new ArrayList<Integer>();
//        int serverCount = serverCursor.getCount();
//        int deviceCount = deviceCursor.getCount();
//        deviceCursor.moveToFirst();
//        serverCursor.moveToFirst();
//        while (serverCount > 0 || deviceCount > 0) {
//            if (serverCount == 0) {
//                for (; deviceCount > 0; deviceCount--, deviceCursor.moveToPrevious()) {
//                    deleteList.add(deviceCursor.getPosition());
//                    if (deleteList.size() > 6) {
//                        if (DEBUG) {
//                            LogUtils.i(TAG, "[getRefreshDeletions(); mega changes]");
//                        }
//                        return EMPTY_DELETION_LIST;
//                    }
//                }
//                break;
//            } else if (deviceCount == 0) {
//                break;
//            }
//            long deviceMs = deviceCursor.getLong(UIProvider.CONVERSATION_DATE_RECEIVED_MS_COLUMN);
//            long serverMs = serverCursor.getLong(UIProvider.CONVERSATION_DATE_RECEIVED_MS_COLUMN);
//            String deviceUri = deviceCursor.getString(UIProvider.CONVERSATION_URI_COLUMN);
//            String serverUri = serverCursor.getString(UIProvider.CONVERSATION_URI_COLUMN);
//            deviceCursor.moveToNext();
//            serverCursor.moveToNext();
//            serverCount--;
//            deviceCount--;
//            if (serverMs == deviceMs) {
//                // Check for duplicates here; if our identical dates refer to different messages,
//                // we'll just quit here for now (at worst, this will cause a non-animating delete)
//                // My guess is that this happens VERY rarely, if at all
//                if (!deviceUri.equals(serverUri)) {
//                    // To do this right, we'd find all of the rows with the same ms (date), etc...
//                    //return deleteList;
//                }
//                continue;
//            } else if (deviceMs > serverMs) {
//                deleteList.add(deviceCursor.getPosition() - 1);
//                if (deleteList.size() > 6) {
//                    if (DEBUG) {
//                        LogUtils.i(TAG, "[getRefreshDeletions(); mega changes]");
//                    }
//                    return EMPTY_DELETION_LIST;
//                }
//                // Move back because we've already advanced cursor (that's why we subtract 1 above)
//                serverCount++;
//                serverCursor.moveToPrevious();
//            } else if (serverMs > deviceMs) {
//                // If we wanted to track insertions, we'd so so here
//                // Move back because we've already advanced cursor
//                deviceCount++;
//                deviceCursor.moveToPrevious();
//            }
//        }
//        if (DEBUG) {
//            LogUtils.i(TAG, "getRefreshDeletions(): " + deleteList);
//        }
//        return deleteList;
    }

    /**
     * When we get a requery from the UI, we'll do it, but also clear the cache. The listener is
     * notified when the requery is complete
     * NOTE: This will have to change, of course, when we start using loaders...
     */
    public boolean refresh() {
        if (DEBUG) {
            LogUtils.i(TAG, "[refresh() called]");
        }
        synchronized(sCacheMapLock) {
            if (sRefreshTask != null) {
                if (DEBUG) {
                    LogUtils.i(TAG, "[refresh() returning; already running %d]",
                            sRefreshTask.hashCode());
                }
                return false;
            }
            sRefreshTask = new RefreshTask(qUri, qProjection);
            sRefreshTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        return true;
    }

    @Override
    public void close() {
        if (!sUnderlyingCursor.isClosed()) {
            // Unregister our observer on the underlying cursor and close as usual
            if (mCursorObserverRegistered) {
                try {
                    sUnderlyingCursor.unregisterContentObserver(mCursorObserver);
                } catch (IllegalStateException e) {
                    // Maybe the cursor got GC'd?
                }
                mCursorObserverRegistered = false;
            }
            sUnderlyingCursor.close();
        }
    }

    /**
     * Move to the next not-deleted item in the conversation
     */
    @Override
    public boolean moveToNext() {
        while (true) {
            boolean ret = sUnderlyingCursor.moveToNext();
            if (!ret) return false;
            if (getCachedValue(DELETED_COLUMN_INDEX) instanceof Integer) continue;
            mPosition++;
            return true;
        }
    }

    /**
     * Move to the previous not-deleted item in the conversation
     */
    @Override
    public boolean moveToPrevious() {
        while (true) {
            boolean ret = sUnderlyingCursor.moveToPrevious();
            if (!ret) return false;
            if (getCachedValue(DELETED_COLUMN_INDEX) instanceof Integer) continue;
            mPosition--;
            // STOPSHIP: Remove this if statement
            if (mPosition < 0) {
                mStackTrace = new Throwable().getStackTrace();
            }
            return true;
        }
    }

    @Override
    public int getPosition() {
        return mPosition;
    }

    /**
     * The actual cursor's count must be decremented by the number we've deleted from the UI
     */
    @Override
    public int getCount() {
        return sUnderlyingCursor.getCount() - sDeletedCount;
    }

    @Override
    public boolean moveToFirst() {
        sUnderlyingCursor.moveToPosition(-1);
        mPosition = -1;
        return moveToNext();
    }

    // STOPSHIP: Remove this
    private StackTraceElement[] mStackTrace = null;

    @Override
    public boolean moveToPosition(int pos) {
        // STOPSHIP: Remove this if statement
        if (pos == -1) {
            mStackTrace = new Throwable().getStackTrace();
        }
        // STOPSHIP: Remove this check
        if (offUiThread()) {
            LogUtils.w(TAG, new Throwable(), "********** moveToPosition OFF UI THREAD: %d", pos);
        }
        if (pos < -1 || pos >= getCount()) {
            // STOPSHIP: Remove this logging
            LogUtils.w(TAG, new Throwable(), "********** moveToPosition OUT OF RANGE: %d", pos);
            return false;
        }
        if (pos == mPosition) return true;
        if (pos > mPosition) {
            while (pos > mPosition) {
                if (!moveToNext()) {
                    return false;
                }
            }
            return true;
        } else if (pos == 0) {
            return moveToFirst();
        } else {
            while (pos < mPosition) {
                if (!moveToPrevious()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Make sure mPosition is correct after locally deleting/undeleting items
     */
    private void recalibratePosition() {
        int pos = mPosition;
        moveToFirst();
        moveToPosition(pos);
    }

    @Override
    public boolean moveToLast() {
        throw new UnsupportedOperationException("moveToLast unsupported!");
    }

    @Override
    public boolean move(int offset) {
        throw new UnsupportedOperationException("move unsupported!");
    }

    /**
     * We need to override all of the getters to make sure they look at cached values before using
     * the values in the underlying cursor
     */
    @Override
    public double getDouble(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Double)obj;
        return sUnderlyingCursor.getDouble(columnIndex);
    }

    @Override
    public float getFloat(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Float)obj;
        return sUnderlyingCursor.getFloat(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Integer)obj;
        return sUnderlyingCursor.getInt(columnIndex);
    }

    @Override
    public long getLong(int columnIndex) {
        // STOPSHIP: Remove try/catch
        try {
            Object obj = getCachedValue(columnIndex);
            if (obj != null) return (Long)obj;
            return sUnderlyingCursor.getLong(columnIndex);
        } catch (CursorIndexOutOfBoundsException e) {
            if (mStackTrace != null) {
                Log.e(TAG, "Stack trace at last moveToPosition(-1)");
                Throwable t = new Throwable();
                t.setStackTrace(mStackTrace);
                t.printStackTrace();
            }
            throw e;
        }
    }

    @Override
    public short getShort(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Short)obj;
        return sUnderlyingCursor.getShort(columnIndex);
    }

    @Override
    public String getString(int columnIndex) {
        // If we're asking for the Uri for the conversation list, we return a forwarding URI
        // so that we can intercept update/delete and handle it ourselves
        if (columnIndex == sUriColumnIndex) {
            Uri uri = Uri.parse(sUnderlyingCursor.getString(columnIndex));
            return uriToCachingUriString(uri);
        }
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (String)obj;
        return sUnderlyingCursor.getString(columnIndex);
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (byte[])obj;
        return sUnderlyingCursor.getBlob(columnIndex);
    }

    /**
     * Observer of changes to underlying data
     */
    private class CursorObserver extends ContentObserver {
        public CursorObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            // If we're here, then something outside of the UI has changed the data, and we
            // must query the underlying provider for that data
            ConversationCursor.this.underlyingChanged();
        }
    }

    /**
     * ConversationProvider is the ContentProvider for our forwarding Uri's; it passes queries
     * and inserts directly, and caches updates/deletes before passing them through.  The caching
     * will cause a redraw of the list with updated values.
     */
    public abstract static class ConversationProvider extends ContentProvider {
        public static String AUTHORITY;

        /**
         * Allows the implementing provider to specify the authority that should be used.
         */
        protected abstract String getAuthority();

        @Override
        public boolean onCreate() {
            sProvider = this;
            AUTHORITY = getAuthority();
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            return mResolver.query(
                    uriFromCachingUri(uri), projection, selection, selectionArgs, sortOrder);
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            insertLocal(uri, values);
            return ProviderExecute.opInsert(uri, values);
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            updateLocal(uri, values);
            return ProviderExecute.opUpdate(uri, values);
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            deleteLocal(uri);
            return ProviderExecute.opDelete(uri);
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        /**
         * Quick and dirty class that executes underlying provider CRUD operations on a background
         * thread.
         */
        static class ProviderExecute implements Runnable {
            static final int DELETE = 0;
            static final int INSERT = 1;
            static final int UPDATE = 2;

            final int mCode;
            final Uri mUri;
            final ContentValues mValues; //HEHEH

            ProviderExecute(int code, Uri uri, ContentValues values) {
                mCode = code;
                mUri = uriFromCachingUri(uri);
                mValues = values;
            }

            ProviderExecute(int code, Uri uri) {
                this(code, uri, null);
            }

            static Uri opInsert(Uri uri, ContentValues values) {
                ProviderExecute e = new ProviderExecute(INSERT, uri, values);
                if (offUiThread()) return (Uri)e.go();
                new Thread(e).start();
                return null;
            }

            static int opDelete(Uri uri) {
                ProviderExecute e = new ProviderExecute(DELETE, uri);
                if (offUiThread()) return (Integer)e.go();
                new Thread(new ProviderExecute(DELETE, uri)).start();
                return 0;
            }

            static int opUpdate(Uri uri, ContentValues values) {
                ProviderExecute e = new ProviderExecute(UPDATE, uri, values);
                if (offUiThread()) return (Integer)e.go();
                new Thread(e).start();
                return 0;
            }

            @Override
            public void run() {
                go();
            }

            public Object go() {
                switch(mCode) {
                    case DELETE:
                        return mResolver.delete(mUri, null, null);
                    case INSERT:
                        return mResolver.insert(mUri, mValues);
                    case UPDATE:
                        return mResolver.update(mUri,  mValues, null, null);
                    default:
                        return null;
                }
            }
        }

        private void insertLocal(Uri uri, ContentValues values) {
            // Placeholder for now; there's no local insert
        }

        private int mUndoSequence = 0;
        private ArrayList<Uri> mUndoDeleteUris = new ArrayList<Uri>();

        @VisibleForTesting
        void deleteLocal(Uri uri) {
            Uri underlyingUri = uriFromCachingUri(uri);
            // Remember to decode the underlying Uri as it might be encoded (as w/ Gmail)
            String uriString =  Uri.decode(underlyingUri.toString());
            cacheValue(uriString, DELETED_COLUMN, true);
            if (sSequence != mUndoSequence) {
                mUndoSequence = sSequence;
                mUndoDeleteUris.clear();
            }
            mUndoDeleteUris.add(uri);
        }

        @VisibleForTesting
        void undeleteLocal(Uri uri) {
            Uri underlyingUri = uriFromCachingUri(uri);
            // Remember to decode the underlying Uri as it might be encoded (as w/ Gmail)
            String uriString =  Uri.decode(underlyingUri.toString());
            cacheValue(uriString, DELETED_COLUMN, false);
        }

        public void undo() {
            if (sSequence == mUndoSequence) {
                for (Uri uri: mUndoDeleteUris) {
                    undeleteLocal(uri);
                }
                mUndoSequence = 0;
                sConversationCursor.recalibratePosition();
            }
        }

        @VisibleForTesting
        void updateLocal(Uri uri, ContentValues values) {
            if (values == null) {
                return;
            }
            Uri underlyingUri = uriFromCachingUri(uri);
            // Remember to decode the underlying Uri as it might be encoded (as w/ Gmail)
            String uriString =  Uri.decode(underlyingUri.toString());
            for (String columnName: values.keySet()) {
                cacheValue(uriString, columnName, values.get(columnName));
            }
        }

        public int apply(ArrayList<ConversationOperation> ops) {
            final HashMap<String, ArrayList<ContentProviderOperation>> batchMap =
                    new HashMap<String, ArrayList<ContentProviderOperation>>();
            // STOPSHIP: Remove this test
            if (offUiThread()) {
                Log.w(TAG, "apply() called off of UI thread", new Throwable());
            }
            // Increment sequence count
            sSequence++;

            // Execute locally and build CPO's for underlying provider
            boolean recalibrateRequired = false;
            for (ConversationOperation op: ops) {
                Uri underlyingUri = uriFromCachingUri(op.mUri);
                String authority = underlyingUri.getAuthority();
                ArrayList<ContentProviderOperation> authOps = batchMap.get(authority);
                if (authOps == null) {
                    authOps = new ArrayList<ContentProviderOperation>();
                    batchMap.put(authority, authOps);
                }
                authOps.add(op.execute(underlyingUri));
                // Keep track of whether our operations require recalibrating the cursor position
                if (op.mRecalibrateRequired) {
                    recalibrateRequired = true;
                }
            }

            // Recalibrate cursor position if required
            if (recalibrateRequired) {
                sConversationCursor.recalibratePosition();
            }
            // Notify listeners that data has changed
            sConversationCursor.notifyDataChanged();

            // Send changes to underlying provider
            for (String authority: batchMap.keySet()) {
                try {
                    if (offUiThread()) {
                        mResolver.applyBatch(authority, batchMap.get(authority));
                    } else {
                        final String auth = authority;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mResolver.applyBatch(auth, batchMap.get(auth));
                                } catch (RemoteException e) {
                                } catch (OperationApplicationException e) {
                                }
                           }
                        }).start();
                    }
                } catch (RemoteException e) {
                } catch (OperationApplicationException e) {
                }
            }
            return sSequence;
        }
    }

    /**
     * ConversationOperation is the encapsulation of a ContentProvider operation to be performed
     * atomically as part of a "batch" operation.
     */
    public static class ConversationOperation {
        public static final int DELETE = 0;
        public static final int INSERT = 1;
        public static final int UPDATE = 2;
        public static final int ARCHIVE = 3;
        public static final int MUTE = 4;
        public static final int REPORT_SPAM = 5;

        private final int mType;
        private final Uri mUri;
        private final ContentValues mValues;
        // True if an updated item should be removed locally (from ConversationCursor)
        // This would be the case for a folder change in which the conversation is no longer
        // in the folder represented by the ConversationCursor
        private final boolean mLocalDeleteOnUpdate;
        // After execution, this indicates whether or not the operation requires recalibration of
        // the current cursor position (i.e. it removed or added items locally)
        private boolean mRecalibrateRequired = true;

        /**
         * Set to true to immediately notify any {@link DataSetObserver}s watching the global
         * {@link ConversationCursor} upon applying the change to the data cache. You would not
         * want to do this if a change you make is being handled specially, like an animated delete.
         *
         * TODO: move this to the application Controller, or whoever has a canonical reference
         * to a {@link ConversationCursor} to notify on.
         */
        private final boolean mAutoNotify;

        public ConversationOperation(int type, Conversation conv) {
            this(type, conv, null, false /* autoNotify */);
        }

        public ConversationOperation(int type, Conversation conv, ContentValues values,
                boolean autoNotify) {
            mType = type;
            mUri = conv.uri;
            mValues = values;
            mLocalDeleteOnUpdate = conv.localDeleteOnUpdate;
            mAutoNotify = autoNotify;
        }

        private ContentProviderOperation execute(Uri underlyingUri) {
            Uri uri = underlyingUri.buildUpon()
                    .appendQueryParameter(UIProvider.SEQUENCE_QUERY_PARAMETER,
                            Integer.toString(sSequence))
                    .build();
            ContentProviderOperation op;
            switch(mType) {
                case DELETE:
                    sProvider.deleteLocal(mUri);
                    op = ContentProviderOperation.newDelete(uri).build();
                    break;
                case UPDATE:
                    if (mLocalDeleteOnUpdate) {
                        sProvider.deleteLocal(mUri);
                    } else {
                        sProvider.updateLocal(mUri, mValues);
                        mRecalibrateRequired = false;
                    }
                    op = ContentProviderOperation.newUpdate(uri)
                            .withValues(mValues)
                            .build();
                    break;
                case INSERT:
                    sProvider.insertLocal(mUri, mValues);
                    op = ContentProviderOperation.newInsert(uri)
                            .withValues(mValues).build();
                    break;
                case ARCHIVE:
                    sProvider.deleteLocal(mUri);

                    // Create an update operation that represents archive
                    op = ContentProviderOperation.newUpdate(uri).withValue(
                            ConversationOperations.OPERATION_KEY, ConversationOperations.ARCHIVE)
                            .build();
                    break;
                case MUTE:
                    if (mLocalDeleteOnUpdate) {
                        sProvider.deleteLocal(mUri);
                    }

                    // Create an update operation that represents mute
                    op = ContentProviderOperation.newUpdate(uri).withValue(
                            ConversationOperations.OPERATION_KEY, ConversationOperations.MUTE)
                            .build();
                    break;
                case REPORT_SPAM:
                    sProvider.deleteLocal(mUri);

                    // Create an update operation that represents report spam
                    op = ContentProviderOperation.newUpdate(uri).withValue(
                            ConversationOperations.OPERATION_KEY,
                            ConversationOperations.REPORT_SPAM).build();
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "No such ConversationOperation type: " + mType);
            }

            // FIXME: this is a hack to notify conversation list of changes from conversation view.
            // The proper way to do this is to have the Controller handle the 'mark read' action.
            // It has a reference to this ConversationCursor so it can notify without using global
            // magic.
            if (mAutoNotify) {
                if (sConversationCursor != null) {
                    sConversationCursor.notifyDataSetChanged();
                } else {
                    LogUtils.i(TAG, "Unable to auto-notify because there is no existing" +
                            " conversation cursor");
                }
            }

            return op;
        }
    }

    /**
     * For now, a single listener can be associated with the cursor, and for now we'll just
     * notify on deletions
     */
    public interface ConversationListener {
        /**
         * Data in the underlying provider has changed; a refresh is required to sync up
         */
        public void onRefreshRequired();
        /**
         * We've completed a requested refresh of the underlying cursor
         */
        public void onRefreshReady();
        /**
         * The data underlying the cursor has changed; the UI should redraw the list
         */
        public void onDataSetChanged();
    }

    @Override
    public boolean isFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBeforeFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAfterLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getColumnIndex(String columnName) {
        return sUnderlyingCursor.getColumnIndex(columnName);
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
        return sUnderlyingCursor.getColumnIndexOrThrow(columnName);
    }

    @Override
    public String getColumnName(int columnIndex) {
        return sUnderlyingCursor.getColumnName(columnIndex);
    }

    @Override
    public String[] getColumnNames() {
        return sUnderlyingCursor.getColumnNames();
    }

    @Override
    public int getColumnCount() {
        return sUnderlyingCursor.getColumnCount();
    }

    @Override
    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getType(int columnIndex) {
        return sUnderlyingCursor.getType(columnIndex);
    }

    @Override
    public boolean isNull(int columnIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deactivate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed() {
        return sUnderlyingCursor.isClosed();
    }

    @Override
    public void registerContentObserver(ContentObserver observer) {
        // Nope. We never notify of underlying changes on this channel, since the cursor watches
        // internally and offers onRefreshRequired/onRefreshReady to accomplish the same thing.
    }

    @Override
    public void unregisterContentObserver(ContentObserver observer) {
        // See above.
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.registerObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.unregisterObserver(observer);
    }

    public void notifyDataSetChanged() {
        mDataSetObservable.notifyChanged();
    }

    @Override
    public void setNotificationUri(ContentResolver cr, Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getWantsAllOnMoveCalls() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle getExtras() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle respond(Bundle extras) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean requery() {
        return true;
    }
}
