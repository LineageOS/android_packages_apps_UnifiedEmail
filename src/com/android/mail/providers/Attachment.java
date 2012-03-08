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

import com.google.common.collect.Lists;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.mail.providers.UIProvider.AttachmentColumns;
import com.android.mail.providers.UIProvider.AttachmentDestination;
import com.android.mail.providers.UIProvider.AttachmentState;

import java.util.ArrayList;

public class Attachment implements Parcelable {
    public static final int SERVER_ATTACHMENT = 0;
    /** Extras are "<path>". */
    public static final int  LOCAL_FILE = 1;

    /**
     * Attachment file name. See {@link AttachmentColumns#NAME}.
     */
    public String name;

    /**
     * Attachment size in bytes. See {@link AttachmentColumns#SIZE}.
     */
    public int size;

    /**
     * See {@link AttachmentColumns#URI}.
     */
    public Uri uri;

    /**
     * MIME type of the file. See {@link AttachmentColumns#CONTENT_TYPE}.
     */
    // TODO: rename to be consistent with UIProvider name: "contentType"
    @Deprecated
    public String mimeType;

    /**
     * See {@link AttachmentColumns#STATE}.
     */
    public int state;

    /**
     * See {@link AttachmentColumns#DESTINATION}.
     */
    public int destination;

    /**
     * See {@link AttachmentColumns#DOWNLOADED_SIZE}.
     */
    public int downloadedSize;

    /**
     * See {@link AttachmentColumns#CONTENT_URI}.
     */
    // TODO: change this to be a Uri for consistency with other URIs in data model objects.
    @Deprecated
    public String contentUri;

    /**
     * See {@link AttachmentColumns#THUMBNAIL_URI}. Might be null.
     */
    public Uri thumbnailUri;

    /**
     * See {@link AttachmentColumns#PREVIEW_INTENT}. Might be null.
     */
    public Intent previewIntent;

    /**
     * Part id of the attachment.
     */
    public String partId;

    public int origin;

    /**
     * Attachment origin info.
     * TODO: do we want this? Or location?
     */
    public String originExtras;

    public Attachment(Parcel in) {
        name = in.readString();
        size = in.readInt();
        uri = in.readParcelable(null);
        mimeType = in.readString();
        state = in.readInt();
        destination = in.readInt();
        downloadedSize = in.readInt();
        contentUri = in.readString();
        thumbnailUri = in.readParcelable(null);
        previewIntent = in.readParcelable(null);
        partId = in.readString();
        origin = in.readInt();
        originExtras = in.readString();
    }

    public Attachment() {
    }

    public Attachment(Cursor cursor) {
        if (cursor == null) {
            return;
        }

        name = cursor.getString(UIProvider.ATTACHMENT_NAME_COLUMN);
        size = cursor.getInt(UIProvider.ATTACHMENT_SIZE_COLUMN);
        uri = Uri.parse(cursor.getString(UIProvider.ATTACHMENT_URI_COLUMN));
        mimeType = cursor.getString(UIProvider.ATTACHMENT_CONTENT_TYPE_COLUMN);
        state = cursor.getInt(UIProvider.ATTACHMENT_STATE_COLUMN);
        destination = cursor.getInt(UIProvider.ATTACHMENT_DESTINATION_COLUMN);
        downloadedSize = cursor.getInt(UIProvider.ATTACHMENT_DOWNLOADED_SIZE_COLUMN);
        // TODO: change to use parseUri()
        contentUri = cursor.getString(UIProvider.ATTACHMENT_CONTENT_URI_COLUMN);
        thumbnailUri = parseOptionalUri(
                cursor.getString(UIProvider.ATTACHMENT_THUMBNAIL_URI_COLUMN));
        previewIntent = getOptionalIntentFromBlob(
                cursor.getBlob(UIProvider.ATTACHMENT_PREVIEW_INTENT_COLUMN));

        // TODO: ensure that local files attached to a draft have sane values, like SAVED/EXTERNAL
        // and that contentUri is populated
    }

    public Attachment(String attachmentString) {
        String[] attachmentValues = attachmentString.split("\\|");
        if (attachmentValues != null) {
            partId = attachmentValues[0];
            name = attachmentValues[1];
            mimeType = attachmentValues[2];
            try {
                size = Integer.parseInt(attachmentValues[3]);
            } catch (NumberFormatException e) {
                size = 0;
            }
            mimeType = attachmentValues[4];
            origin = Integer.parseInt(attachmentValues[5]);
            contentUri = attachmentValues[6];
            originExtras = attachmentValues[7];
        }
    }

    public String toJoinedString() {
        // FIXME: mimeType is read/written twice
        return TextUtils.join("|", Lists.newArrayList(partId == null ? "" : partId,
                name == null ? "" : name.replaceAll("[|\n]", ""), mimeType, size, mimeType,
                origin + "", contentUri, TextUtils.isEmpty(originExtras) ? contentUri
                        : originExtras, ""));
    }

    private static Intent getOptionalIntentFromBlob(byte[] blob) {
        if (blob == null) {
            return null;
        }
        final Parcel intentParcel = Parcel.obtain();
        intentParcel.unmarshall(blob, 0, blob.length);
        final Intent intent = new Intent();
        intent.readFromParcel(intentParcel);
        return intent;
    }

    private static Uri parseOptionalUri(String uriString) {
        return uriString == null ? null : Uri.parse(uriString);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(size);
        dest.writeParcelable(uri, flags);
        dest.writeString(mimeType);
        dest.writeInt(state);
        dest.writeInt(destination);
        dest.writeInt(downloadedSize);
        dest.writeString(contentUri);
        dest.writeParcelable(thumbnailUri, flags);
        dest.writeParcelable(previewIntent, flags);
        dest.writeString(partId);
        dest.writeInt(origin);
        dest.writeString(originExtras);
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

    public boolean isImage() {
        return mimeType.startsWith("image");
    }

    public boolean isDownloading() {
        return state == AttachmentState.DOWNLOADING;
    }

    public boolean isPresentLocally() {
        return state == AttachmentState.SAVED || origin == LOCAL_FILE;
    }

    public boolean isSavedToExternal() {
        return state == AttachmentState.SAVED && destination == AttachmentDestination.EXTERNAL;
    }

    public boolean canSave() {
        return origin == SERVER_ATTACHMENT && state != AttachmentState.DOWNLOADING
                && state != AttachmentState.SAVED;
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
