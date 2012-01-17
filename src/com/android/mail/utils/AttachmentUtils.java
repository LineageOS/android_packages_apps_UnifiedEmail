/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.mail.utils;

import android.content.Context;
import android.text.TextUtils;

import com.android.mail.R;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;

import java.text.DecimalFormat;
import java.util.ArrayList;

public class AttachmentUtils {
    private static final int KILO = 1024;
    private static final int MEGA = KILO * KILO;

    /**
     * @return A string suitable for display in bytes, kilobytes or megabytes
     *         depending on its size.
     */
    public static String convertToHumanReadableSize(Context context, long size) {
        if (size < KILO) {
            return size + context.getString(R.string.bytes);
        } else if (size < MEGA) {
            return (size / KILO) + context.getString(R.string.kilobytes);
        } else {
            DecimalFormat onePlace = new DecimalFormat("0.#");
            return onePlace.format((float) size / (float) MEGA)
                    + context.getString(R.string.megabytes);
        }
    }

    /**
     * Translate attachment info from a message into attachment objects.
     * @param msg the message
     * @return list of Attachment objects, or an empty list if the message
     * had no associated attachments.
     */
    public static ArrayList<Attachment> getAttachmentsFromMessage(Message msg) {
        return getAttachmentsFromJoinedAttachmentInfo(msg.joinedAttachmentInfos);
    }

    /**
     * Translate joines attachment info from a message into attachment objects.
     * @param infoString the joined attachment info string
     * @return list of Attachment objects, or an empty list if the message
     * had no associated attachments.
     */
    public static ArrayList<Attachment> getAttachmentsFromJoinedAttachmentInfo(String infoString) {
        ArrayList<Attachment> infoList = new ArrayList<Attachment>();
        if (!TextUtils.isEmpty(infoString)) {
            Attachment attachment;
            String[] attachmentStrings = infoString
                    .split(UIProvider.MESSAGE_ATTACHMENT_INFO_SEPARATOR);
            for (String attachmentString : attachmentStrings) {
                attachment = new Attachment(attachmentString);
                infoList.add(attachment);
            }
        }
        return infoList;
    }
}
