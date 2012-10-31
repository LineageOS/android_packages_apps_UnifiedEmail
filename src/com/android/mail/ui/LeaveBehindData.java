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
package com.android.mail.ui;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

import com.android.mail.providers.Conversation;

public class LeaveBehindData implements Parcelable {
    ToastBarOperation op;
    Conversation data;

    public LeaveBehindData(Conversation conv, ToastBarOperation undoOp) {
        op = undoOp;
        data = conv;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel arg, int flags) {
        arg.writeParcelable(op, 0);
        arg.writeParcelable(data, 0);
    }

    private LeaveBehindData(Parcel arg) {
        this((Conversation) arg.readParcelable(null),
                (ToastBarOperation) arg.readParcelable(null));
    }

    public static final Creator<LeaveBehindData> CREATOR = new Creator<LeaveBehindData>() {

        @Override
        public LeaveBehindData createFromParcel(Parcel source) {
            return new LeaveBehindData(source);
        }

        @Override
        public LeaveBehindData[] newArray(int size) {
            return new LeaveBehindData[size];
        }

    };
}