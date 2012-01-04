/*
 * Copyright (C) 2012 Google Inc.
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

package com.android.email.browse;

/**
 * A header block in the conversation view that corresponds to a region in the web content. It may
 * also be eligible for snapping.
 *
 */
public interface HeaderBlock {

    /**
     * Eligible to become a a snappy header?
     */
    boolean canSnap();
    /**
     * If eligible for snapping, returns the populated header view to snap.
     */
    MessageHeaderView getSnapView();
    /**
     * Spaces out this view in its container by this number of pixels to match its message body
     * size, if any.
     */
    void setMarginBottom(int height);
    void setVisibility(int vis);
    /**
     * Called on a header when new contact info is known for the conversation. If the header
     * displays contact info, it should refresh it.
     */
    void updateContactInfo();
    /**
     * If this header can be starred/unstarred, change whether the message header appears to be
     * starred or not. Does not actually mark the backing message starred/unstarred.
     */
    void setStarDisplay(boolean starred);

}
