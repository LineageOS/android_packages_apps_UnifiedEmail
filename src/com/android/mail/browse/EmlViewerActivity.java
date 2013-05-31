package com.android.mail.browse;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MimeType;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
        final ContentResolver resolver = getContentResolver();
        try {
            final InputStream stream = resolver.openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            StringBuilder builder = new StringBuilder(stream.available());
            String line = reader.readLine();
            while (line != null) {
                builder.append(line);
                line = reader.readLine();
            }
            mWebView.loadDataWithBaseURL("", builder.toString(), "text/html", "utf-8", null);
        } catch (FileNotFoundException e) {
            // TODO handle exceptions
        } catch (IOException e) {
            // TODO handle exception
        }
    }
}