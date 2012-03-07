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
package com.android.mail.providers;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.google.common.collect.Lists;

import java.util.ArrayList;

public class Attachment implements Parcelable {
    public static final int SERVER_ATTACHMENT = 0;
    /** Extras are "<path>". */
    public static final int  LOCAL_FILE = 1;

    /**
     * Attachment name.
     */
    public String name;

    public int origin;

    /**
     * Attachment origin info.
     * TODO: do we want this? Or location?
     */
    public String originExtras;
    /**
     * Mime type of the file.
     */
    public String mimeType;
    /**
     * Content uri location of the attachment.
     */
    public String contentUri;
    /**
     * Part id of the attachment.
     */
    public String partId;
    /**
     * Attachment size in kb.
     */
    public long size;

    public Attachment(Parcel in) {
        name = in.readString();
        originExtras = in.readString();
        mimeType = in.readString();
        contentUri = in.readString();
        partId = in.readString();
        size = in.readLong();
        origin = in.readInt();
    }

    public Attachment() {
    }

    public Attachment(String attachmentString) {
        String[] attachmentValues = attachmentString.split("\\|");
        if (attachmentValues != null) {
            partId = attachmentValues[0];
            name = attachmentValues[1];
            mimeType = attachmentValues[2];
            try {
                size = Long.parseLong(attachmentValues[3]);
            } catch (NumberFormatException e) {
                size = 0;
            }
            mimeType = attachmentValues[4];
            origin = Integer.parseInt(attachmentValues[5]);
            contentUri = attachmentValues[6];
            originExtras = attachmentValues[7];
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(originExtras);
        dest.writeString(mimeType);
        dest.writeString(contentUri);
        dest.writeString(partId);
        dest.writeLong(size);
        dest.writeInt(origin);
    }

    public static final Creator<Attachment> CREATOR = new Creator<Attachment>() {
        @Override
        public Attachment createFromParcel(Parcel source) {
            return new Attachment(source);
        }

        @Override
        public Attachment[] newArray(int size) {
            return new Attachment[size];
        }
    };


    public String toJoinedString() {
        return TextUtils.join("|", Lists.newArrayList(partId == null ? "" : partId,
                name == null ? "" : name.replaceAll("[|\n]", ""), mimeType, size, mimeType,
                origin + "", contentUri, TextUtils.isEmpty(originExtras) ? contentUri
                        : originExtras, ""));
    }

    public boolean isImage() {
        return mimeType.startsWith("image");
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
