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

package com.android.mail.providers;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.android.mail.providers.UIProvider.AccountCursorExtraKeys;
import com.android.mail.providers.protos.boot.AccountReceiver;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MatrixCursorWithExtra;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * The Mail App provider allows email providers to register "accounts" and the UI has a single
 * place to query for the list of accounts.
 *
 * During development this will allow new account types to be added, and allow them to be shown in
 * the application.  For example, the mock accounts can be enabled/disabled.
 * In the future, once other processes can add new accounts, this could allow other "mail"
 * applications have their content appear within the application
 */
public abstract class MailAppProvider extends ContentProvider
        implements OnLoadCompleteListener<Cursor>{

    private static final String SHARED_PREFERENCES_NAME = "MailAppProvider";
    private static final String ACCOUNT_LIST_KEY = "accountList";
    private static final String LAST_VIEWED_ACCOUNT_KEY = "lastViewedAccount";
    private static final String LAST_SENT_FROM_ACCOUNT_KEY = "lastSendFromAccount";

    /**
     * Extra used in the result from the activity launched by the intent specified
     * by {@link #getNoAccountsIntent} to return the list of accounts.  The data
     * specified by this extra key should be a ParcelableArray.
     */
    public static final String ADD_ACCOUNT_RESULT_ACCOUNTS_EXTRA = "addAccountResultAccounts";

    private final static String LOG_TAG = LogTag.getLogTag();

    private final Map<Uri, AccountCacheEntry> mAccountCache = Maps.newHashMap();

    private final Map<Uri, CursorLoader> mCursorLoaderMap = Maps.newHashMap();

    private ContentResolver mResolver;
    private static String sAuthority;
    private static MailAppProvider sInstance;

    private volatile boolean mAccountsFullyLoaded = false;

    private SharedPreferences mSharedPrefs;

    /**
     * Allows the implementing provider to specify the authority for this provider. Email and Gmail
     * must specify different authorities.
     */
    protected abstract String getAuthority();

    /**
     * Authority for the suggestions provider. Email and Gmail must specify different authorities,
     * much like the implementation of {@link #getAuthority()}.
     * @return the suggestion authority associated with this provider.
     */
    public abstract String getSuggestionAuthority();

    /**
     * Allows the implementing provider to specify an intent that should be used in a call to
     * {@link Context#startActivityForResult(android.content.Intent)} when the account provider
     * doesn't return any accounts.
     *
     * The result from the {@link Activity} activity should include the list of accounts in
     * the returned intent, in the

     * @return Intent or null, if the provider doesn't specify a behavior when no accounts are
     * specified.
     */
    protected abstract Intent getNoAccountsIntent(Context context);

    /**
     * The cursor returned from a call to {@link android.content.ContentResolver#query()} with this
     * uri will return a cursor that with columns that are a subset of the columns specified
     * in {@link UIProvider.ConversationColumns}
     * The cursor returned by this query can return a {@link android.os.Bundle}
     * from a call to {@link android.database.Cursor#getExtras()}.  This Bundle may have
     * values with keys listed in {@link AccountCursorExtraKeys}
     */
    public static Uri getAccountsUri() {
        return Uri.parse("content://" + sAuthority + "/");
    }

    public static MailAppProvider getInstance() {
        return sInstance;
    }

    /** Default constructor */
    protected MailAppProvider() {
        sInstance = this;
    }

    @Override
    public boolean onCreate() {
        sAuthority = getAuthority();
        mResolver = getContext().getContentResolver();

        final Intent intent = new Intent(AccountReceiver.ACTION_PROVIDER_CREATED);
        getContext().sendBroadcast(intent);

        // Load the previously saved account list
        loadCachedAccountList();

        return true;
    }

    @Override
    public void shutdown() {
        sInstance = null;

        for (CursorLoader loader : mCursorLoaderMap.values()) {
            loader.stopLoading();
        }
        mCursorLoaderMap.clear();
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // This content provider currently only supports one query (to return the list of accounts).
        // No reason to check the uri.  Currently only checking the projections

        // Validates and returns the projection that should be used.
        final String[] resultProjection = UIProviderValidator.validateAccountProjection(projection);
        final Bundle extras = new Bundle();
        extras.putInt(AccountCursorExtraKeys.ACCOUNTS_LOADED, mAccountsFullyLoaded ? 1 : 0);

        // Make a copy of the account cache
        final List<AccountCacheEntry> accountList = Lists.newArrayList();
        synchronized (mAccountCache) {
            accountList.addAll(mAccountCache.values());
        }
        Collections.sort(accountList);

        final MatrixCursor cursor =
                new MatrixCursorWithExtra(resultProjection, accountList.size(), extras);

        for (AccountCacheEntry accountEntry : accountList) {
            final Account account = accountEntry.mAccount;
            final MatrixCursor.RowBuilder builder = cursor.newRow();

            for (String column : resultProjection) {
                if (TextUtils.equals(column, BaseColumns._ID)) {
                    // TODO(pwestbro): remove this as it isn't used.
                    builder.add(Integer.valueOf(0));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.NAME)) {
                    builder.add(account.name);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.PROVIDER_VERSION)) {
                    // TODO fix this
                    builder.add(Integer.valueOf(account.providerVersion));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.URI)) {
                    builder.add(account.uri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.CAPABILITIES)) {
                    builder.add(Integer.valueOf(account.capabilities));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.FOLDER_LIST_URI)) {
                    builder.add(account.folderListUri);
                } else if (TextUtils
                        .equals(column, UIProvider.AccountColumns.FULL_FOLDER_LIST_URI)) {
                    builder.add(account.fullFolderListUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SEARCH_URI)) {
                    builder.add(account.searchUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.ACCOUNT_FROM_ADDRESSES)) {
                    builder.add(account.accountFromAddresses);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SAVE_DRAFT_URI)) {
                    builder.add(account.saveDraftUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SEND_MAIL_URI)) {
                    builder.add(account.sendMessageUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.EXPUNGE_MESSAGE_URI)) {
                    builder.add(account.expungeMessageUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.UNDO_URI)) {
                    builder.add(account.undoUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SETTINGS_INTENT_URI)) {
                    builder.add(account.settingsIntentUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.HELP_INTENT_URI)) {
                    builder.add(account.helpIntentUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SEND_FEEDBACK_INTENT_URI)) {
                    builder.add(account.sendFeedbackIntentUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.REAUTHENTICATION_INTENT_URI)) {
                    builder.add(account.reauthenticationIntentUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.SYNC_STATUS)) {
                    builder.add(Integer.valueOf(account.syncStatus));
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.COMPOSE_URI)) {
                    builder.add(account.composeIntentUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.MIME_TYPE)) {
                    builder.add(account.mimeType);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.RECENT_FOLDER_LIST_URI)) {
                    builder.add(account.recentFolderListUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.DEFAULT_RECENT_FOLDER_LIST_URI)) {
                    builder.add(account.defaultRecentFolderListUri);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.MANUAL_SYNC_URI)) {
                    builder.add(account.manualSyncUri);
                } else if (TextUtils.equals(column, UIProvider.AccountColumns.COLOR)) {
                    builder.add(account.color);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.SIGNATURE)) {
                    builder.add(account.settings.signature);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.AUTO_ADVANCE)) {
                    builder.add(Integer.valueOf(account.settings.autoAdvance));
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.MESSAGE_TEXT_SIZE)) {
                    builder.add(Integer.valueOf(account.settings.messageTextSize));
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.REPLY_BEHAVIOR)) {
                    builder.add(Integer.valueOf(account.settings.replyBehavior));
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.HIDE_CHECKBOXES)) {
                    builder.add(Integer.valueOf(account.settings.hideCheckboxes ? 1 : 0));
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.CONFIRM_DELETE)) {
                    builder.add(Integer.valueOf(account.settings.confirmDelete ? 1 : 0));
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.CONFIRM_ARCHIVE)) {
                    builder.add(Integer.valueOf(account.settings.confirmArchive ? 1 : 0));
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.CONFIRM_SEND)) {
                    builder.add(Integer.valueOf(account.settings.confirmSend ? 1 : 0));
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.DEFAULT_INBOX)) {
                    builder.add(account.settings.defaultInbox);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.DEFAULT_INBOX_NAME)) {
                    builder.add(account.settings.defaultInboxName);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.SNAP_HEADERS)) {
                    builder.add(Integer.valueOf(account.settings.snapHeaders));
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.FORCE_REPLY_FROM_DEFAULT)) {
                    builder.add(Integer.valueOf(account.settings.forceReplyFromDefault ? 1 : 0));
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.MAX_ATTACHMENT_SIZE)) {
                    builder.add(account.settings.maxAttachmentSize);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.SWIPE)) {
                    builder.add(account.settings.swipe);
                } else if (TextUtils.equals(column,
                        UIProvider.AccountColumns.SettingsColumns.PRIORITY_ARROWS_ENABLED)) {
                    builder.add(Integer.valueOf(account.settings.priorityArrowsEnabled ? 1 : 0));
                } else {
                    throw new IllegalStateException("Column not found: " + column);
                }
            }
        }

        cursor.setNotificationUri(mResolver, getAccountsUri());
        return cursor;
    }

    @Override
    public Uri insert(Uri url, ContentValues values) {
        return url;
    }

    @Override
    public int update(Uri url, ContentValues values, String selection,
            String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri url, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    /**
     * Asynchronously adds all of the accounts that are specified by the result set returned by
     * {@link ContentProvider#query()} for the specified uri.  The content provider handling the
     * query needs to handle the {@link UIProvider.ACCOUNTS_PROJECTION}
     * Any changes to the underlying provider will automatically be reflected.
     * @param resolver
     * @param accountsQueryUri
     */
    public static void addAccountsForUriAsync(Uri accountsQueryUri) {
        getInstance().startAccountsLoader(accountsQueryUri);
    }

    /**
     * Returns the intent that should be used in a call to
     * {@link Context#startActivity(android.content.Intent)} when the account provider doesn't
     * return any accounts
     * @return Intent or null, if the provider doesn't specify a behavior when no acccounts are
     * specified.
     */
    public static Intent getNoAccountIntent(Context context) {
        return getInstance().getNoAccountsIntent(context);
    }

    private synchronized void startAccountsLoader(Uri accountsQueryUri) {
        final CursorLoader accountsCursorLoader = new CursorLoader(getContext(), accountsQueryUri,
                UIProvider.ACCOUNTS_PROJECTION, null, null, null);

        // Listen for the results
        accountsCursorLoader.registerListener(accountsQueryUri.hashCode(), this);
        accountsCursorLoader.startLoading();

        // If there is a previous loader for the given uri, stop it
        final CursorLoader oldLoader = mCursorLoaderMap.get(accountsQueryUri);
        if (oldLoader != null) {
            oldLoader.stopLoading();
        }
        mCursorLoaderMap.put(accountsQueryUri, accountsCursorLoader);
    }

    public static void addAccount(Account account, Uri accountsQueryUri) {
        final MailAppProvider provider = getInstance();
        if (provider == null) {
            throw new IllegalStateException("MailAppProvider not intialized");
        }
        provider.addAccountImpl(account, accountsQueryUri, true /* notify */);
    }

    private void addAccountImpl(Account account, Uri accountsQueryUri, boolean notify) {
        addAccountImpl(account, accountsQueryUri, mAccountCache.size(), notify);
    }

    private void addAccountImpl(Account account, Uri accountsQueryUri, int position,
            boolean notify) {
        synchronized (mAccountCache) {
            if (account != null) {
                LogUtils.v(LOG_TAG, "adding account %s", account);
                mAccountCache.put(account.uri,
                        new AccountCacheEntry(account, accountsQueryUri, position));
            }
        }
        // Explicitly calling this out of the synchronized block in case any of the observers get
        // called synchronously.
        if (notify) {
            broadcastAccountChange();
        }

        // Cache the updated account list
        cacheAccountList();
    }

    private void removeAccounts(Set<Uri> uris, boolean notify) {
        synchronized (mAccountCache) {
            for (Uri accountUri : uris) {
                LogUtils.d(LOG_TAG, "Removing account %s", accountUri);
                mAccountCache.remove(accountUri);
            }
        }
        // Explicitly calling this out of the synchronized block in case any of the observers get
        // called synchronously.
        if (notify) {
            broadcastAccountChange();
        }

        // Cache the updated account list
        cacheAccountList();
    }

    private static void broadcastAccountChange() {
        final MailAppProvider provider = sInstance;

        if (provider != null) {
            provider.mResolver.notifyChange(getAccountsUri(), null);
        }
    }

    /**
     * Returns the {@link Account#uri} (in String form) of the last viewed account.
     */
    public String getLastViewedAccount() {
        return getPreferences().getString(LAST_VIEWED_ACCOUNT_KEY, null);
    }

    /**
     * Persists the {@link Account#uri} (in String form) of the last viewed account.
     */
    public void setLastViewedAccount(String accountUriStr) {
        final SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(LAST_VIEWED_ACCOUNT_KEY, accountUriStr);
        editor.apply();
    }

    /**
     * Returns the {@link Account#uri} (in String form) of the last account the
     * user compose a message from.
     */
    public String getLastSentFromAccount() {
        return getPreferences().getString(LAST_SENT_FROM_ACCOUNT_KEY, null);
    }

    /**
     * Persists the {@link Account#uri} (in String form) of the last account the
     * user compose a message from.
     */
    public void setLastSentFromAccount(String accountUriStr) {
        final SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(LAST_SENT_FROM_ACCOUNT_KEY, accountUriStr);
        editor.apply();
    }

    private void loadCachedAccountList() {
        final SharedPreferences preference = getPreferences();

        final Set<String> accountsStringSet = preference.getStringSet(ACCOUNT_LIST_KEY, null);

        if (accountsStringSet != null) {
            int pos = 0;
            for (String serializedAccount : accountsStringSet) {
                try {
                    // TODO (pwestbro): we are creating duplicate AccountCacheEntry objects.
                    // One here, and one in addAccountImpl.  We should stop doing that.
                    final AccountCacheEntry accountEntry =
                            new AccountCacheEntry(serializedAccount, pos);
                    if (accountEntry.mAccount.settings != null) {
                        addAccountImpl(accountEntry.mAccount, accountEntry.mAccountsQueryUri, pos,
                                false /* don't notify */);
                    } else {
                        LogUtils.e(LOG_TAG, "Dropping account that doesn't specify settings");
                    }
                    pos++;
                } catch (Exception e) {
                    // Unable to create account object, skip to next
                    LogUtils.e(LOG_TAG, e,
                            "Unable to create account object from serialized string '%s'",
                            serializedAccount);
                }
            }
            broadcastAccountChange();
        }
    }

    private void cacheAccountList() {
        final List<AccountCacheEntry> accountList = Lists.newArrayList();
        synchronized (mAccountCache) {
            accountList.addAll(mAccountCache.values());
        }
        Collections.sort(accountList);

        final Set<String> serializedAccounts = Sets.newHashSet();
        for (AccountCacheEntry accountEntry : accountList) {
            serializedAccounts.add(accountEntry.serialize());
        }

        final SharedPreferences.Editor editor = getPreferences().edit();
        editor.putStringSet(ACCOUNT_LIST_KEY, serializedAccounts);
        editor.apply();
    }

    private SharedPreferences getPreferences() {
        if (mSharedPrefs == null) {
            mSharedPrefs = getContext().getSharedPreferences(
                    SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        }
        return mSharedPrefs;
    }

    static public Account getAccountFromAccountUri(Uri accountUri) {
        MailAppProvider provider = getInstance();
        if (provider != null && provider.mAccountsFullyLoaded) {
            synchronized(provider.mAccountCache) {
                AccountCacheEntry entry = provider.mAccountCache.get(accountUri);
                if (entry != null) {
                    return entry.mAccount;
                }
            }
        }
        return null;
    }

    @Override
    public void onLoadComplete(Loader<Cursor> loader, Cursor data) {
        if (data == null) {
            LogUtils.d(LOG_TAG, "null account cursor returned");
            return;
        }

        LogUtils.d(LOG_TAG, "Cursor with %d accounts returned", data.getCount());
        final CursorLoader cursorLoader = (CursorLoader)loader;
        final Uri accountsQueryUri = cursorLoader.getUri();

        final Set<AccountCacheEntry> accountList;
        synchronized (mAccountCache) {
            accountList = ImmutableSet.copyOf(mAccountCache.values());
        }

        int lastPosition = 0;
        // Build a set of the account uris that had been associated with that query
        final Set<Uri> previousQueryUriSet = Sets.newHashSet();
        for (AccountCacheEntry entry : accountList) {
            if (accountsQueryUri.equals(entry.mAccountsQueryUri)) {
                previousQueryUriSet.add(entry.mAccount.uri);
            }
            if (entry.mPosition > lastPosition) {
                lastPosition = entry.mPosition;
            }
        }

        // Update the internal state of this provider if the returned result set
        // represents all accounts
        // TODO: determine what should happen with a heterogeneous set of accounts
        final Bundle extra = data.getExtras();
        mAccountsFullyLoaded = extra.getInt(AccountCursorExtraKeys.ACCOUNTS_LOADED) != 0;

        final Set<Uri> newQueryUriMap = Sets.newHashSet();

        // We are relying on the fact that all accounts are added in the order specified in the
        // cursor.  Initially assume that we insert these items to at the end of the list
        int pos = lastPosition;
        while (data.moveToNext()) {
            final Account account = new Account(data);
            final Uri accountUri = account.uri;
            newQueryUriMap.add(accountUri);
            addAccountImpl(account, accountsQueryUri, pos++, false /* don't notify */);
        }
        // Remove all of the accounts that are in the new result set
        previousQueryUriSet.removeAll(newQueryUriMap);

        // For all of the entries that had been in the previous result set, and are not
        // in the new result set, remove them from the cache
        if (previousQueryUriSet.size() > 0 && mAccountsFullyLoaded) {
            removeAccounts(previousQueryUriSet, false /* don't notify */);
        }
        broadcastAccountChange();
    }

    /**
     * Object that allows the Account Cache provider to associate the account with the content
     * provider uri that originated that account.
     */
    private static class AccountCacheEntry implements Comparable<AccountCacheEntry> {
        final Account mAccount;
        final Uri mAccountsQueryUri;
        final int mPosition;

        private static final String ACCOUNT_ENTRY_COMPONENT_SEPARATOR = "^**^";
        private static final Pattern ACCOUNT_ENTRY_COMPONENT_SEPARATOR_PATTERN =
                Pattern.compile("\\^\\*\\*\\^");

        private static final int NUMBER_MEMBERS = 2;

        public AccountCacheEntry(Account account, Uri accountQueryUri, int position) {
            mAccount = account;
            mAccountsQueryUri = accountQueryUri;
            mPosition = position;
        }

        /**
         * Return a serialized String for this AccountCacheEntry.
         */
        public synchronized String serialize() {
            StringBuilder out = new StringBuilder();
            out.append(mAccount.serialize()).append(ACCOUNT_ENTRY_COMPONENT_SEPARATOR);
            final String accountQueryUri =
                    mAccountsQueryUri != null ? mAccountsQueryUri.toString() : "";
            out.append(accountQueryUri);
            return out.toString();
        }

        /**
         * Create an account cache object from a serialized string previously stored away.
         * If the serializedString does not parse as a valid account, we throw an
         * {@link IllegalArgumentException}. The caller is responsible for checking this and
         * ignoring the newly created object if the exception is thrown.
         * @param serializedString
         */
        public AccountCacheEntry(String serializedString, int position)
                throws IllegalArgumentException {
            String[] cacheEntryMembers = TextUtils.split(serializedString,
                    ACCOUNT_ENTRY_COMPONENT_SEPARATOR_PATTERN);
            if (cacheEntryMembers.length != NUMBER_MEMBERS) {
                throw new IllegalArgumentException("AccountCacheEntry de-serializing failed. "
                        + "Wrong number of members detected. "
                        + cacheEntryMembers.length + " detected");
            }
            mAccount = Account.newinstance(cacheEntryMembers[0]);
            if (mAccount == null) {
                throw new IllegalArgumentException("AccountCacheEntry de-serializing failed. "
                        + "Account object could not be created from the serialized string: "
                        + serializedString);
            }
            if (mAccount.settings == Settings.EMPTY_SETTINGS) {
                throw new IllegalArgumentException("AccountCacheEntry de-serializing failed. "
                        + "Settings could not be created from the string: " + serializedString);
            }
            mAccountsQueryUri = !TextUtils.isEmpty(cacheEntryMembers[1]) ?
                    Uri.parse(cacheEntryMembers[1]) : null;
            mPosition = position;
        }

        @Override
        public int compareTo(AccountCacheEntry o) {
            return o.mPosition - mPosition;
        }
    }
}
