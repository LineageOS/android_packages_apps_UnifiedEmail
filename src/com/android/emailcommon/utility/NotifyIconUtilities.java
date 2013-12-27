/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.emailcommon.utility;

import android.content.Context;
import android.content.res.XmlResourceParser;

import com.android.mail.utils.LogUtils;

public class NotifyIconUtilities {
    private static final String LOG_TAG = "NotifyIconUtilities";

    private final static String ADDRESS_SEPARATOR = "@";
    /** Pattern to match any part of a domain */
    private final static String WILD_STRING = "*";
    /** Will match any, single character */
    private final static char WILD_CHARACTER = '?';
    private final static String DOMAIN_SEPARATOR = "\\.";

    public static int findNotifyIconForAccountDomain(Context context, int iconProviders,
            String address, int defaultIcon) {
        LogUtils.i(LOG_TAG, "find the notify icon for account: ", address);

        int notify_icon_res = defaultIcon;
        String domain = address.split(ADDRESS_SEPARATOR)[1];
        try {
            XmlResourceParser xml = context.getResources().getXml(iconProviders);
            int xmlEventType;
            boolean foundNotifyIcon = false;
            while ((xmlEventType = xml.next()) != XmlResourceParser.END_DOCUMENT) {
                if (xmlEventType == XmlResourceParser.START_TAG
                        && "provider".equals(xml.getName())) {
                    String providerDomain = xml.getAttributeValue(null, "domain");
                    try {
                        if (matchProvider(domain, providerDomain)) {
                            String notify_icon_res_name = xml.getAttributeValue(null, "icon");
                            int resId = context.getResources().
                                    getIdentifier(notify_icon_res_name, "drawable",
                                            context.getPackageName());
                            if (resId > 0) {
                                notify_icon_res = resId;
                            }
                            foundNotifyIcon = true;
                        }
                    } catch (IllegalArgumentException e) {
                        LogUtils.w(LOG_TAG, "providers line: " + xml.getLineNumber() +
                                "; Domain contains multiple globals");
                    }
                } else if (xmlEventType == XmlResourceParser.END_TAG
                        && "provider".equals(xml.getName())
                        && foundNotifyIcon) {
                    return notify_icon_res;
                }
            }
        } catch (Exception e) {
            LogUtils.e(LOG_TAG, "Error while trying to load provider settings.", e);
        }
        return notify_icon_res;
    }

    /**
     * Returns true if the string <code>s1</code> matches the string <code>s2</code>. The string
     * <code>s2</code> may contain any number of wildcards -- a '?' character -- and/or asterisk
     * characters -- '*'. Wildcards match any single character, while the asterisk matches a domain
     * part (i.e. substring demarcated by a period, '.')
     */
    private static boolean matchProvider(String testDomain, String providerDomain) {
        String[] testParts = testDomain.split(DOMAIN_SEPARATOR);
        String[] providerParts = providerDomain.split(DOMAIN_SEPARATOR);
        if (testParts.length != providerParts.length) {
            return false;
        }
        for (int i = 0; i < testParts.length; i++) {
            String testPart = testParts[i].toLowerCase();
            String providerPart = providerParts[i].toLowerCase();
            if (!providerPart.equals(WILD_STRING) &&
                    !matchWithWildcards(testPart, providerPart)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchWithWildcards(String testPart, String providerPart) {
        int providerLength = providerPart.length();
        if (testPart.length() != providerLength){
            return false;
        }
        for (int i = 0; i < providerLength; i++) {
            char testChar = testPart.charAt(i);
            char providerChar = providerPart.charAt(i);
            if (testChar != providerChar && providerChar != WILD_CHARACTER) {
                return false;
            }
        }
        return true;
    }
}
