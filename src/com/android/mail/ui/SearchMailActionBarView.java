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

package com.android.mail.ui;

import android.app.ActionBar;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

public class SearchMailActionBarView extends MailActionBarView {

    public SearchMailActionBarView(Context context) {
        this(context, null);
    }

    public SearchMailActionBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchMailActionBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        // We start out with every option enabled. Based on the current view, we disable actions
        // that are possible.
        super.onPrepareOptionsMenu(menu);
        switch (getMode()) {
            case ViewMode.SEARCH_RESULTS_LIST:
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                mActionBar.setDisplayHomeAsUpEnabled(true);
                setEmptyMode();
                if (!showConversationSubject()) {
                    setPopulatedSearchView();
                }
                clearSearchFocus();
                break;
        }
        return false;
    }


    private void clearSearchFocus() {
        // Remove focus from the search action menu in search results mode so
        // the IME and the suggestions don't get in the way.
        MenuItem search = getSearch();
        if (search != null) {
            SearchView searchWidget = (SearchView) search.getActionView();
            searchWidget.clearFocus();
        }
    }

    private void setPopulatedSearchView() {
        MenuItem search = getSearch();
        if (search != null) {
            search.expandActionView();
            String query = mActivity.getIntent().getStringExtra(
                    ConversationListContext.EXTRA_SEARCH_QUERY);
            SearchView searchWidget = (SearchView) search.getActionView();
            if (!TextUtils.isEmpty(query)) {
                searchWidget.setQuery(query, false);
            }
            searchWidget.clearFocus();
        }
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        super.onMenuItemActionCollapse(item);
        // Work around b/6664203 by manually forcing this view to be VISIBLE
        // upon ActionView collapse. DISPLAY_SHOW_CUSTOM will still control its final
        // visibility.
        int mode = getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST
                || (Utils.showTwoPaneSearchResults(getContext())
                        && mode == ViewMode.SEARCH_RESULTS_CONVERSATION)) {

            // When the action menu is collapsed, we have performed a search,
            // pop the search fragment.
            mController.exitSearchMode();
        }
        // Have to return true here. Unlike other callbacks, the return value
        // here is whether we want to suppress the action (rather than consume the action). We
        // don't want to suppress the action.
        return true;
    }
}
