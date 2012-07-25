// Copyright 2012 Google Inc. All Rights Reserved.

package com.android.mail.ui;

import android.database.DataSetObserver;

import com.android.mail.providers.Folder;

/**
 * The canonical owner of the apps' current {@link Folder} should implement this interface to keep
 * other components updated.
 *
 */
public interface FolderController {

    Folder getFolder();

    void registerFolderObserver(DataSetObserver observer);
    void unregisterFolderObserver(DataSetObserver observer);

}
