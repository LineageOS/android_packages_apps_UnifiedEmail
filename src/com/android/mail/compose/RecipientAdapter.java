/**
 * Copyright (c) 2007, Google Inc.
 * Copyright (c) 2015, The CyanogenMod Project
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
package com.android.mail.compose;

import com.android.emailcommon.provider.EmailContent;
import com.android.emailcommon.provider.SuggestedContact;
import com.android.ex.chips.BaseRecipientAdapter;
import com.android.ex.chips.RecipientEntry;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Account;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

public class RecipientAdapter extends BaseRecipientAdapter
implements OnSharedPreferenceChangeListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "RecipientAdapter";

    private static final int MAX_SUGGESTED_CONTACTS_ENTRIES = 3;

    private static final int MAX_DAYS_FOR_RECENTS_SUGGESTED_CONTACTS = -7;

    private String mSuggestedContactsMode;
    private long mAccountId;

    public RecipientAdapter(Context context, Account account) {
        super(context);
        setAccount(account.getAccountManagerAccount());
        mAccountId = -1;

        // Load the account id because we needed to access the suggested contacts data
        // (in async mode because will do i/o writes)
        loadAccountKey(account);

        mSuggestedContactsMode = MailPrefs.get(getContext()).getSuggestedContactMode();
        MailPrefs.get(context).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void finalize() throws Throwable {
        MailPrefs.get(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.finalize();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(MailPrefs.PreferenceKeys.SUGGESTED_CONTACTS_MODE)) {
            mSuggestedContactsMode = MailPrefs.get(getContext()).getSuggestedContactMode();
        }
    }

    @Override
    protected Set<SuggestionEntry> loadSuggestedEntries(CharSequence constraint, int maxResults) {
        Set<SuggestionEntry> entries = new HashSet<>();
        if (!mSuggestedContactsMode.equals(MailPrefs.SuggestedContactsMode.NONE)) {
            boolean recentsMode = mSuggestedContactsMode.equals(
                    MailPrefs.SuggestedContactsMode.RECENTS);

            String selection = SuggestedContact.ACCOUNT_KEY + " = ?" +
                    " and LOWER(" + SuggestedContact.DISPLAY_NAME + ") like LOWER(?) ESCAPE '\\' ";
            String[] args = new String[recentsMode ? 3 : 2];
            args[0] = String.valueOf(mAccountId);
            args[1] = "%" + constraint + "%";
            String sort = SuggestedContact.LAST_SEEN + " DESC LIMIT "
                    + Math.min(MAX_SUGGESTED_CONTACTS_ENTRIES, maxResults);
            if (recentsMode) {
                Calendar c = Calendar.getInstance(Locale.getDefault());
                c.add(Calendar.DAY_OF_YEAR, MAX_DAYS_FOR_RECENTS_SUGGESTED_CONTACTS);
                selection += SuggestedContact.LAST_SEEN + " >= ? ";
                args[2] = String.valueOf(c.getTimeInMillis());
            }

            Cursor cursor = getContext().getContentResolver().query(SuggestedContact.CONTENT_URI,
                    SuggestedContact.PROJECTION, selection, args, sort);
            try {
                if (cursor != null) {
                    Set<String> cachedAddresses = new HashSet<>();
                    Map<String, Integer> cachedContacts = new HashMap<>();
                    int contactsIds = -100;
                    while (cursor.moveToNext()) {
                        long suggestionId = cursor.getLong(
                                cursor.getColumnIndexOrThrow(SuggestedContact._ID));
                        String address = cursor.getString(
                                cursor.getColumnIndexOrThrow(SuggestedContact.ADDRESS));
                        String name = cursor.getString(
                                cursor.getColumnIndexOrThrow(SuggestedContact.NAME));
                        String displayName = cursor.getString(
                                cursor.getColumnIndexOrThrow(SuggestedContact.DISPLAY_NAME));

                        if (!cachedAddresses.contains(address)) {
                            int contactId = (cachedContacts.containsKey(name)) ?
                                    cachedContacts.get(name) : contactsIds++;
                            SuggestionEntry entry = new SuggestionEntry(
                                    suggestionId, displayName, address, name, contactId);
                            entries.add(entry);
                            cachedAddresses.add(address);

                            cachedContacts.put(name, contactId);
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Failed to perform search over suggested contacts table", e);

            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        if (DEBUG) {
            Log.i(TAG, "Found " + entries.size() + " entries in suggested contacts");
        }

        return entries;
    }

    protected void onAddSuggestion(final RecipientEntry entry, final SuggestionAddCallback cb) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... args) {
                return createSuggestedContact(entry);
            }
            @Override
            protected void onPostExecute(Boolean result) {
                if (result) {
                    cb.onSucess();
                } else {
                    cb.onFailed();
                }
            }
        }.execute();
    }

    protected void onDeleteSuggestion(final RecipientEntry entry,
            final SuggestionRemoveCallback cb) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... args) {
                return deleteSuggestedContact(entry);
            }
            @Override
            protected void onPostExecute(Boolean result) {
                if (result) {
                    cb.onSucess();
                } else {
                    cb.onFailed();
                }
            }
        }.execute();
    }

    private void loadAccountKey(final Account account) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Cursor c = getContext().getContentResolver().query(
                        com.android.emailcommon.provider.Account.CONTENT_URI,
                        EmailContent.ID_PROJECTION,
                        com.android.emailcommon.provider.Account.AccountColumns.EMAIL_ADDRESS
                            + " = ?",
                        new String[]{account.getAccountId()}, null);
                try {
                    if (c != null && c.moveToFirst()) {
                        mAccountId = c.getLong(0);
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                return null;
            }
        }.execute();
    }

    private boolean createSuggestedContact(RecipientEntry entry) {
        ContentResolver cr = getContext().getContentResolver();
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int rawContactInsertIndex = ops.size();

        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build());
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, entry.getDisplayName())
                .build());
        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.DATA, entry.getDestination())
                .withValue(Email.TYPE, Email.TYPE_OTHER)
                .build());

        try {
            cr.applyBatch(ContactsContract.AUTHORITY, ops);
            return true;

        } catch (RemoteException | OperationApplicationException e) {
            Log.e(TAG, "Failed to create the suggested contact.", e);
        }
        return false;
    }

    private boolean deleteSuggestedContact(RecipientEntry entry) {
        ContentResolver cr = getContext().getContentResolver();
        final Uri uri = ContentUris.withAppendedId(SuggestedContact.CONTENT_URI, entry.getDataId());
        return cr.delete(uri, null, null) == 1;
    }
}
