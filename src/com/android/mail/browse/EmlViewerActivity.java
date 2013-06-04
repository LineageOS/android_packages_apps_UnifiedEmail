/*
 * Copyright (C) 2013 Google Inc.
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

package com.android.mail.browse;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;

import com.android.emailcommon.TempDirectory;
import com.android.emailcommon.internet.MimeMessage;
import com.android.emailcommon.mail.MessagingException;
import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MimeType;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static com.android.mail.browse.MessageCursor.ConversationMessage;

public class EmlViewerActivity extends Activity {
    private static final String LOG_TAG = LogTag.getLogTag();

    private WebView mWebView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.eml_viewer_activity);
        mWebView = (WebView) findViewById(R.id.eml_web_view);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        final String type = intent.getType();

        if (Intent.ACTION_VIEW.equals(action) &&
                MimeType.EML_ATTACHMENT_CONTENT_TYPE.equals(type)) {
            openEmlFile(intent.getData());
        } else {
            LogUtils.wtf(LOG_TAG,
                    "Entered EmlViewerActivity with wrong intent action or type: %s, %s",
                    action, type);
            finish(); // we should not be here. bail out. bail out.
        }
    }

    private void openEmlFile(Uri uri) {
        TempDirectory.setTempDirectory(this);
        final ContentResolver resolver = getContentResolver();
        final InputStream stream;
        try {
            stream = resolver.openInputStream(uri);
        } catch (FileNotFoundException e) {
            // TODO handle exception
            return;
        }

        final MimeMessage mimeMessage;
        final ConversationMessage convMessage;
        try {
            mimeMessage = new MimeMessage(stream);
            convMessage = new ConversationMessage(mimeMessage);
        } catch (IOException e) {
            // TODO handle exception
            return;
        } catch (MessagingException e) {
            // TODO handle exception
            return;
        }

        mWebView.loadDataWithBaseURL("", convMessage.getBodyAsHtml(), "text/html", "utf-8", null);
    }
}
