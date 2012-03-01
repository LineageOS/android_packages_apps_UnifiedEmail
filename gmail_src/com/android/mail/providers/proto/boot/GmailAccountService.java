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

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountCacheProvider;
import com.android.mail.providers.AccountCacheProvider.CachedAccount;
import com.android.mail.providers.protos.mock.MockUiProvider;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.AccountColumns;
import com.android.mail.utils.LogUtils;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import java.util.Map;


/**
 * A service to handle various intents asynchronously.
 */
public class GmailAccountService extends IntentService {

    private static final String GMAIL_UI_PROVIDER_AUTHORITY = "com.android.gmail.ui";
    private static final String GMAIL_UI_PROVIDER_BASE_URI_STRING =
            "content://" + GMAIL_UI_PROVIDER_AUTHORITY;

    private static final Uri BASE_SETTINGS_URI = Uri.parse("setting://gmail/");

    private static final Uri ACCOUNTS_URI =
            Uri.parse(GMAIL_UI_PROVIDER_BASE_URI_STRING + "/accounts");

    public static final String DEFAULT_HELP_URL =
            "http://www.google.com/support/mobile/?hl=%locale%";

    private final static String LOG_TAG = new LogUtils().getLogTag();

    private static GmailAccountListObserver sGmailAccountListObserver = null;

    public GmailAccountService() {
        super("GmailAccountService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String action = intent.getAction();
        if (AccountReceiver.ACTION_PROVIDER_CREATED.equals(action)) {
            // Register all Gmail accounts
            getAndRegisterGmailAccounts();
        }
    }

    private void getAndRegisterGmailAccounts() {
        // Get the list of Google accounts that have the mail feature, and register thiese with
        // the AccountCacheProvider

        final ContentResolver resolver = getContentResolver();
        // Get the accounts from Gmail
        // NOTE: Once Gmail & Unified Email are merged, this service should be removed
        AccountCacheProvider.addAccountsForUri(getContentResolver(), ACCOUNTS_URI);
        // This is a work around to make sure that changes in the underlying provider changes
        // that UnifiedEmail is updates with those change
        // TODO(pwestbro): remove this once AccountCacheProvider.addAccountsForUri registers for
        // changes
        if (sGmailAccountListObserver == null) {

            // The current thread may not be around after this IntentService stops, make
            // sure to handle notifications on the main thread
            final Handler notificationHandler = new Handler(Looper.getMainLooper());

            sGmailAccountListObserver =
                    new GmailAccountListObserver(this, notificationHandler);

            resolver.registerContentObserver(ACCOUNTS_URI, true,
                    sGmailAccountListObserver);
        }
    }

    static class GmailAccountListObserver extends ContentObserver {
        final Context mContext;
        GmailAccountListObserver(Context context, Handler handler) {
            super(handler);
            mContext = context;
        }

        @Override
        public void onChange(boolean selfChange) {
            LogUtils.d(LOG_TAG, "GmailAccountListObserver#onChange called.");
            // Send an intent to cause the account list to be requeried
            final Intent intent = new Intent(AccountReceiver.ACTION_PROVIDER_CREATED);
            mContext.sendBroadcast(intent);
        }
    }
}