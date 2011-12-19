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

package com.android.email;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Application;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.MenuInflater;
import android.view.View;
import android.view.Window;

/**
 * {@link RestrictedActivity} gives access to a subset of {@link Activity} methods. Those methods
 * must match the signatures from {@link Activity}. It also includes a number of common methods in
 * all Gmail's {@link Activity}.
 *
 * @author phamm
 */
public interface RestrictedActivity {
    Application getApplication();

    ComponentName getComponentName();

    Window getWindow();

    ContentResolver getContentResolver();

    FragmentManager getFragmentManager();

    LoaderManager getLoaderManager();

    Intent getIntent();

    MenuInflater getMenuInflater();

    ActionBar getActionBar();

    View findViewById(int id);

    boolean isChangingConfigurations();

    boolean isFinishing();

    void setDefaultKeyMode(int mode);

    void setContentView(int layoutResId);

    void setTitle(CharSequence title);

    void invalidateOptionsMenu();

    void startActivityForResult(Intent intent, int requestCode);

    void setResult(int resultCode, Intent data);

    void showDialog(int id);

    ActionMode startActionMode(ActionMode.Callback callback);

    void onBackPressed();

    void finish();

    public boolean onSearchRequested();

    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch);

}
