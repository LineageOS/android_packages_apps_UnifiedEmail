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
package com.android.mail.providers.protos.boot;

import com.android.mail.providers.MailAppProvider;
import com.android.mail.providers.protos.mock.MockUiProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class AccountReceiver extends BroadcastReceiver {

    private static final Uri GMAIL_ACCOUNTS_URI =
            Uri.parse("content://com.android.gmail.ui/accounts");

    private static final Uri EMAIL_ACCOUNTS_URI =
            Uri.parse("content://com.android.email.provider/uiaccts");

    /**
     * Intent used to notify interested parties that the Mail provider has been created.
     */
    public static final String ACTION_PROVIDER_CREATED
            = "com.android.mail.providers.protos.boot.intent.ACTION_PROVIDER_CREATED";

    @Override
    public void onReceive(Context context, Intent intent) {
        MockUiProvider.initializeMockProvider();

        MailAppProvider.addAccountsForUriAsync(GMAIL_ACCOUNTS_URI);
        MailAppProvider.addAccountsForUriAsync(EMAIL_ACCOUNTS_URI);
    }
}
