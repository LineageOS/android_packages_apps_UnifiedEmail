/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.email.providers;

import com.android.email.utils.LogUtils;
import com.android.email.utils.Utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.google.common.collect.Sets;

import java.io.Console;
import java.lang.Override;
import java.lang.String;
import java.lang.System;
import java.util.HashSet;
import java.util.Set;


// TODO: Create a UiProviderTest, and change MockUiProviderTests to extend it.  This would allow us
// to share the validation logic when we add new UI providers.

public class MockUiProviderTests extends AndroidTestCase {

    private Set<String> mTraversedUris;
    private String mLogTag;

    @Override
    public void setUp() {
        mLogTag = new LogUtils().getLogTag();
    }

    @SmallTest
    public void testTraverseContentProvider() {

        // Get the starting Uri
        final Uri accountUri = MockUiProvider.getAccountsUri();

        mTraversedUris = new HashSet<String>();
        traverseUri(accountUri);
    }

    private void traverseUri(Uri uri) {
        if (uri == null) {
            return;
        }

        LogUtils.i(mLogTag, "Traversing: %s", uri.toString());

        final ContentResolver resolver = getContext().getContentResolver();

        final Cursor cursor = resolver.query(uri, null, null, null, null);

        mTraversedUris.add(uri.toString());

        if (cursor != null) {
            try {
                // get the columns
                final String[] columns = cursor.getColumnNames();

                // Go through each of rows
                while (cursor.moveToNext()) {

                    // Go through each of the columns find the ones that returns uris
                    for (String columnName : columns) {
                        if (columnName.toLowerCase().contains("uri")) {
                            final String uriString =
                                    cursor.getString(cursor.getColumnIndex(columnName));

                            if (!mTraversedUris.contains(uriString)) {
                                final Uri childUri = Uri.parse(uriString);

                                traverseUri(childUri);
                            }
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        } else {
            // fail("query returned null");
            LogUtils.e(mLogTag, "query returned null: %s", uri.toString());
        }
    }


    /**
     * Test to add
     * 1) Make sure that the query result columns match the UIProvider schema
     * 2) Make sure the data is valid
     */
}