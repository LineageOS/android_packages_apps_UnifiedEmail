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

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.CursorAdapter;

import java.util.HashMap;
import java.util.List;

/**
 * ConversationCursor is a wrapper around a conversation list cursor that provides update/delete
 * caching for quick UI response. This is effectively a singleton class, as the cache is
 * implemented as a static HashMap.
 */
public class ConversationCursor extends CursorWrapper {
    private static final String TAG = "ConversationCursor";
    private static final boolean DEBUG = true;  // STOPSHIP Set to false before shipping

    // The authority of our conversation provider (a forwarding provider)
    // This string must match the declaration in AndroidManifest.xml
    private static final String sAuthority = "com.android.mail.conversation.provider";

    // A mapping from Uri to updated ContentValues
    private static HashMap<String, ContentValues> sCacheMap = new HashMap<String, ContentValues>();
    // A deleted row is indicated by the presence of DELETED_COLUMN in the cache map
    private static final String DELETED_COLUMN = "__deleted__";
    // A sentinel value for the "index" of the deleted column; it's an int that is otherwise invalid
    private static final int DELETED_COLUMN_INDEX = -1;

    // The cursor underlying the caching cursor
    private final Cursor mUnderlying;
    // Column names for this cursor
    private final String[] mColumnNames;
    // The index of the Uri whose data is reflected in the cached row
    // Updates/Deletes to this Uri are cached
    private final int mUriColumnIndex;
    // The resolver for the cursor instantiator's context
    private static ContentResolver mResolver;
    // An observer on the underlying cursor (so we can detect changes from outside the UI)
    private final CursorObserver mCursorObserver;
    // The adapter using this cursor (which needs to refresh when data changes)
    private static CursorAdapter mAdapter;

    // The current position of the cursor
    private int mPosition = -1;
    // The number of cached deletions from this cursor (used to quickly generate an accurate count)
    private static int sDeletedCount = 0;

    public ConversationCursor(Cursor cursor, Context context, String messageListColumn) {
        super(cursor);
        mUnderlying = cursor;
        mCursorObserver = new CursorObserver();
        // New cursor -> clear the cache
        resetCache();
        mColumnNames = cursor.getColumnNames();
        mUriColumnIndex = getColumnIndex(messageListColumn);
        if (mUriColumnIndex < 0) {
            throw new IllegalArgumentException("Cursor must include a message list column");
        }
        mResolver = context.getContentResolver();
        // We'll observe the underlying cursor and act when it changes
        //cursor.registerContentObserver(mCursorObserver);
    }

    /**
     * Reset the cache; this involves clearing out our cache map and resetting our various counts
     * The cache should be reset whenever we get fresh data from the underlying cursor
     */
    private void resetCache() {
        sCacheMap.clear();
        sDeletedCount = 0;
        mPosition = -1;
        mUnderlying.registerContentObserver(mCursorObserver);
    }

    /**
     * Set the adapter for this cursor; we'll notify it when our data changes
     */
    public void setAdapter(CursorAdapter adapter) {
        mAdapter = adapter;
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
        return uri.getScheme() + "://" + sAuthority + "/" + provider + uri.getPath();
    }

    /**
     * Regenerate the original Uri from a forwarding (ConversationProvider) Uri
     * NOTE: See note above for uriToCachingUri
     * @param uri the forwarding Uri
     * @return the original Uri
     */
    private static Uri uriFromCachingUri(Uri uri) {
        List<String> path = uri.getPathSegments();
        Uri.Builder builder = new Uri.Builder().scheme(uri.getScheme()).authority(path.get(0));
        for (int i = 1; i < path.size(); i++) {
            builder.appendPath(path.get(i));
        }
        return builder.build();
    }

    /**
     * Cache a column name/value pair for a given Uri
     * @param uriString the Uri for which the column name/value pair applies
     * @param columnName the column name
     * @param value the value to be cached
     */
    private static void cacheValue(String uriString, String columnName, Object value) {
        // Get the map for our uri
        ContentValues map = sCacheMap.get(uriString);
        // Create one if necessary
        if (map == null) {
            map = new ContentValues();
            sCacheMap.put(uriString, map);
        }
        // If we're caching a deletion, add to our count
        if ((columnName == DELETED_COLUMN) && (map.get(columnName) == null)) {
            sDeletedCount++;
            if (DEBUG) {
                Log.d(TAG, "Deleted " + uriString);
            }
        }
        // ContentValues has no generic "put", so we must test.  For now, the only classes of
        // values implemented are Boolean/Integer/String, though others are trivially added
        if (value instanceof Boolean) {
            map.put(columnName, ((Boolean)value).booleanValue() ? 1 : 0);
        } else if (value instanceof Integer) {
            map.put(columnName, (Integer)value);
        } else if (value instanceof String) {
            map.put(columnName, (String)value);
        } else {
            String cname = value.getClass().getName();
            throw new IllegalArgumentException("Value class not compatible with cache: " + cname);
        }

        // Since we've changed the data, alert the adapter to redraw
        mAdapter.notifyDataSetChanged();
        if (DEBUG && (columnName != DELETED_COLUMN)) {
            Log.d(TAG, "Caching value for " + uriString + ": " + columnName);
        }
    }

    /**
     * Get the cached value for the provided column; we special case -1 as the "deleted" column
     * @param columnIndex the index of the column whose cached value we want to retrieve
     * @return the cached value for this column, or null if there is none
     */
    private Object getCachedValue(int columnIndex) {
        String uri = super.getString(mUriColumnIndex);
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
     * When the underlying cursor changes, we want to force a requery to get the new provider data;
     * the cache must also be reset here since it's no longer fresh
     */
    private void underlyingChanged() {
        super.requery();
        resetCache();
    }

    // We don't want to do anything when we get a requery, as our data is updated immediately from
    // the UI and we detect changes on the underlying provider above
    public boolean requery() {
        return true;
    }

    public void close() {
        // Unregister our observer on the underlying cursor and close as usual
        mUnderlying.unregisterContentObserver(mCursorObserver);
        super.close();
    }

    /**
     * Move to the next not-deleted item in the conversation
     */
    public boolean moveToNext() {
        while (true) {
            boolean ret = super.moveToNext();
            if (!ret) return false;
            if (getCachedValue(DELETED_COLUMN_INDEX) instanceof Integer) continue;
            mPosition++;
            return true;
        }
    }

    /**
     * Move to the previous not-deleted item in the conversation
     */
    public boolean moveToPrevious() {
        while (true) {
            boolean ret = super.moveToPrevious();
            if (!ret) return false;
            if (getCachedValue(-1) instanceof Integer) continue;
            mPosition--;
            return true;
        }
    }

    public int getPosition() {
        return mPosition;
    }

    /**
     * The actual cursor's count must be decremented by the number we've deleted from the UI
     */
    public int getCount() {
        return super.getCount() - sDeletedCount;
    }

    public boolean moveToFirst() {
        super.moveToPosition(-1);
        mPosition = -1;
        return moveToNext();
    }

    public boolean moveToPosition(int pos) {
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

    public boolean moveToLast() {
        throw new UnsupportedOperationException("moveToLast unsupported!");
    }

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
        return super.getDouble(columnIndex);
    }

    @Override
    public float getFloat(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Float)obj;
        return super.getFloat(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Integer)obj;
        return super.getInt(columnIndex);
    }

    @Override
    public long getLong(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Long)obj;
        return super.getLong(columnIndex);
    }

    @Override
    public short getShort(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (Short)obj;
        return super.getShort(columnIndex);
    }

    @Override
    public String getString(int columnIndex) {
        // If we're asking for the Uri for the conversation list, we return a forwarding URI
        // so that we can intercept update/delete and handle it ourselves
        if (columnIndex == mUriColumnIndex) {
            Uri uri = Uri.parse(super.getString(columnIndex));
            return uriToCachingUriString(uri);
        }
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (String)obj;
        return super.getString(columnIndex);
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        Object obj = getCachedValue(columnIndex);
        if (obj != null) return (byte[])obj;
        return super.getBlob(columnIndex);
    }

    /**
     * Observer of changes to underlying data
     */
    private class CursorObserver extends ContentObserver {
        public CursorObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            // If we're here, then something outside of the UI has changed the data, and we
            // must requery to get that data from the underlying provider
            if (DEBUG) {
                Log.d(TAG, "Underlying conversation cursor changed; requerying");
            }
            // It's not at all obvious to me why we must unregister/re-register after the requery
            // However, if we don't we'll only get one notification and no more...
            mUnderlying.unregisterContentObserver(mCursorObserver);
            ConversationCursor.this.underlyingChanged();
        }
    }

    /**
     * ConversationProvider is the ContentProvider for our forwarding Uri's; it passes queries
     * and inserts directly, and caches updates/deletes before passing them through.  The caching
     * will cause a redraw of the list with updated values.
     */
    public static class ConversationProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            return false;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            return mResolver.query(
                    uriFromCachingUri(uri), projection, selection, selectionArgs, sortOrder);
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

            static void opDelete(Uri uri) {
                new Thread(new ProviderExecute(DELETE, uri)).start();
            }

            static void opInsert(Uri uri, ContentValues values) {
                new Thread(new ProviderExecute(INSERT, uri, values)).start();
            }

            static void opUpdate(Uri uri, ContentValues values) {
                new Thread(new ProviderExecute(UPDATE, uri, values)).start();
            }

            @Override
            public void run() {
                switch(mCode) {
                    case DELETE:
                        mResolver.delete(mUri, null, null);
                        break;
                    case INSERT:
                        mResolver.insert(mUri, mValues);
                        break;
                    case UPDATE:
                        mResolver.update(mUri,  mValues, null, null);
                        break;
                }
            }
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            ProviderExecute.opInsert(uri, values);
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            Uri underlyingUri = uriFromCachingUri(uri);
            String uriString = underlyingUri.toString();
            cacheValue(uriString, DELETED_COLUMN, true);
            ProviderExecute.opDelete(uri);
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            Uri underlyingUri = uriFromCachingUri(uri);
            // Remember to decode the underlying Uri as it might be encoded (as w/ Gmail)
            String uriString =  Uri.decode(underlyingUri.toString());
            for (String columnName: values.keySet()) {
                cacheValue(uriString, columnName, values.get(columnName));
            }
            ProviderExecute.opUpdate(uri, values);
            return 0;
        }
    }
}
