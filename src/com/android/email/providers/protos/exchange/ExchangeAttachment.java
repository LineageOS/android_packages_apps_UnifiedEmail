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
package com.android.email.providers.protos.exchange;

import com.android.email.providers.Attachment;

import java.io.Serializable;

public class ExchangeAttachment implements Serializable, Attachment {
    private static final long serialVersionUID = 1L;

    public String mFileName;
    public String mMimeType;
    public long mSize;
    public String mContentId;
    public String mContentUri;
    public long mMessageKey;
    public String mLocation;
    public String mEncoding;
    public String mContent; // Not currently used
    public int mFlags;
    public byte[] mContentBytes;
    public long mAccountKey;


    @Override
    public String getName() {
        return mFileName;
    }

    @Override
    public long getSize() {
        return mSize;
    }

    @Override
    public String getOriginExtras() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getContentType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object getOrigin() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getPartId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isSynced() {
        // TODO Auto-generated method stub
        return false;
    }
}
