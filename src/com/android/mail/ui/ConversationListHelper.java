/*
 * Copyright (C) 2013 Google Inc.
 * Licensed to The Android Open Source Project.
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
package com.android.mail.ui;

import com.google.common.collect.Lists;

import android.content.Context;

import com.android.mail.providers.Account;

import java.util.ArrayList;

public class ConversationListHelper {
    /**
     * Creates a list of newly created special views.
     */
    public ArrayList<ConversationSpecialItemView> makeConversationListSpecialViews(Context context,
            Account account, FolderSelector listener) {
        // TODO: Move conversation photo teaser view here once
        // getConversationListIcon() is moved out of Persistence
        return Lists.newArrayList();
    }
}
