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

package com.android.email.providers;

import com.google.common.collect.ImmutableSet;
import java.lang.IllegalArgumentException;
import java.util.Set;


/**
 * A helper class to validate projections for the UIProvider queries.
 *
 * TODO(pwestbro): Consider creating an abstract ContentProvider that contains this
 * functionionality.
 */
public class UIProviderValidator {

    private final static Set<String> VALID_ACCOUNT_PROJECTION_VALUES =
            ImmutableSet.of(UIProvider.ACCOUNTS_PROJECTION);

    /**
     * Validates and returns the projection that can be used for an account query,
     */
    public static String[] validateAccountProjection(String[] projection)
            throws IllegalArgumentException {
        final String[] resultProjection;
        if (projection != null) {
            for (String column : projection) {
                if (!VALID_ACCOUNT_PROJECTION_VALUES.contains(column)) {
                    throw new IllegalArgumentException("Invalid projection");
                }
            }
            resultProjection = projection;
        } else {
            resultProjection = UIProvider.ACCOUNTS_PROJECTION;
        }
        return resultProjection;
    }
}