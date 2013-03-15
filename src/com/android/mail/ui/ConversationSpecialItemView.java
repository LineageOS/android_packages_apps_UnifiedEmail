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

import android.app.LoaderManager;
import android.widget.BaseAdapter;

import com.android.mail.browse.ConversationCursor;
import com.android.mail.providers.Folder;

/**
 * An interface for a view that can be inserted into an {@link AnimatedAdapter} at an arbitrary
 * point. The methods described here control whether the view gets displayed, and what it displays.
 */
public interface ConversationSpecialItemView {
    /**
     * Called when there as an update to the information being displayed.
     *
     * @param cursor The {@link ConversationCursor}. May be <code>null</code>
     */
    void onUpdate(String account, Folder folder, ConversationCursor cursor);

    boolean getShouldDisplayInList();

    int getPosition();

    void setAdapter(BaseAdapter adapter);

    void bindLoaderManager(LoaderManager loaderManager);

    /**
     * Called when the view is being destroyed.
     */
    void cleanup();
}
