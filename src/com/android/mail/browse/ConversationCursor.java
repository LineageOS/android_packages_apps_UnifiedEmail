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
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
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
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
    // A deleted row is indicated by the presence of DELETED_COLUMN in the cache map
    private static final String DELETED_COLUMN = "__deleted__";
    // An row cached during a requery is indicated by the presence of REQUERY_COLUMN in the map
    private static final String REQUERY_COLUMN = "__requery__";
    // A sentinel value for the "index" of the deleted column; it's an int that is otherwise invalid
    private static final int DELETED_COLUMN_INDEX = -1;
    // Empty deletion list
    private static final Collection<Conversation> EMPTY_DELETION_LIST = Lists.newArrayList();
    // The index of the Uri whose data is reflected in the cached row
    // Updates/Deletes to this Uri are cached
    private static int sUriColumnIndex;
    // Our sequence count (for changes sent to underlying provider)
    private static int sSequence = 0;
    // The resolver for the cursor instantiator's context
    private static ContentResolver sResolver;
    @VisibleForTesting
    static ConversationProvider sProvider;

    // The cursor instantiator's activity
    private Activity mActivity;
    // The cursor underlying the caching cursor
    @VisibleForTesting
    Wrapper mUnderlyingCursor;
    // The new cursor obtained via a requery
    private volatile Wrapper mRequeryCursor;
    // A mapping from Uri to updated ContentValues
    private HashMap<String, ContentValues> mCacheMap = new HashMap<String, ContentValues>();
    // Cache map lock (will be used only very briefly - few ms at most)
    private Object mCacheMapLock = new Object();
    // The listeners registered for this cursor
    private List<ConversationListener> mListeners = Lists.newArrayList();
    // The ConversationProvider instance
    // The runnable executing a refresh (query of underlying provider)
    private RefreshTask mRefreshTask;
    // Set when we've sent refreshReady() to listeners
    private boolean mRefreshReady = false;
    // Set when we've sent refreshRequired() to listeners
    private boolean mRefreshRequired = false;
    // Whether our first query on this cursor should include a limit
    private boolean mInitialConversationLimit = false;
    // A list of mostly-dead items
    private List<Conversation> sMostlyDead = Lists.newArrayList();
    // The name of the loader
    private final String mName;
    // Column names for this cursor
    private String[] mColumnNames;
    // An observer on the underlying cursor (so we can detect changes from outside the UI)
    private final CursorObserver mCursorObserver;
    // Whether our observer is currently registered with the underlying cursor
    private boolean mCursorObserverRegistered = false;
    // Whether our loader is paused
    private boolean mPaused = false;
    // Whether or not sync from underlying provider should be deferred
    private boolean mDeferSync = false;

    // The current position of the cursor
    private int mPosition = -1;

    // The number of cached deletions from this cursor (used to quickly generate an accurate count)
    private int mDeletedCount = 0;

    // Parameters passed to the underlying query
    private Uri qUri;
    private String[] qProjection;

    private void setCursor(Wrapper cursor) {
        // If we have an existing underlying cursor, make sure it's closed
        if (mUnderlyingCursor != null) {
            close();
        }
        mColumnNames = cursor.getColumnNames();
        mRefreshRequired = false;
        mRefreshReady = false;
        mRefreshTask = null;
        resetCursor(cursor);
    }

    public ConversationCursor(Activity activity, Uri uri, boolean initialConversationLimit,
            String name) {
        //sActivity = activity;
        mInitialConversationLimit = initialConversationLimit;
        sResolver = activity.getContentResolver();
        sUriColumnIndex = UIProvider.CONVERSATION_URI_COLUMN;
        qUri = uri;
        mName = name;
        qProjection = UIProvider.CONVERSATION_PROJECTION;
        mCursorObserver = new CursorObserver();
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
    public void load() {
        synchronized (mCacheMapLock) {
            try {
                // Create new ConversationCursor
                LogUtils.i(TAG, "Create: initial creation");
                Wrapper c = doQuery(mInitialConversationLimit);
                setCursor(c);
            } finally {
                // If we used a limit, queue up a query without limit
                if (mInitialConversationLimit) {
                    mInitialConversationLimit = false;
                    refresh();
                }
            }
        }
    }

    /**
     * Pause notifications to UI
     */
    public void pause() {
        if (DEBUG) {
            LogUtils.i(TAG, "[Paused: %s]", mName);
        }
        mPaused = true;
    }

    /**
     * Resume notifications to UI; if any are pending, send them
     */
    public void resume() {
        if (DEBUG) {
            LogUtils.i(TAG, "[Resumed: %s]", mName);
        }
        mPaused = false;
        checkNotifyUI();
    }

    private void checkNotifyUI() {
        if (!mPaused && !mDeferSync) {
            if (mRefreshRequired && (mRefreshTask == null)) {
                notifyRefreshRequired();
            } else if (mRefreshReady) {
                notifyRefreshReady();
            }
        } else {
            LogUtils.i(TAG, "[checkNotifyUI: %s%s",
                    (mPaused ? "Paused " : ""), (mDeferSync ? "Defer" : ""));
        }
    }

    /**
     * Runnable that performs the query on the underlying provider
     */
    private class RefreshTask extends AsyncTask<Void, Void, Void> {
        private Wrapper mCursor = null;

        private RefreshTask() {
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (DEBUG) {
                LogUtils.i(TAG, "[Start refresh of %s: %d]", mName, hashCode());
            }
            // Get new data
            mCursor = doQuery(false);
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            synchronized(mCacheMapLock) {
                mRequeryCursor = mCursor;
                // Make sure window is full
                mRequeryCursor.getCount();
                mRefreshReady = true;
                if (DEBUG) {
                    LogUtils.i(TAG, "[Query done %s: %d]", mName, hashCode());
                }
                if (!mDeferSync && !mPaused) {
                    notifyRefreshReady();
                }
            }
        }

        @Override
        protected void onCancelled() {
            if (DEBUG) {
                LogUtils.i(TAG, "[Ignoring refresh result: %d]", hashCode());
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
    }

    private Wrapper doQuery(boolean withLimit) {
        if (sResolver == null) {
            sResolver = mActivity.getContentResolver();
        }
        Uri uri = qUri;
        if (withLimit) {
            uri = uri.buildUpon().appendQueryParameter(ConversationListQueryParameters.LIMIT,
                    ConversationListQueryParameters.DEFAULT_LIMIT).build();
        }
        long time = System.currentTimeMillis();

        Wrapper result = new Wrapper(sResolver.query(uri, qProjection, null, null, null), uri);
        if (result.getWrappedCursor() == null) {
            Log.w(TAG, "doQuery returning null cursor, uri: " + uri);
        } else if (DEBUG) {
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
        mUnderlyingCursor.moveToPosition(-1);
        while (mUnderlyingCursor.moveToNext()) {
            if (uriString.equals(mUnderlyingCursor.getString(sUriColumnIndex))) {
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
        synchronized (mCacheMapLock) {
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
            Iterator<HashMap.Entry<String, ContentValues>> iter = mCacheMap.entrySet().iterator();
            while (iter.hasNext()) {
                HashMap.Entry<String, ContentValues> entry = iter.next();
                ContentValues values = entry.getValue();
                if (values.containsKey(REQUERY_COLUMN) && isInUnderlyingCursor(entry.getKey())) {
                    // If we're in a requery and we're still around, remove the requery key
                    // We're good here, the cached change (delete/update) is on its way to UP
                    values.remove(REQUERY_COLUMN);
                    LogUtils.i(TAG,
                            "IN resetCursor, remove requery column from %s", entry.getKey());
                } else {
                    // Keep the deleted count up-to-date; remove the cache entry
                    if (values.containsKey(DELETED_COLUMN)) {
                        mDeletedCount--;
                        LogUtils.i(TAG, "IN resetCursor, sDeletedCount decremented to: %d by %s",
                                mDeletedCount, entry.getKey());
                    }
                    // Remove the entry
                    iter.remove();
                }
            }
        }

        // Swap cursor
        if (mUnderlyingCursor != null) {
            close();
        }
        mUnderlyingCursor = newCursor;

        mPosition = -1;
        mUnderlyingCursor.moveToPosition(mPosition);
        if (!mCursorObserverRegistered) {
            mUnderlyingCursor.registerContentObserver(mCursorObserver);
            mCursorObserverRegistered = true;
        }
        mRefreshRequired = false;
    }

    /**
     * Add a listener for this cursor; we'll notify it when our data changes
     */
    public void addListener(ConversationListener listener) {
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            } else {
                LogUtils.i(TAG, "Ignoring duplicate add of listener");
            }
        }
    }

    /**
     * Remove a listener for this cursor
     */
    public void removeListener(ConversationListener listener) {
        synchronized(mListeners) {
            mListeners.remove(listener);
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

    private static String uriStringFromCachingUri(Uri uri) {
        Uri underlyingUri = uriFromCachingUri(uri);
        // Remember to decode the underlying Uri as it might be encoded (as w/ Gmail)
        return Uri.decode(underlyingUri.toString());
    }

    public void setConversationColumn(Uri conversationUri, String columnName, Object value) {
        final String uriStr = uriStringFromCachingUri(conversationUri);
        synchronized (mCacheMapLock) {
            cacheValue(uriStr, columnName, value);
        }
        notifyDataChanged();
    }

    /**
     * Cache a column name/value pair for a given Uri
     * @param uriString the Uri for which the column name/value pair applies
     * @param columnName the column name
     * @param value the value to be cached
     */
    private void cacheValue(String uriString, String columnName, Object value) {
        // Calling this method off the UI thread will mess with ListView's reading of the cursor's
        // count
        if (offUiThread()) {
            LogUtils.e(TAG, new Error(), "cacheValue incorrectly being called from non-UI thread");
        }

        synchronized (mCacheMapLock) {
            // Get the map for our uri
            ContentValues map = mCacheMap.get(uriString);
            // Create one if necessary
            if (map == null) {
                map = new ContentValues();
                mCacheMap.put(uriString, map);
            }
            // If we're caching a deletion, add to our count
            if (columnName == DELETED_COLUMN) {
                final boolean state = (Boolean)value;
                final boolean hasValue = map.get(columnName) != null;
                if (state && !hasValue) {
                    mDeletedCount++;
                    if (DEBUG) {
                        LogUtils.i(TAG, "Deleted %s, incremented deleted count=%d", uriString,
                                mDeletedCount);
                    }
                } else if (!state && hasValue) {
                    mDeletedCount--;
                    map.remove(columnName);
                    if (DEBUG) {
                        LogUtils.i(TAG, "Undeleted %s, decremented deleted count=%d", uriString,
                                mDeletedCount);
                    }
                    return;
                } else if (!state) {
                    // Trying to undelete, but it's not deleted; just return
                    if (DEBUG) {
                        LogUtils.i(TAG, "Undeleted %s, IGNORING, deleted count=%d", uriString,
                                mDeletedCount);
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
            if (mRefreshTask != null) {
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
        String uri = mUnderlyingCursor.getString(sUriColumnIndex);
        return getCachedValue(uri, columnIndex);
    }

    private Object getCachedValue(String uri, int columnIndex) {
        ContentValues uriMap = mCacheMap.get(uri);
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
                mUnderlyingCursor.unregisterContentObserver(mCursorObserver);
            } catch (IllegalStateException e) {
                // Maybe the cursor was GC'd?
            }
            mCursorObserverRegistered = false;
        }
        synchronized(mCacheMapLock) {
            mRefreshRequired = true;
            if (!mPaused) {
                notifyRefreshRequired();
            }
        }
    }

    /**
     * Must be called on UI thread; notify listeners that a refresh is required
     */
    private void notifyRefreshRequired() {
        if (DEBUG) {
            LogUtils.i(TAG, "[Notify %s: onRefreshRequired()]", mName);
        }
        if (!mDeferSync) {
            synchronized(mListeners) {
                for (ConversationListener listener: mListeners) {
                    listener.onRefreshRequired();
                }
            }
        }
    }

    /**
     * Must be called on UI thread; notify listeners that a new cursor is ready
     */
    private void notifyRefreshReady() {
        if (DEBUG) {
            LogUtils.i(TAG, "[Notify %s: onRefreshReady(), %d listeners]",
                    mName, mListeners.size());
        }
        synchronized(mListeners) {
            for (ConversationListener listener: mListeners) {
                listener.onRefreshReady();
            }
        }
    }

    /**
     * Must be called on UI thread; notify listeners that data has changed
     */
    private void notifyDataChanged() {
        if (DEBUG) {
            LogUtils.i(TAG, "[Notify %s: onDataSetChanged()]", mName);
        }
        synchronized(mListeners) {
            for (ConversationListener listener: mListeners) {
                listener.onDataSetChanged();
            }
        }
    }

    /**
     * Put the refreshed cursor in place (called by the UI)
     */
    public void sync() {
        final Wrapper requeryCursor = mRequeryCursor;
        if (requeryCursor == null) {
            // This can happen during an animated deletion, if the UI isn't keeping track, or
            // if a new query intervened (i.e. user changed folders)
            if (DEBUG) {
                LogUtils.i(TAG, "[sync() %s; no requery cursor]", mName);
            }
            return;
        }
        if (DEBUG) {
            LogUtils.i(TAG, "[sync() %s]", mName);
        }
        synchronized(mCacheMapLock) {
            mRequeryCursor = null;
            mRefreshTask = null;
            mRefreshReady = false;
        }
        resetCursor(requeryCursor);

        notifyDataChanged();
    }

    public boolean isRefreshRequired() {
        return mRefreshRequired;
    }

    public boolean isRefreshReady() {
        return mRefreshReady;
    }

    /**
     * Cancel a refresh in progress
     */
    public void cancelRefresh() {
        if (DEBUG) {
            LogUtils.i(TAG, "[cancelRefresh() %s]", mName);
        }
        synchronized(mCacheMapLock) {
            if (mRefreshTask != null) {
                mRefreshTask.cancel(true);
                mRefreshTask = null;
            }
            mRefreshReady = false;
            // If we have the cursor, close it; otherwise, it will get closed when the query
            // finishes (it checks sRefreshInProgress)
            if (mRequeryCursor != null) {
                mRequeryCursor.close();
                mRequeryCursor = null;
            }
        }
    }

    /**
     * Get a list of deletions from ConversationCursor to the refreshed cursor that hasn't yet
     * been swapped into place; this allows the UI to animate these away if desired
     * @return a list of positions deleted in ConversationCursor
     */
    public Collection<Conversation> getRefreshDeletions () {
        return EMPTY_DELETION_LIST;
    }

    /**
     * When we get a requery from the UI, we'll do it, but also clear the cache. The listener is
     * notified when the requery is complete
     * NOTE: This will have to change, of course, when we start using loaders...
     */
    public boolean refresh() {
        if (DEBUG) {
            LogUtils.i(TAG, "[refresh() %s]", mName);
        }
        synchronized(mCacheMapLock) {
            if (mRefreshTask != null) {
                if (DEBUG) {
                    LogUtils.i(TAG, "[refresh() %s returning; already running %d]",
                            mName, mRefreshTask.hashCode());
                }
                return false;
            }
            mRefreshTask = new RefreshTask();
            mRefreshTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        return true;
    }

    public void disable() {
        close();
        mCacheMap.clear();
        mListeners.clear();
        mUnderlyingCursor = null;
    }

    @Override
    public void close() {
        if (mUnderlyingCursor != null && !mUnderlyingCursor.isClosed()) {
            // Unregister our observer on the underlying cursor and close as usual
            if (mCursorObserverRegistered) {
                try {
                    mUnderlyingCursor.unregisterContentObserver(mCursorObserver);
                } catch (IllegalStateException e) {
                    // Maybe the cursor got GC'd?
                }
                mCursorObserverRegistered = false;
            }
            mUnderlyingCursor.close();
        }
    }

    /**
     * Move to the next not-deleted item in the conversation
     */
    @Override
    public boolean moveToNext() {
        while (true) {
            boolean ret = mUnderlyingCursor.moveToNext();
            if (!ret) {
                mPosition = getCount();
                // STOPSHIP
                LogUtils.i(TAG, "*** moveToNext returns false; pos = %d, und = %d, del = %d",
                        mPosition, mUnderlyingCursor.getPosition(), mDeletedCount);
                return false;
            }
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
            boolean ret = mUnderlyingCursor.moveToPrevious();
            if (!ret) {
                // Make sure we're before the first position
                mPosition = -1;
                return false;
            }
            if (getCachedValue(DELETED_COLUMN_INDEX) instanceof Integer) continue;
            mPosition--;
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
        if (mUnderlyingCursor == null) {
            throw new IllegalStateException(
                    "getCount() on disabled cursor: " + mName + "(" + qUri + ")");
        }
        return mUnderlyingCursor.getCount() - mDeletedCount;
    }

    @Override
    public boolean moveToFirst() {
        if (mUnderlyingCursor == null) {
            throw new IllegalStateException(
                    "moveToFirst() on disabled cursor: " + mName + "(" + qUri + ")");
        }
        mUnderlyingCursor.moveToPosition(-1);
        mPosition = -1;
        return moveToNext();
    }

    @Override
    public boolean moveToPosition(int pos) {
        if (mUnderlyingCursor == null) {
            throw new IllegalStateException(
                    "moveToPosition() on disabled cursor: " + mName + "(" + qUri + ")");
        }
        // Handle the "move to first" case before anything else; moveToPosition(0) in an empty
        // SQLiteCursor moves the position to 0 when returning false, which we will mirror.
        // But we don't want to return true on a subsequent "move to first", which we would if we
        // check pos vs mPosition first
        if (pos == 0) {
            return moveToFirst();
        } else if (pos == mPosition) {
            return true;
        } else if (pos > mPosition) {
            while (pos > mPosition) {
                if (!moveToNext()) {
                    return false;
                }
            }
            return true;
        } else if ((mPosition - pos) > pos) {
            // Optimization if it's easier to move forward to position instead of backward
            // STOPSHIP (Remove logging)
            LogUtils.i(TAG, "*** Move from %d to %d, starting from first", mPosition, pos);
            moveToFirst();
            return moveToPosition(pos);
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
        return mUnderlyingCursor.getDouble(columnIndex);
    }

    @Override
    public float getFloat(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Float)obj;
        return mUnderlyingCursor.getFloat(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Integer)obj;
        return mUnderlyingCursor.getInt(columnIndex);
    }

    @Override
    public long getLong(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Long)obj;
        return mUnderlyingCursor.getLong(columnIndex);
    }

    @Override
    public short getShort(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Short)obj;
        return mUnderlyingCursor.getShort(columnIndex);
    }

    @Override
    public String getString(int columnIndex) {
        // If we're asking for the Uri for the conversation list, we return a forwarding URI
        // so that we can intercept update/delete and handle it ourselves
        if (columnIndex == sUriColumnIndex) {
            Uri uri = Uri.parse(mUnderlyingCursor.getString(columnIndex));
            return uriToCachingUriString(uri);
        }
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (String)obj;
        return mUnderlyingCursor.getString(columnIndex);
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (byte[])obj;
        return mUnderlyingCursor.getBlob(columnIndex);
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
            return sResolver.query(
                    uriFromCachingUri(uri), projection, selection, selectionArgs, sortOrder);
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            insertLocal(uri, values);
            return ProviderExecute.opInsert(uri, values);
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            throw new IllegalStateException("Unexpected call to ConversationProvider.delete");
//            updateLocal(uri, values);
           // return ProviderExecute.opUpdate(uri, values);
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            throw new IllegalStateException("Unexpected call to ConversationProvider.delete");
            //deleteLocal(uri);
           // return ProviderExecute.opDelete(uri);
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
                        return sResolver.delete(mUri, null, null);
                    case INSERT:
                        return sResolver.insert(mUri, mValues);
                    case UPDATE:
                        return sResolver.update(mUri,  mValues, null, null);
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

        void addToUndoSequence(Uri uri) {
            if (sSequence != mUndoSequence) {
                mUndoSequence = sSequence;
                mUndoDeleteUris.clear();
            }
            mUndoDeleteUris.add(uri);
        }

        @VisibleForTesting
        void deleteLocal(Uri uri, ConversationCursor conversationCursor) {
            String uriString = uriStringFromCachingUri(uri);
            conversationCursor.cacheValue(uriString, DELETED_COLUMN, true);
            addToUndoSequence(uri);
        }

        @VisibleForTesting
        void undeleteLocal(Uri uri, ConversationCursor conversationCursor) {
            String uriString = uriStringFromCachingUri(uri);
            conversationCursor.cacheValue(uriString, DELETED_COLUMN, false);
        }

        void setMostlyDead(Conversation conv, ConversationCursor conversationCursor) {
            Uri uri = conv.uri;
            String uriString = uriStringFromCachingUri(uri);
            conversationCursor.setMostlyDead(uriString, conv);
            addToUndoSequence(uri);
        }

        void commitMostlyDead(Conversation conv, ConversationCursor conversationCursor) {
            conversationCursor.commitMostlyDead(conv);
        }

        boolean clearMostlyDead(Uri uri, ConversationCursor conversationCursor) {
            String uriString =  uriStringFromCachingUri(uri);
            return conversationCursor.clearMostlyDead(uriString);
        }

        public void undo(ConversationCursor conversationCursor) {
            if (sSequence == mUndoSequence) {
                for (Uri uri: mUndoDeleteUris) {
                    if (!clearMostlyDead(uri, conversationCursor)) {
                        undeleteLocal(uri, conversationCursor);
                    }
                }
                mUndoSequence = 0;
                conversationCursor.recalibratePosition();
            }
        }

        @VisibleForTesting
        void updateLocal(Uri uri, ContentValues values, ConversationCursor conversationCursor) {
            if (values == null) {
                return;
            }
            String uriString = uriStringFromCachingUri(uri);
            for (String columnName: values.keySet()) {
                conversationCursor.cacheValue(uriString, columnName, values.get(columnName));
            }
        }

        public int apply(ArrayList<ConversationOperation> ops,
                ConversationCursor conversationCursor) {
            final HashMap<String, ArrayList<ContentProviderOperation>> batchMap =
                    new HashMap<String, ArrayList<ContentProviderOperation>>();
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
                ContentProviderOperation cpo = op.execute(underlyingUri);
                if (cpo != null) {
                    authOps.add(cpo);
                }
                // Keep track of whether our operations require recalibrating the cursor position
                if (op.mRecalibrateRequired) {
                    recalibrateRequired = true;
                }
            }

            // Recalibrate cursor position if required
            if (recalibrateRequired) {
                conversationCursor.recalibratePosition();
            }

            // Notify listeners that data has changed
            conversationCursor.notifyDataChanged();

            // Send changes to underlying provider
            for (String authority: batchMap.keySet()) {
                try {
                    if (offUiThread()) {
                        sResolver.applyBatch(authority, batchMap.get(authority));
                    } else {
                        final String auth = authority;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    sResolver.applyBatch(auth, batchMap.get(auth));
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

    void setMostlyDead(String uriString, Conversation conv) {
        LogUtils.i(TAG, "[Mostly dead, deferring: %s] ", uriString);
        cacheValue(uriString,
                UIProvider.ConversationColumns.FLAGS, Conversation.FLAG_MOSTLY_DEAD);
        conv.convFlags |= Conversation.FLAG_MOSTLY_DEAD;
        sMostlyDead.add(conv);
        mDeferSync = true;
    }

    void commitMostlyDead(Conversation conv) {
        conv.convFlags &= ~Conversation.FLAG_MOSTLY_DEAD;
        sMostlyDead.remove(conv);
        LogUtils.i(TAG, "[All dead: %s]", conv.uri);
        if (sMostlyDead.isEmpty()) {
            mDeferSync = false;
            checkNotifyUI();
        }
    }

    boolean clearMostlyDead(String uriString) {
        Object val = getCachedValue(uriString,
                UIProvider.CONVERSATION_FLAGS_COLUMN);
        if (val != null) {
            int flags = ((Integer)val).intValue();
            if ((flags & Conversation.FLAG_MOSTLY_DEAD) != 0) {
                cacheValue(uriString, UIProvider.ConversationColumns.FLAGS,
                        flags &= ~Conversation.FLAG_MOSTLY_DEAD);
                return true;
            }
        }
        return false;
    }




    /**
     * ConversationOperation is the encapsulation of a ContentProvider operation to be performed
     * atomically as part of a "batch" operation.
     */
    public class ConversationOperation {
        private static final int MOSTLY = 0x80;
        public static final int DELETE = 0;
        public static final int INSERT = 1;
        public static final int UPDATE = 2;
        public static final int ARCHIVE = 3;
        public static final int MUTE = 4;
        public static final int REPORT_SPAM = 5;
        public static final int REPORT_NOT_SPAM = 6;
        public static final int REPORT_PHISHING = 7;
        public static final int MOSTLY_ARCHIVE = MOSTLY | ARCHIVE;
        public static final int MOSTLY_DELETE = MOSTLY | DELETE;
        public static final int MOSTLY_DESTRUCTIVE_UPDATE = MOSTLY | UPDATE;

        private final int mType;
        private final Uri mUri;
        private final Conversation mConversation;
        private final ContentValues mValues;
        // True if an updated item should be removed locally (from ConversationCursor)
        // This would be the case for a folder change in which the conversation is no longer
        // in the folder represented by the ConversationCursor
        private final boolean mLocalDeleteOnUpdate;
        // After execution, this indicates whether or not the operation requires recalibration of
        // the current cursor position (i.e. it removed or added items locally)
        private boolean mRecalibrateRequired = true;
        // Whether this item is already mostly dead
        private final boolean mMostlyDead;

        public ConversationOperation(int type, Conversation conv) {
            this(type, conv, null);
        }

        public ConversationOperation(int type, Conversation conv, ContentValues values) {
            mType = type;
            mUri = conv.uri;
            mConversation = conv;
            mValues = values;
            mLocalDeleteOnUpdate = conv.localDeleteOnUpdate;
            mMostlyDead = conv.isMostlyDead();
        }

        private ContentProviderOperation execute(Uri underlyingUri) {
            Uri uri = underlyingUri.buildUpon()
                    .appendQueryParameter(UIProvider.SEQUENCE_QUERY_PARAMETER,
                            Integer.toString(sSequence))
                    .build();
            ContentProviderOperation op = null;
            switch(mType) {
                case UPDATE:
                    if (mLocalDeleteOnUpdate) {
                        sProvider.deleteLocal(mUri, ConversationCursor.this);
                    } else {
                        sProvider.updateLocal(mUri, mValues, ConversationCursor.this);
                        mRecalibrateRequired = false;
                    }
                    if (!mMostlyDead) {
                        op = ContentProviderOperation.newUpdate(uri)
                                .withValues(mValues)
                                .build();
                    } else {
                        sProvider.commitMostlyDead(mConversation, ConversationCursor.this);
                    }
                    break;
                case MOSTLY_DESTRUCTIVE_UPDATE:
                    sProvider.setMostlyDead(mConversation, ConversationCursor.this);
                    op = ContentProviderOperation.newUpdate(uri).withValues(mValues).build();
                    break;
                case INSERT:
                    sProvider.insertLocal(mUri, mValues);
                    op = ContentProviderOperation.newInsert(uri)
                            .withValues(mValues).build();
                    break;
                // Destructive actions below!
                // "Mostly" operations are reflected globally, but not locally, except to set
                // FLAG_MOSTLY_DEAD in the conversation itself
                case DELETE:
                    sProvider.deleteLocal(mUri, ConversationCursor.this);
                    if (!mMostlyDead) {
                        op = ContentProviderOperation.newDelete(uri).build();
                    } else {
                        sProvider.commitMostlyDead(mConversation, ConversationCursor.this);
                    }
                    break;
                case MOSTLY_DELETE:
                    sProvider.setMostlyDead(mConversation,ConversationCursor.this);
                    op = ContentProviderOperation.newDelete(uri).build();
                    break;
                case ARCHIVE:
                    sProvider.deleteLocal(mUri, ConversationCursor.this);
                    if (!mMostlyDead) {
                        // Create an update operation that represents archive
                        op = ContentProviderOperation.newUpdate(uri).withValue(
                                ConversationOperations.OPERATION_KEY,
                                ConversationOperations.ARCHIVE)
                                .build();
                    } else {
                        sProvider.commitMostlyDead(mConversation, ConversationCursor.this);
                    }
                    break;
                case MOSTLY_ARCHIVE:
                    sProvider.setMostlyDead(mConversation, ConversationCursor.this);
                    // Create an update operation that represents archive
                    op = ContentProviderOperation.newUpdate(uri).withValue(
                            ConversationOperations.OPERATION_KEY, ConversationOperations.ARCHIVE)
                            .build();
                    break;
                case MUTE:
                    if (mLocalDeleteOnUpdate) {
                        sProvider.deleteLocal(mUri, ConversationCursor.this);
                    }

                    // Create an update operation that represents mute
                    op = ContentProviderOperation.newUpdate(uri).withValue(
                            ConversationOperations.OPERATION_KEY, ConversationOperations.MUTE)
                            .build();
                    break;
                case REPORT_SPAM:
                case REPORT_NOT_SPAM:
                    sProvider.deleteLocal(mUri, ConversationCursor.this);

                    final String operation = mType == REPORT_SPAM ?
                            ConversationOperations.REPORT_SPAM :
                            ConversationOperations.REPORT_NOT_SPAM;

                    // Create an update operation that represents report spam
                    op = ContentProviderOperation.newUpdate(uri).withValue(
                            ConversationOperations.OPERATION_KEY, operation).build();
                    break;
                case REPORT_PHISHING:
                    sProvider.deleteLocal(mUri, ConversationCursor.this);

                    // Create an update operation that represents report spam
                    op = ContentProviderOperation.newUpdate(uri).withValue(
                            ConversationOperations.OPERATION_KEY,
                            ConversationOperations.REPORT_PHISHING).build();
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "No such ConversationOperation type: " + mType);
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
        return mUnderlyingCursor.getColumnIndex(columnName);
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
        return mUnderlyingCursor.getColumnIndexOrThrow(columnName);
    }

    @Override
    public String getColumnName(int columnIndex) {
        return mUnderlyingCursor.getColumnName(columnIndex);
    }

    @Override
    public String[] getColumnNames() {
        return mUnderlyingCursor.getColumnNames();
    }

    @Override
    public int getColumnCount() {
        return mUnderlyingCursor.getColumnCount();
    }

    @Override
    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getType(int columnIndex) {
        return mUnderlyingCursor.getType(columnIndex);
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
        return mUnderlyingCursor == null || mUnderlyingCursor.isClosed();
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
        // Nope. We use ConversationListener to accomplish this.
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        // See above.
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
        return mUnderlyingCursor != null ? mUnderlyingCursor.getExtras() : Bundle.EMPTY;
    }

    @Override
    public Bundle respond(Bundle extras) {
        if (mUnderlyingCursor != null) {
            return mUnderlyingCursor.respond(extras);
        }
        return Bundle.EMPTY;
    }

    @Override
    public boolean requery() {
        return true;
    }

    // Below are methods that update Conversation data (update/delete)

    public int updateBoolean(Context context, Conversation conversation, String columnName,
            boolean value) {
        return updateBoolean(context, Arrays.asList(conversation), columnName, value);
    }

    /**
     * Update an integer column for a group of conversations (see updateValues below)
     */
    public int updateInt(Context context, Collection<Conversation> conversations,
            String columnName, int value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return updateValues(context, conversations, cv);
    }

    /**
     * Update a string column for a group of conversations (see updateValues below)
     */
    public int updateBoolean(Context context, Collection<Conversation> conversations,
            String columnName, boolean value) {
        ContentValues cv = new ContentValues();
        cv.put(columnName, value);
        return updateValues(context, conversations, cv);
    }

    /**
     * Update a string column for a group of conversations (see updateValues below)
     */
    public int updateString(Context context, Collection<Conversation> conversations,
            String columnName, String value) {
        return updateStrings(context, conversations, new String[] {
            columnName
        }, new String[] {
            value
        });
    }

    /**
     * Update a string columns for a group of conversations (see updateValues below)
     */
    public int updateStrings(Context context, Collection<Conversation> conversations,
            String[] columnNames, String[] values) {
        ContentValues cv = new ContentValues();
        for (int i = 0; i < columnNames.length; i++) {
            cv.put(columnNames[i], values[i]);
        }
        return updateValues(context, conversations, cv);
    }

    public int updateBoolean(Context context, String conversationUri, String columnName,
            boolean value) {
        Conversation conv = new Conversation();
        conv.uri = Uri.parse(conversationUri);
        return updateBoolean(context, conv, columnName, value);
    }

    /**
     * Update a boolean column for a group of conversations, immediately in the UI and in a single
     * transaction in the underlying provider
     * @param context the caller's context
     * @param conversations a collection of conversations
     * @param values the data to update
     * @return the sequence number of the operation (for undo)
     */
    public int updateValues(Context context, Collection<Conversation> conversations,
            ContentValues values) {
        return apply(context,
                getOperationsForConversations(conversations, ConversationOperation.UPDATE, values));
    }

    private ArrayList<ConversationOperation> getOperationsForConversations(
            Collection<Conversation> conversations, int type, ContentValues values) {
        final ArrayList<ConversationOperation> ops = Lists.newArrayList();
        for (Conversation conv: conversations) {
            ConversationOperation op = new ConversationOperation(type, conv, values);
            ops.add(op);
        }
        return ops;
    }

    /**
     * Delete a single conversation
     * @param context the caller's context
     * @return the sequence number of the operation (for undo)
     */
    public int delete(Context context, Conversation conversation) {
        ArrayList<Conversation> conversations = new ArrayList<Conversation>();
        conversations.add(conversation);
        return delete(context, conversations);
    }

    /**
     * Delete a single conversation
     * @param context the caller's context
     * @return the sequence number of the operation (for undo)
     */
    public int mostlyArchive(Context context, Conversation conversation) {
        ArrayList<Conversation> conversations = new ArrayList<Conversation>();
        conversations.add(conversation);
        return archive(context, conversations);
    }

    /**
     * Delete a single conversation
     * @param context the caller's context
     * @return the sequence number of the operation (for undo)
     */
    public int mostlyDelete(Context context, Conversation conversation) {
        ArrayList<Conversation> conversations = new ArrayList<Conversation>();
        conversations.add(conversation);
        return delete(context, conversations);
    }

    // Convenience methods
    private int apply(Context context, ArrayList<ConversationOperation> operations) {
        return sProvider.apply(operations, this);
    }

    private void undoLocal() {
        sProvider.undo(this);
    }

    public void undo(final Context context, final Uri undoUri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Cursor c = context.getContentResolver().query(undoUri, UIProvider.UNDO_PROJECTION,
                        null, null, null);
                if (c != null) {
                    c.close();
                }
            }
        }).start();
        undoLocal();
    }

    /**
     * Delete a group of conversations immediately in the UI and in a single transaction in the
     * underlying provider. See applyAction for argument descriptions
     */
    public int delete(Context context, Collection<Conversation> conversations) {
        return applyAction(context, conversations, ConversationOperation.DELETE);
    }

    /**
     * As above, for archive
     */
    public int archive(Context context, Collection<Conversation> conversations) {
        return applyAction(context, conversations, ConversationOperation.ARCHIVE);
    }

    /**
     * As above, for mute
     */
    public int mute(Context context, Collection<Conversation> conversations) {
        return applyAction(context, conversations, ConversationOperation.MUTE);
    }

    /**
     * As above, for report spam
     */
    public int reportSpam(Context context, Collection<Conversation> conversations) {
        return applyAction(context, conversations, ConversationOperation.REPORT_SPAM);
    }

    /**
     * As above, for report not spam
     */
    public int reportNotSpam(Context context, Collection<Conversation> conversations) {
        return applyAction(context, conversations, ConversationOperation.REPORT_NOT_SPAM);
    }

    /**
     * As above, for report phishing
     */
    public int reportPhishing(Context context, Collection<Conversation> conversations) {
        return applyAction(context, conversations, ConversationOperation.REPORT_PHISHING);
    }

    /**
     * As above, for mostly archive
     */
    public int mostlyArchive(Context context, Collection<Conversation> conversations) {
        return applyAction(context, conversations, ConversationOperation.MOSTLY_ARCHIVE);
    }

    /**
     * As above, for mostly delete
     */
    public int mostlyDelete(Context context, Collection<Conversation> conversations) {
        return applyAction(context, conversations, ConversationOperation.MOSTLY_DELETE);
    }

    /**
     * As above, for mostly destructive updates
     */
    public int mostlyDestructiveUpdate(Context context, Collection<Conversation> conversations,
            String column, String value) {
        return mostlyDestructiveUpdate(context, conversations, new String[] {
            column
        }, new String[] {
            value
        });
    }

    /**
     * As above, for mostly destructive updates.
     */
    public int mostlyDestructiveUpdate(Context context, Collection<Conversation> conversations,
            String[] columnNames, String[] values) {
        ContentValues cv = new ContentValues();
        for (int i = 0; i < columnNames.length; i++) {
            cv.put(columnNames[i], values[i]);
        }
        return apply(
                context,
                getOperationsForConversations(conversations,
                        ConversationOperation.MOSTLY_DESTRUCTIVE_UPDATE, cv));
    }

    /**
     * Convenience method for performing an operation on a group of conversations
     * @param context the caller's context
     * @param conversations the conversations to be affected
     * @param opAction the action to take
     * @return the sequence number of the operation applied in CC
     */
    private int applyAction(Context context, Collection<Conversation> conversations,
            int opAction) {
        ArrayList<ConversationOperation> ops = Lists.newArrayList();
        for (Conversation conv: conversations) {
            ConversationOperation op =
                    new ConversationOperation(opAction, conv);
            ops.add(op);
        }
        return apply(context, ops);
    }

}
