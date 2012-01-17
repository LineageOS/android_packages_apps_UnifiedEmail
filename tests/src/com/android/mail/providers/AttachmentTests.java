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

import android.test.AndroidTestCase;

import com.android.mail.utils.AttachmentUtils;

import java.util.ArrayList;

public class AttachmentTests extends AndroidTestCase {

    public void testSerializeDeSerialize() {
        Message message = new Message();
        ArrayList<Attachment> attachments = new ArrayList<Attachment>();
        Attachment attachment;
        for (int i = 0; i < 5; i++) {
            attachment = new Attachment();
            attachment.name = "name" + i;
            attachment.contentUri = "content://" + i;
            attachment.mimeType = "mimeType" + i;
            attachment.size = i;
            attachment.partId = i + "";
            attachment.originExtras = "extras" + i;
            attachments.add(attachment);
        }

        message.joinedAttachmentInfos = MessageModification.joinedAttachmentsString(attachments);
        ArrayList<Attachment> reformed = AttachmentUtils.getAttachmentsFromMessage(message);
        assertEquals(reformed.size(), 5);
        for (int i = 0; i < 5; i++) {
            assertEquals(reformed.get(i).name, "name" + i);
            assertEquals(reformed.get(i).contentUri, "content://" + i);
            assertEquals(reformed.get(i).mimeType, "mimeType" + i);
            assertEquals(reformed.get(i).size, i);
            assertEquals(reformed.get(i).partId, i + "");
            assertEquals(reformed.get(i).originExtras, "extras" + i);
        }
    }
}