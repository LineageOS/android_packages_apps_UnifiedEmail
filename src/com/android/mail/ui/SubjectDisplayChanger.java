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

/**
 * Interface to allow changing the subject shown in Conversation View. Classes implementing this
 * interface know how to change the subject shown in Conversation View (
 * {@link #setSubject(String)}). They can also measure the view and determine how much of the
 * subject string was not shown due to screen constraints ({@link #getUnshownSubject(String)}).
 */
// Called ConversationSubjectDisplayer in Gmail.
public interface SubjectDisplayChanger {
    /**
     * Set the subject of the conversation view to the subject given.
     * @param subject the subject string
     */
    // This had the signature setSubject(ConversationInfo info, String subject) in Gmail, but info
    // was not being used anywhere.
    void setSubject(String subject);

    /**
     * Clear the subject display.
     */
    void clearSubject();

    /**
     * Fits the subject text into the view that will display it, and returns any text that
     * does not fit. If all text fits, it will return an empty string. If the subject will not be
     * displayed externally, it will return the entire subject so the conversation view can display
     * it internally.
     *
     * This method is purely about measurement, and should not modify the view itself.
     *
     * @param subject conversation subject to measure
     * @return remainder of the subject that doesn't fit, the entire subject if subjects are not
     * displayed, or empty if the subject can be displayed in its entirety
     */
    String getUnshownSubject(String subject);
}