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

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.ui.MailActionBar.Callback;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

/**
 * A dummy activity to make an actionbar and a few buttons to test some of the interactions.
 * This is pure UI, there is no functionality here.
 */
public class ActionbarActivity extends Activity
        implements View.OnCreateContextMenuListener,RestrictedActivity, Callback {
    private MailActionBar mActionBar;
    private Context mContext;
    private int mActionBarMode;
    private ViewMode mViewMode;

    /**
     *
     */
    public ActionbarActivity() {
        super();
        mActionBarMode = ViewMode.UNKNOWN;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
        return mActionBar.prepareOptionsMenu(menu) || super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        return mActionBar.createOptionsMenu(menu) || super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar bar = this.getActionBar();
        mContext = getApplicationContext();
        mViewMode = new ViewMode(mContext);
        mViewMode.enterConversationListMode();

        if (mActionBar == null){
            mActionBar = (MailActionBar) LayoutInflater.from(mContext).inflate(
                    R.layout.actionbar_view, null);
        }
        mActionBar.initialize(this, this, mViewMode, bar);
        setContentView(R.layout.actionbar_tests);
    }

    /**
     * Change the action bar mode, and redraw the actionbar.
     * @param mode
     */
    private void changeMode(int mode){
        mActionBar.setMode(mode);
        // Tell the framework to redraw the Action Bar
        invalidateOptionsMenu();
    }

    // Methods that will be called through the android:onclick attribute in layout XML.
    public void testSetBackButton(View v){
        mActionBar.setBackButton();
    }

    public void testRemoveBackButton(View v){
        mActionBar.removeBackButton();
    }

    public void testSearchConversationMode(View v){
        changeMode(ViewMode.SEARCH_RESULTS);
    }

    public void testNormalMode(View v){
        changeMode(ViewMode.UNKNOWN);
    }

    public void testSearchResultMode(View v){
        changeMode(ViewMode.SEARCH_RESULTS);
    }

    public void testLabelMode(View v){
        changeMode(ViewMode.FOLDER_LIST);
    }

    @Override
    public void enterSearchMode() {
        Toast.makeText(this, "Entering Search Mode", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void exitSearchMode() {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void reloadSearch(String string) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public boolean navigateToAccount(Account account) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void navigateToFolder(String folderCanonicalName) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void showFolderList() {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public String getCurrentAccount() {
        // TODO(viki): Auto-generated method stub
        return null;
    }

    @Override
    public ConversationListContext getCurrentListContext() {
        // TODO(viki): Auto-generated method stub
        return null;
    }

    @Override
    public void startActionBarStatusCursorLoader(String account) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void stopActionBarStatusCursorLoader(String account) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public Context getActivityContext() {
        return this;
    }
}
