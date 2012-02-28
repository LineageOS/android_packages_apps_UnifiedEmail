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

import com.android.mail.providers.AccountCacheProvider;
import com.android.mail.providers.protos.mock.MockUiProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.AccountColumns;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;

import java.io.IOException;
import java.util.Map;


/**
 * A service to handle various intents asynchronously.
 */
public class GmailAccountService extends IntentService {

    private static final String GMAIL_UI_PROVIDER_AUTHORITY = "com.android.gmail.ui";
    private static final String GMAIL_UI_PROVIDER_BASE_URI_STRING =
            "content://" + GMAIL_UI_PROVIDER_AUTHORITY;

    private static final Uri BASE_SETTINGS_URI = Uri.parse("setting://gmail/");

    public static final String DEFAULT_HELP_URL =
            "http://www.google.com/support/mobile/?hl=%locale%";


    private static Uri getAccountUri(String account) {
        return Uri.parse(GMAIL_UI_PROVIDER_BASE_URI_STRING + "/account/" + account);
    }

    private static Uri getAccountFoldersUri(String account) {
        return Uri.parse(GMAIL_UI_PROVIDER_BASE_URI_STRING + "/" + account + "/labels");
    }

    private static Uri getAccountSendMailUri(String account) {
        return Uri.parse(GMAIL_UI_PROVIDER_BASE_URI_STRING + "/" + account + "/sendNewMessage");
    }

    private static Uri getAccountUndoUri(String account) {
        return Uri.parse(GMAIL_UI_PROVIDER_BASE_URI_STRING + "/" + account + "/undo");
    }

    private static Uri getAccountSettingUri(String account) {
        return BASE_SETTINGS_URI.buildUpon().appendQueryParameter("account", account).build();
    }

    private static Uri getHelpUri() {
        // TODO(pwestbro): allow this url to be changed via Gservices
        return Uri.parse(DEFAULT_HELP_URL);
    }

    private static Uri getComposeUri() {
        // TODO(pwestbro): please add correct uri.
        return Uri.parse(DEFAULT_HELP_URL);
    }

    private static Uri getAccountSettingsQueryUri(String account) {
        return Uri.parse(GMAIL_UI_PROVIDER_BASE_URI_STRING + "/" + account + "/settings");
    }

    private static Uri getAccountSaveDraftUri(String account) {
        return Uri.parse(GMAIL_UI_PROVIDER_BASE_URI_STRING + "/" + account + "/saveNewMessage");
    }

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
                    MockUiProvider.createAccountDetailsMap(i % MockUiProvider.NUM_MOCK_ACCOUNTS,
                            false /* don't cache */);
            // TODO: where should this really be stored?
            long capabilities = Long.valueOf(
                    AccountCapabilities.SYNCABLE_FOLDERS |
                    AccountCapabilities.REPORT_SPAM |
                    AccountCapabilities.ARCHIVE |
                    AccountCapabilities.MUTE |
                    AccountCapabilities.SERVER_SEARCH |
                    AccountCapabilities.FOLDER_SERVER_SEARCH |
                    AccountCapabilities.SANITIZED_HTML |
                    AccountCapabilities.DRAFT_SYNCHRONIZATION |
                    AccountCapabilities.MULTIPLE_FROM_ADDRESSES |
                    AccountCapabilities.LOCAL_SEARCH |
                    AccountCapabilities.THREADED_CONVERSATIONS |
                    AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV |
                    AccountCapabilities.UNDO |
                    AccountCapabilities.HELP_CONTENT);
            final AccountCacheProvider.CachedAccount cachedAccount =
                    new AccountCacheProvider.CachedAccount(gmailAccountId,
                            account.name,
                            getAccountUri(account.name),
                            capabilities,
                            getAccountFoldersUri(account.name),
                            (Uri) mockAccountMap.get(AccountColumns.SEARCH_URI),
                            (Uri) mockAccountMap.get(AccountColumns.ACCOUNT_FROM_ADDRESSES_URI),
                            getAccountSaveDraftUri(account.name),
                            getAccountSendMailUri(account.name),
                            (Uri) mockAccountMap.get(AccountColumns.EXPUNGE_MESSAGE_URI),
                            getAccountUndoUri(account.name),
                            getAccountSettingUri(account.name),
                            getAccountSettingsQueryUri(account.name),
                            getHelpUri(),
                            0,
                            getComposeUri());

            AccountCacheProvider.addAccount(cachedAccount);
        }
    }
}