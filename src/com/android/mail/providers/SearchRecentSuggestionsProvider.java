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

package com.android.mail.providers;

import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;
import android.text.TextUtils;

import com.android.mail.R;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;

public class SearchRecentSuggestionsProvider {
    /*
     * String used to delimit different parts of a query.
     */
    public static final String QUERY_TOKEN_SEPARATOR = " ";

    // general database configuration and tables
    private SQLiteOpenHelper mOpenHelper;
    private static final String sDatabaseName = "suggestions.db";
    private static final String sSuggestions = "suggestions";
    private static final String ORDER_BY = "date DESC";

    // Table of database versions.  Don't forget to update!
    // NOTE:  These version values are shifted left 8 bits (x 256) in order to create space for
    // a small set of mode bitflags in the version int.
    //
    // 1      original implementation with queries, and 1 or 2 display columns
    // 1->2   added UNIQUE constraint to display1 column
    // 2->3   <redacted> being dumb and accidentally upgraded, this should be ignored.
    private static final int DATABASE_VERSION = 3 * 256;

    private static final int DATABASE_VERSION_2 = 2 * 256;
    private static final int DATABASE_VERSION_3 = 3 * 256;

    private String mSuggestSuggestionClause;
    private String[] mSuggestionProjection;

    protected final Context mContext;

    public SearchRecentSuggestionsProvider(Context context) {
        mContext = context;
        mOpenHelper = new DatabaseHelper(mContext, DATABASE_VERSION);
    }

    public void cleanup() {
        mOpenHelper.close();
    }

    /**
     * Builds the database.  This version has extra support for using the version field
     * as a mode flags field, and configures the database columns depending on the mode bits
     * (features) requested by the extending class.
     *
     * @hide
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context, int newVersion) {
            super(context, sDatabaseName, null, newVersion);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            StringBuilder builder = new StringBuilder();
            builder.append("CREATE TABLE suggestions (" +
                    "_id INTEGER PRIMARY KEY" +
                    ",display1 TEXT UNIQUE ON CONFLICT REPLACE" +
                    ",query TEXT" +
                    ",date LONG" +
                    ");");
            db.execSQL(builder.toString());
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // When checking the old version clear the last 8 bits
            oldVersion = oldVersion & ~0xff;
            newVersion = newVersion & ~0xff;
            if (oldVersion == DATABASE_VERSION_2 && newVersion == DATABASE_VERSION_3) {
                // Oops, didn't mean to upgrade this database. Ignore this upgrade.
                return;
            }
            db.execSQL("DROP TABLE IF EXISTS suggestions");
            onCreate(db);
        }
    }

    /**
     * In order to use this class, you must extend it, and call this setup function from your
     * constructor.  In your application or activities, you must provide the same values when
     * you create the {@link android.provider.SearchRecentSuggestions} helper.
     */
    protected void setupSuggestions() {
        // The URI of the icon that we will include on every suggestion here.
        final String historicalIcon = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + mContext.getPackageName() + "/" + R.drawable.ic_history_24dp;

        mSuggestSuggestionClause = "display1 LIKE ?";
        mSuggestionProjection = new String [] {
                "_id",
                "display1 AS " + SearchManager.SUGGEST_COLUMN_TEXT_1,
                "query AS " + SearchManager.SUGGEST_COLUMN_QUERY,
                "'" + historicalIcon + "' AS " + SearchManager.SUGGEST_COLUMN_ICON_1
        };
    }

    private ArrayList<String> mFullQueryTerms;

    /**
     *  Copy the projection, and change the query field alone.
     * @param selectionArgs
     * @return projection
     */
    private String[] createProjection(String[] selectionArgs) {
        String[] newProjection = new String[mSuggestionProjection.length];
        String queryAs;
        int fullSize = (mFullQueryTerms != null) ? mFullQueryTerms.size() : 0;
        if (fullSize > 0) {
            String realQuery = "'";
            for (int i = 0; i < fullSize; i++) {
                realQuery+= mFullQueryTerms.get(i);
                if (i < fullSize -1) {
                    realQuery += QUERY_TOKEN_SEPARATOR;
                }
            }
            queryAs = realQuery + " ' || query AS " + SearchManager.SUGGEST_COLUMN_QUERY;
        } else {
            queryAs = "query AS " + SearchManager.SUGGEST_COLUMN_QUERY;
        }
        for (int i = 0; i < mSuggestionProjection.length; i++) {
            newProjection[i] = mSuggestionProjection[i];
        }
        // Assumes that newProjection[length-2] is the query field.
        newProjection[mSuggestionProjection.length - 2] = queryAs;
        return newProjection;
    }

    /**
     * Set the other query terms to be included in the user's query.
     * These are in addition to what is being looked up for suggestions.
     * @param terms
     */
    public void setFullQueryTerms(ArrayList<String> terms) {
        mFullQueryTerms = terms;
    }

    // TODO: Confirm no injection attacks here, or rewrite.
    public Cursor query(String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        // special case for actual suggestions (from search manager)
        String suggestSelection;
        String[] myArgs;
        if (TextUtils.isEmpty(selectionArgs[0])) {
            suggestSelection = null;
            myArgs = null;
        } else {
            String like = "%" + selectionArgs[0] + "%";
            myArgs = new String[] { like };
            suggestSelection = mSuggestSuggestionClause;
        }
        // Suggestions are always performed with the default sort order
        Cursor c = db.query(sSuggestions, createProjection(selectionArgs), suggestSelection, myArgs,
                null, null, ORDER_BY, null);
        return c;
    }

    /**
     * We are going to keep track of recent suggestions ourselves and not depend on the framework.
     * Note that this writes to disk. DO NOT CALL FROM MAIN THREAD.
     */
    public void saveRecentQuery(String query) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        ContentValues values = new ContentValues(3);
        values.put("display1", query);
        values.put("query", query);
        values.put("date", System.currentTimeMillis());
        // Note:  This table has on-conflict-replace semantics, so insert() may actually replace()
        db.insert(sSuggestions, null, values);
    }

    public void clearHistory() {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        db.delete(sSuggestions, null, null);
    }
}