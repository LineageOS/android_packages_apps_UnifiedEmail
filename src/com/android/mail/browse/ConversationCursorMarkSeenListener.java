/*******************************************************************************
 *      Copyright (C) 2013 Google Inc.
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

import android.database.Cursor;
import android.database.CursorWrapper;

public interface ConversationCursorMarkSeenListener {
    /**
     * Marks all contents of this cursor as seen.
     */
    void markContentsSeen();

    public class MarkSeenHelper {
        /**
         * Invokes {@link ConversationCursorMarkSeenListener#markContentsSeen(Cursor)} on the
         * specified {@link Cursor}, recursively calls {@link #markContentsSeen(Cursor)} on a
         * wrapped cursor, or returns.
         */
        public static void markContentsSeen(final Cursor cursor) {
            if (cursor == null) {
                return;
            }

            if (cursor instanceof ConversationCursorMarkSeenListener) {
                ((ConversationCursorMarkSeenListener) cursor).markContentsSeen();
            } else if (cursor instanceof CursorWrapper) {
                markContentsSeen(((CursorWrapper) cursor).getWrappedCursor());
            }
        }
    }
}
