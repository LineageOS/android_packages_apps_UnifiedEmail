// Copyright 2014 Google Inc. All Rights Reserved.
package com.android.mail.welcome;

public interface WelcomeTourCompletionListener {
    /** Status indication that the welcome tour was not shown. */
    public static final int STATUS_NOT_SHOWN = 0;
    /** Status indicating that the user has seen the welcome tour. */
    public static final int STATUS_SHOWN = 1;
    /** Status indicating that the user has discarded the welcome tour. */
    public static final int STATUS_DISCARDED = 2;

    /**
     * Called when the {@link android.app.LoaderManager.LoaderCallbacks<Boolean>} has decided
     * whether the tour should be shown.
     *
     * @param status The display status of the tour, in {@link #STATUS_NOT_SHOWN},
     *        {@link #STATUS_SHOWN} or {@link #STATUS_DISCARDED}.
     */
    void onWelcomeTourFinished(int status);
}
