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

import com.android.emailcommon.provider.Account;
import com.android.emailcommon.provider.EmailContent;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.AccountColumns;
import com.android.mail.providers.protos.mock.MockUiProvider;
import com.android.mail.providers.AccountCacheProvider;

import java.util.Map;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;

/**
 * A service to handle various intents asynchronously.
 */
public class EmailAccountService extends IntentService {
    private static final long BASE_EAS_CAPABILITIES =
        AccountCapabilities.SYNCABLE_FOLDERS |
        AccountCapabilities.FOLDER_SERVER_SEARCH |
        AccountCapabilities.SANITIZED_HTML |
        AccountCapabilities.SMART_REPLY |
        AccountCapabilities.SERVER_SEARCH;

    private static String getUriString(String type, String accountName) {
        return EmailContent.CONTENT_URI.toString() + "/" + type + "/" + accountName;
    }

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
        Cursor c = getContentResolver().query(Account.CONTENT_URI, Account.CONTENT_PROJECTION, null,
                null, null);
        if (c == null) return;
        try {
            int i = 0;
            while (c.moveToNext()) {
                final Map<String, Object> mockAccountMap =
                    MockUiProvider.createAccountDetailsMap(i % MockUiProvider.NUM_MOCK_ACCOUNTS);
                // Send our account information to the cache provider
                String accountName = c.getString(Account.CONTENT_EMAIL_ADDRESS_COLUMN);
                final AccountCacheProvider.CachedAccount cachedAccount =
                    new AccountCacheProvider.CachedAccount(
                            c.getLong(Account.CONTENT_ID_COLUMN),
                            accountName,
                            getUriString("uiaccount", accountName),
                            // TODO: Check actual account protocol and return proper values
                            BASE_EAS_CAPABILITIES,
                            getUriString("uifolders", accountName),
                            (String)mockAccountMap.get(AccountColumns.SEARCH_URI),
                            (String)mockAccountMap.get(AccountColumns.ACCOUNT_FROM_ADDRESSES_URI),
                            (String)mockAccountMap.get(AccountColumns.SAVE_DRAFT_URI),
                            getUriString("uisendmail", accountName),
                            (String)mockAccountMap.get(AccountColumns.EXPUNGE_MESSAGE_URI));

                AccountCacheProvider.addAccount(cachedAccount);
                i++;
            }
        } finally {
            c.close();
        }
    }
}