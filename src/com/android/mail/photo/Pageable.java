/*
 * Copyright (C) 2011 Google Inc.
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

package com.android.mail.photo;

/**
 * Defines the interface to a pageable data source.
 */
public interface Pageable {
    /** Number of cursor rows in a page */
    static final int CURSOR_PAGE_SIZE = 16;

    /**
     * @return true if more data is left to be read.
     */
    boolean hasMore();

    /**
     * Loads the next page of data.
     */
    void loadMore();

    /**
     * @return the current page
     */
    int getCurrentPage();
}
