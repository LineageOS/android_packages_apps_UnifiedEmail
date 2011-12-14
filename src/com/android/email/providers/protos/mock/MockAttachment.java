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
package com.android.email.providers.protos.mock;

import com.android.email.providers.protos.Attachment;

import java.io.Serializable;



public class MockAttachment implements Serializable, Attachment {

    private static final long serialVersionUID = 1L;

    /** Identifies the attachment uniquely when combined wih a message id.*/
    public String partId;

    /** The intended filename of the attachment.*/
    public String name;

    /** The native content type.*/
    public String contentType;

    /** The size of the attachment in its native form.*/
    public int size;

    /**
     * The content type of the simple version of the attachment. Blank if no simple version is
     * available.
     */
    public String simpleContentType;

    public String originExtras;

    public String cachedContent;


    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public String getOriginExtras() {
        return originExtras;
    }

    @Override
    public String getContentType() {
        return contentType;
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
        return true;
    }
}