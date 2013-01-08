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

package com.android.mail.utils;

import android.content.res.Resources;

import com.android.mail.R;
import java.util.regex.Pattern;

/**
 * A veiled email address is where we don't want to display the email address, because the address
 * might be throw-away, or temporary. For these veiled addresses, we want to display some alternate
 * information. To find if an email address is veiled, call the method
 * {@link #isVeiledAddress(Resources, String)}
 */
public final class VeiledAddressMatcher {
    /**
     * Private object that does the actual matching. The object is initialized lazily.
     */
    private Pattern mMatcher = null;

    /**
     * Resource for the regex pattern that specifies a veiled addresses.
     */
    private static final int VEILED_RESOURCE = R.string.veiled_address;

    /**
     * Resource that specifies whether veiled address matching is enabled.
     */
    private static final int VEILED_MATCHING_ENABLED = R.bool.veiled_address_enabled;

    /**
     * True if veiled address matching is enabled, false otherwise.
     */
    protected static boolean mVeiledMatchingEnabled = false;

    /**
     */
    protected static boolean mInitialized = false;

    /**
     * Similar to {@link #VEILED_ALTERNATE_TEXT} except this is for addresses where we don't have
     * the name corresponding to the veiled address. Since we don't show the address, we should
     * indicate that the recipient is unknown to us.
     */
    public static final int VEILED_ALTERNATE_TEXT_UNKNOWN_PERSON =
            R.string.veiled_alternate_text_unknown_person;

    /**
     * When we show a veiled address, we should show an alternate string rather than the email
     * address. This is the resource that specifies the alternate string.
     */
    public static final int VEILED_ALTERNATE_TEXT = R.string.veiled_alternate_text;

    /**
     * Returns true if the given email address is a throw-away (or veiled) address. Such addresses
     * are created using special server-side logic for the purpose of keeping the real address of
     * the user hidden.
     * @param address
     * @return true if the address is veiled, false otherwise.
     */
    public final boolean isVeiledAddress (Resources resources, String address) {
        if (mInitialized && !mVeiledMatchingEnabled) {
            // We have been initialized, and veiled address matching is explicitly disabled.
            // Match nothing.
            return false;
        }
        if (resources == null) {
            return false;
        }
        if (mInitialized == false) {
            mVeiledMatchingEnabled = resources.getBoolean(VEILED_MATCHING_ENABLED);
            mInitialized = true;
            if (mVeiledMatchingEnabled) {
                mMatcher = Pattern.compile(resources.getString(VEILED_RESOURCE));
            }
        }
        if (mMatcher == null) {
            return false;
        }
        return mMatcher.matcher(address).matches();
    }
}
