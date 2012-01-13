/**
 * Copyright (c) 2012, Google Inc.
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

package com.android.mail.providers;

import android.content.ContentValues;
import android.text.TextUtils;

import com.android.mail.providers.UIProvider.DraftType;
import com.android.mail.providers.UIProvider.MessageColumns;

/**
 * A helper class for creating or updating messages. Use the putXxx methods to
 * provide initial or new values for the message. Then save or send the message.
 * To save or send an existing message without making other changes to it simply
 * provide an empty ContentValues.
 */
public class MessageModification {

    private static final CharSequence EMAIL_SEPARATOR = "\n";

    /**
     * Sets the message's subject. Only valid for drafts.
     * @param values the ContentValues that will be used to create or update the
     *            message
     * @param subject the new subject
     */
    public static void putSubject(ContentValues values, String subject) {
        values.put(MessageColumns.SUBJECT, subject);
    }

    /**
     * Sets the message's to address. Only valid for drafts.
     * @param values the ContentValues that will be used to create or update the
     *            message
     * @param toAddresses the new to addresses
     */
    public static void putToAddresses(ContentValues values, String[] toAddresses) {
        values.put(MessageColumns.TO, TextUtils.join(EMAIL_SEPARATOR, toAddresses));
    }

    /**
     * Sets the message's cc address. Only valid for drafts.
     * @param values the ContentValues that will be used to create or update the
     *            message
     * @param ccAddresses the new cc addresses
     */
    public static void putCcAddresses(ContentValues values, String[] ccAddresses) {
        values.put(MessageColumns.CC, TextUtils.join(EMAIL_SEPARATOR, ccAddresses));
    }

    /**
     * Sets the message's bcc address. Only valid for drafts.
     * @param values the ContentValues that will be used to create or update the
     *            message
     * @param bccAddresses the new bcc addresses
     */
    public static void putBccAddresses(ContentValues values, String[] bccAddresses) {
        values.put(MessageColumns.BCC, TextUtils.join(EMAIL_SEPARATOR, bccAddresses));
    }

    /**
     * Saves a flag indicating the message is forwarded. Only valid for drafts
     * not yet sent to / retrieved from server.
     * @param values the ContentValues that will be used to create or update the
     *            message
     * @param forward true if the message is forwarded
     */
    public static void putForward(ContentValues values, boolean forward) {
        values.put(MessageColumns.DRAFT_TYPE, DraftType.FORWARD);
    }

    /**
     * Saves an include quoted text flag. Only valid for drafts not yet sent to
     * / retrieved from server.
     * @param values the ContentValues that will be used to create or update the
     *            message
     * @param includeQuotedText the include quoted text flag
     */
    public static void putAppendRefMessageContent(ContentValues values, boolean includeQuotedText) {
        values.put(MessageColumns.APPEND_REF_MESSAGE_CONTENT, includeQuotedText);
    }

    /**
     * Saves a new body for the message. Only valid for drafts.
     * @param values the ContentValues that will be used to create or update the
     *            message
     * @param body the new body of the message
     */
    public static void putBody(ContentValues values, String body) {
        values.put(MessageColumns.BODY_TEXT, body);
    }

    /**
     * Saves a new body for the message. Only valid for drafts.
     * @param values the ContentValues that will be used to create or update the
     *            message
     * @param body the new body of the message
     */
    public static void putBodyHtml(ContentValues values, String body) {
        values.put(MessageColumns.BODY_HTML, body);
    }
}
