/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui;

import android.app.Activity;
import android.content.Context;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.StrictMode;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * <p>
 * A complete Mail activity instance. This is the toplevel class that creates the views and handles
 * the activity lifecycle.</p>
 *
 * <p>This class is abstract to allow many other activities to be quickly created by subclassing
 * this activity and overriding a small subset of the life cycle methods: for example
 * ComposeActivity and CreateShortcutActivity.</p>
 *
 * <p>In the Gmail codebase, this was called GmailBaseActivity</p>
 *
 */
public abstract class AbstractMailActivity extends Activity
        implements HelpCallback, RestrictedActivity {

    private NfcAdapter mNfcAdapter; // final after onCreate
    private NdefMessage mForegroundNdef;
    private static AbstractMailActivity sForegroundInstance;
    private final UiHandler mUiHandler = new UiHandler();

    private static final boolean STRICT_MODE = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectAll()   // or .detectAll() for all detectable problems
            .penaltyLog()
            .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .penaltyLog()
            .penaltyDeath()
            .build());
        }

        super.onCreate(savedInstanceState);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter != null) {
            // Find custom "from" address asynchronously.
            // TODO(viki): Get a real account
            String realAccount = "test@android.com";
        }
        mUiHandler.setEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mUiHandler.setEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mUiHandler.setEnabled(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (this) {
            if (mNfcAdapter != null && mForegroundNdef != null) {
                mNfcAdapter.disableForegroundNdefPush(this);
            }
            sForegroundInstance = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        synchronized (this) {
            sForegroundInstance = this;
            if (mNfcAdapter != null && mForegroundNdef != null) {
                mNfcAdapter.enableForegroundNdefPush(this, mForegroundNdef);
            }
        }

        mUiHandler.setEnabled(true);
    }

    /**
     * Sets an NDEF message to be shared with "zero-clicks" using NFC. The message
     * will be available as long as the current activity is in the foreground.
     */
    static void setForegroundNdef(NdefMessage ndef) {
        AbstractMailActivity foreground = sForegroundInstance;
        if (foreground != null && foreground.mNfcAdapter != null) {
            synchronized (foreground) {
                foreground.mForegroundNdef = ndef;
                if (sForegroundInstance != null) {
                    if (ndef != null) {
                        sForegroundInstance.mNfcAdapter.enableForegroundNdefPush(
                                sForegroundInstance, ndef);
                    } else {
                        sForegroundInstance.mNfcAdapter.disableForegroundNdefPush(
                                sForegroundInstance);
                    }
                }
            }
        }
    }

    /**
     * Get the contextual help parameter for this activity. This can be overridden
     * to allow the extending activities to return different help context strings.
     * The default implementation is to return "gm".
     * @return The help context of this activity.
     */
    @Override
    public String getHelpContext() {
        return "Mail";
    }

    /**
     * Returns an NDEF message with a single mailto URI record
     * for the given email address.
     */
    static NdefMessage getMailtoNdef(String account) {
        byte[] accountBytes;
        try {
            accountBytes = URLEncoder.encode(account, "UTF-8").getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            accountBytes = account.getBytes();
        }
        byte prefix = 0x06; // mailto:
        byte[] recordBytes = new byte[accountBytes.length + 1];
        recordBytes[0] = prefix;
        System.arraycopy(accountBytes, 0, recordBytes, 1, accountBytes.length);
        NdefRecord mailto = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_URI,
                new byte[0], recordBytes);
        return new NdefMessage(new NdefRecord[] { mailto });
    }

    @Override
    public Context getActivityContext() {
        return this;
    }
}
