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

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Objects;

public class MessageInfo implements Parcelable {
    public static final String SENDER_LIST_TOKEN_ELIDED = " .. ";

    public boolean read;
    public boolean starred;
    public String sender;
    public int priority;

    public MessageInfo() {
    }

    public MessageInfo(boolean isRead, boolean isStarred, String senderString, int p) {
        set(isRead, isStarred, senderString, p);
    }

    private MessageInfo(Parcel in) {
        read = (in.readInt() != 0);
        starred = (in.readInt() != 0);
        sender = in.readString();
        priority = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(read ? 1 : 0);
        dest.writeInt(starred ? 1 : 0);
        dest.writeString(sender);
        dest.writeInt(priority);
    }

    public void set(boolean isRead, boolean isStarred, String senderString, int p) {
        read = isRead;
        starred = isStarred;
        sender = senderString;
        priority = p;
    }

    public boolean markRead(boolean isRead) {
        if (read != isRead) {
            read = isRead;
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(read, starred, sender);
    }

    public static final Creator<MessageInfo> CREATOR = new Creator<MessageInfo>() {

        @Override
        public MessageInfo createFromParcel(Parcel source) {
            return new MessageInfo(source);
        }

        @Override
        public MessageInfo[] newArray(int size) {
            return new MessageInfo[size];
        }

    };

}
