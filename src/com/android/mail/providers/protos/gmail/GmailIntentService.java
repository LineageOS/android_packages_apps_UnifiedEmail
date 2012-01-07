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
package com.android.mail.providers.protos.gmail;

import com.android.mail.providers.AccountCacheProvider;
import com.android.mail.providers.protos.mock.MockUiProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.AccountColumns;
import com.android.mail.providers.UIProvider.AttachmentColumns;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.FolderColumns;
import com.android.mail.providers.UIProvider.MessageColumns;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;

import android.app.IntentService;
import android.content.Intent;

import java.io.IOException;
import java.util.Map;


/**
 * A service to handle various intents asynchronously.
 */
public class GmailIntentService extends IntentService {
    public GmailIntentService() {
        super("GmailIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String action = intent.getAction();
        if (DummyGmailProvider.ACTION_PROVIDER_CREATED.equals(action)) {
            // Register all Gmail accounts
            getAndRegisterGmailAccounts();
        }
    }

    private void getAndRegisterGmailAccounts() {
        // Get the list of Google accounts that have the mail feature, and register thiese with
        // the AccountCacheProvider
        AccountManagerFuture<Account[]> future;
        future = AccountManager.get(this).getAccountsByTypeAndFeatures(
                "com.google",
                // Ideally we would call GoogleLoginServiceConstants.featureForService("mail") here,
                // but we can't depend on Google code from this project.  Just use the resulting
                // value
                new String[] { "service_mail" },
                null, null);
        try {
            // Block this IntentService on the result because this thread may not be around later
            // to handle anything if it's killed in the interim.  This is a blockable non-UI thread.
            Account[] accounts = future.getResult();

            registerGmailAccounts(accounts);
        } catch (OperationCanceledException oce) {
            // should not happen.
        } catch (IOException ioe) {
            // should not happen
        } catch (AuthenticatorException ae) {
            // should not happen
        }
    }

    private void registerGmailAccounts(Account[] accounts) {
        for (int i = 0; i < accounts.length; i++) {
            final Account account = accounts[i];

            final int gmailAccountId = account.hashCode();

            // NOTE: This doesn't completely populate the provider.  A query for the account uri
            // will not return a cursor.
            final Map<String, Object> mockAccountMap =
                    MockUiProvider.createAccountDetailsMap(i % MockUiProvider.NUM_MOCK_ACCOUNTS);
            final AccountCacheProvider.CachedAccount cachedAccount =
                    new AccountCacheProvider.CachedAccount(gmailAccountId,
                            account.name,
                            MockUiProvider.getMockAccountUri(gmailAccountId),
                            (Long)mockAccountMap.get(AccountColumns.CAPABILITIES),
                            (String)mockAccountMap.get(AccountColumns.FOLDER_LIST_URI),
                            (String)mockAccountMap.get(AccountColumns.SEARCH_URI),
                            (String)mockAccountMap.get(AccountColumns.ACCOUNT_FROM_ADDRESSES_URI),
                            (String)mockAccountMap.get(AccountColumns.SAVE_NEW_DRAFT_URI),
                            (String)mockAccountMap.get(AccountColumns.SEND_MESSAGE_URI));

            AccountCacheProvider.addAccount(cachedAccount);
        }
    }
}