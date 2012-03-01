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

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import com.android.mail.providers.Account;
import com.android.mail.providers.AccountCacheProvider;
import com.android.mail.providers.AccountCacheProvider.CachedAccount;
import com.android.mail.providers.UIProvider;

/**
 * A service to handle various intents asynchronously.
 */
public class EmailAccountService extends IntentService {

    private static final Uri ACCOUNTS_URI =
            Uri.parse("content://com.android.email.provider/uiaccts");

    public EmailAccountService() {
        super("EmailAccountService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String action = intent.getAction();
        if (AccountReceiver.ACTION_PROVIDER_CREATED.equals(action)) {
            // Register all Email accounts
            getAndRegisterEmailAccounts();
        }
    }

    private void getAndRegisterEmailAccounts() {
        // Use EmailProvider to get our accounts
        AccountCacheProvider.addAccountsForUri(getContentResolver(), ACCOUNTS_URI);
    }
}