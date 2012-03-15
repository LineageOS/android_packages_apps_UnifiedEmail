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

import com.google.common.collect.ImmutableMap;

import android.content.Context;
import android.text.TextUtils;

import com.android.mail.R;
import com.android.mail.providers.Attachment;

import java.text.DecimalFormat;
import java.util.Map;

public class AttachmentUtils {
    private static final int KILO = 1024;
    private static final int MEGA = KILO * KILO;

    /**
     * Singleton map of MIME->friendly description
     * @see #getMimeTypeDisplayName(Context, String)
     */
    private static Map<String, String> sDisplayNameMap;

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
     * Return a friendly localized file type for this attachment, or the empty string if
     * unknown.
     * @param context a Context to do resource lookup against
     * @return friendly file type or empty string
     */
    public static String getDisplayType(final Context context, final Attachment attachment) {
        // try to get a friendly name for the exact mime type
        // then try to show a friendly name for the mime family
        // finally, give up and just show the file extension
        String displayType = getMimeTypeDisplayName(context, attachment.contentType);
        int index = !TextUtils.isEmpty(attachment.contentType) ? attachment.contentType
                .indexOf('/') : -1;
        if (displayType == null && index > 0) {
            displayType = getMimeTypeDisplayName(context,
                    attachment.contentType.substring(0, index));
        }
        if (displayType == null) {
            String extension = Utils.getFileExtension(attachment.name);
            // show '$EXTENSION File' for unknown file types
            if (extension != null && extension.length() > 1 && extension.indexOf('.') == 0) {
                displayType = context.getString(R.string.attachment_unknown,
                        extension.substring(1).toUpperCase());
            }
        }
        if (displayType == null) {
            // no extension to display, but the map doesn't accept null entries
            displayType = "";
        }
        return displayType;
    }

    /**
     * Returns a user-friendly localized description of either a complete a MIME type or a
     * MIME family.
     * @param context used to look up localized strings
     * @param type complete MIME type or just MIME family
     * @return localized description text, or null if not recognized
     */
    public static synchronized String getMimeTypeDisplayName(final Context context,
            String type) {
        if (sDisplayNameMap == null) {
            String docName = context.getString(R.string.attachment_application_msword);
            String presoName = context.getString(R.string.attachment_application_vnd_ms_powerpoint);
            String sheetName = context.getString(R.string.attachment_application_vnd_ms_excel);

            sDisplayNameMap = new ImmutableMap.Builder<String, String>()
                .put("image", context.getString(R.string.attachment_image))
                .put("audio", context.getString(R.string.attachment_audio))
                .put("video", context.getString(R.string.attachment_video))
                .put("text", context.getString(R.string.attachment_text))
                .put("application/pdf", context.getString(R.string.attachment_application_pdf))

                // Documents
                .put("application/msword", docName)
                .put("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        docName)

                // Presentations
                .put("application/vnd.ms-powerpoint",
                        presoName)
                .put("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        presoName)

                // Spreadsheets
                .put("application/vnd.ms-excel", sheetName)
                .put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        sheetName)

                .build();
        }
        return sDisplayNameMap.get(type);
    }
}
