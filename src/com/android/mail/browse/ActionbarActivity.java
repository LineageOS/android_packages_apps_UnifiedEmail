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

package com.android.mail.browse;

import com.android.mail.ConversationListContext;
import com.android.mail.MailActionBar;
import com.android.mail.ActionBarView.Mode;
import com.android.mail.MailActionBar.Callback;
import com.android.mail.RestrictedActivity;
import com.android.mail.R;
import com.android.mail.ViewMode;

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
    private MailActionBar.Mode mActionBarMode;
    private ViewMode mViewMode;

    /**
     *
     */
    public ActionbarActivity() {
        super();
        mActionBarMode = Mode.NORMAL;
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
        mViewMode.transitionToConversationListMode();

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
    private void changeMode(Mode mode){
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
        changeMode(Mode.SEARCH_RESULTS_CONVERSATION);
    }

    public void testNormalMode(View v){
        changeMode(Mode.NORMAL);
    }

    public void testSearchResultMode(View v){
        changeMode(Mode.SEARCH_RESULTS);
    }

    public void testLabelMode(View v){
        changeMode(Mode.LABEL);
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
    public boolean navigateToAccount(String account) {
        // TODO(viki): Auto-generated method stub
        return false;
    }

    @Override
    public void navigateToLabel(String labelCanonicalName) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void showLabelList() {
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
}
