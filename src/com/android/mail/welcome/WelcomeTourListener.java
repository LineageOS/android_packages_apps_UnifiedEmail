// Copyright 2014 Google Inc. All Rights Reserved.
package com.android.mail.welcome;

public interface WelcomeTourListener {
    /**
     * Implemented by activities which support a welcome tour.
     * @param completionListener The callback invoked when the welcome tour is either skipped or
     *                           completed.
     */
    void onWelcomeTourRequested(WelcomeTourCompletionListener completionListener);
}
