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

public class Attachment implements Parcelable {
    /**
     * Attachment name.
     */
    public String name;
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
     * isSynced is true if the attachment is available locally on the device.
     */
    public boolean isSynced;
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
        isSynced = in.readInt() == 1;
        size = in.readLong();
    }

    public Attachment() {
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
        dest.writeInt(isSynced? 1 : 0);
        dest.writeLong(size);
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
}
