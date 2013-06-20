/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.LoaderManager;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

/**
 * For folders that might contain other folders, we show the nested folders within this view.
 * Tapping on this opens the folder.
 */
public class NestedFolderView extends LinearLayout implements ConversationSpecialItemView,
        SwipeableItemView {
    protected static final String LOG_TAG = LogTag.getLogTag();
    /**
     * The actual view that is displayed and is perhaps swiped away. We don't allow swiping,
     * but this is required by the {@link SwipeableItemView} interface.
     */
    private View mSwipeableContent;
    /** The folder this view represents */
    private Folder mFolder;

    public NestedFolderView(Context context) {
        super(context);
    }

    public NestedFolderView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NestedFolderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSwipeableContent = findViewById(R.id.swipeable_content);
    }

    @Override
    public void onUpdate(String s, Folder folder, ConversationCursor conversationCursor) {
        // Do nothing. We don't care about the change to the conversation cursor here.
        // Nested folders only care if they were removed from the parent folder,
        // so supposing we should check for that here.
    }

    /**
     * Sets the folder associated with this view. This method is meant to be called infrequently,
     * since we assume that a new view will be created when the unread count changes.
     * @param folder the folder that this view represents.
     */
    public void setFolder(Folder folder) {
        mFolder = folder;
        // Since we assume that setFolder will be called infrequently (once currently),
        // we don't bother saving the textviews for folder name and folder unread count.  If we
        // find that setFolder gets called repeatedly, it might be prudent to remember the
        // references to these textviews, making setFolder slightly faster.
        TextView t = (TextView) findViewById(R.id.nested_folder_name);
        t.setText(folder.name);
        t = (TextView) findViewById(R.id.nested_folder_unread);
        t.setText("" + folder.unreadCount);
    }

    /**
     * Returns the folder associated with this view
     * @return a folder that this view represents.
     */
    public Folder getFolder() {
        return mFolder;
    }

    @Override
    public boolean getShouldDisplayInList() {
        // Nested folders once created are always displayed in the list.
        return true;
    }

    @Override
    public int getPosition() {
        // We only have one element, and that's always at the top for now.
        return 0;
    }

    @Override
    public void setAdapter(AnimatedAdapter animatedAdapter) {
        // Do nothing, since the adapter creates these views.
    }

    @Override
    public void bindLoaderManager(LoaderManager loaderManager) {
        // Do nothing. We don't need the loader manager.
    }

    @Override
    public void cleanup() {
        // Do nothing.
    }

    @Override
    public void onConversationSelected() {
        // Do nothing. We don't care if conversations are selected.
    }

    @Override
    public void onCabModeEntered() {
        // Do nothing. We don't care if cab mode was entered.
    }

    @Override
    public boolean acceptsUserTaps() {
        return true;
    }

    @Override
    public SwipeableView getSwipeableView() {
        return SwipeableView.from(mSwipeableContent);
    }

    @Override
    public boolean canChildBeDismissed() {
        // The folders can never be dismissed, return false.
        return false;
    }

    @Override
    public void dismiss() {
        /** How did this happen? We returned false in {@link #canChildBeDismissed()} so this
         * method should never be called. */
        LogUtils.wtf(LOG_TAG, "NestedFolderView.dismiss() called. Not expected.");
    }

    @Override
    public float getMinAllowScrollDistance() {
        return -1;
    }
}
