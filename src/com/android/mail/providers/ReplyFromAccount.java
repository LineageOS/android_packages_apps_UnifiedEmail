/**
 * Copyright (c) 2012, Google Inc.
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

import android.net.Uri;

import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class ReplyFromAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String LOG_TAG = new LogUtils().getLogTag();
    private static final String BASE_ACCOUNT_URI = "baseAccountUri";
    private static final String ADDRESS_STRING = "address";
    private static final String REPLY_TO = "replyTo";
    private static final String NAME_STRING = "name";
    private static final String IS_DEFAULT = "isDefault";
    private static final String IS_CUSTOM_FROM = "isCustom";

    public Account account;
    Uri baseAccountUri;
    public String address;
    public String replyTo;
    public String name;
    public boolean isDefault;
    public boolean isCustomFrom;

    public ReplyFromAccount(Account account, Uri baseAccountUri, String address, String name,
            String replyTo, boolean isDefault, boolean isCustom) {
        this.account = account;
        this.baseAccountUri = baseAccountUri;
        this.address = address;
        this.name = name;
        this.replyTo = replyTo;
        this.isDefault = isDefault;
        this.isCustomFrom = isCustom;
    }

    public JSONObject serialize() {
        JSONObject json = new JSONObject();
        try {
            json.put(BASE_ACCOUNT_URI, baseAccountUri);
            json.put(ADDRESS_STRING, address);
            json.put(NAME_STRING, name);
            json.put(REPLY_TO, replyTo);
            json.put(IS_DEFAULT, isDefault);
            json.put(IS_CUSTOM_FROM, isCustomFrom);
        } catch (JSONException e) {
            LogUtils.wtf(LOG_TAG, e, "Could not serialize account with name " + name);
        }
        return json;
    }

    public static ReplyFromAccount deserialize(Account account, JSONObject json) {
        ReplyFromAccount replyFromAccount = null;
        try {
            Uri uri = Utils.getValidUri(json.getString(BASE_ACCOUNT_URI));
            String addressString = json.getString(ADDRESS_STRING);
            String nameString = json.getString(NAME_STRING);
            String replyTo = json.getString(REPLY_TO);
            boolean isDefault = json.getBoolean(IS_DEFAULT);
            boolean isCustomFrom = json.getBoolean(IS_CUSTOM_FROM);
            replyFromAccount = new ReplyFromAccount(account, uri, addressString, nameString,
                    replyTo, isDefault, isCustomFrom);
        } catch (JSONException e) {
            LogUtils.wtf(LOG_TAG, e, "Could not deserialize replyfromaccount");
        }
        return replyFromAccount;
    }

    public static ReplyFromAccount deserialize(Account account, String stringExtra) {
        ReplyFromAccount replyFromAccount = null;
        try {
            replyFromAccount =  deserialize(account, new JSONObject(stringExtra));
        } catch (JSONException e) {
            LogUtils.wtf(LOG_TAG, e, "Could not deserialize replyfromaccount");
        }
        return replyFromAccount;
    }
}
