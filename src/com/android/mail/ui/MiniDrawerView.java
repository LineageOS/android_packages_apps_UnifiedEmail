/**
 * Copyright (C) 2014 Google Inc.
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

import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;

import com.android.mail.R;
import com.android.mail.content.ObjectCursor;
import com.android.mail.providers.Folder;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A smaller version of the account- and folder-switching drawer view for tablet UIs.
 */
public class MiniDrawerView extends LinearLayout {

    private FolderListFragment mController;

    private View mDotdotdot;
    private View mSpacer;

    private final LayoutInflater mInflater;

    private static final int NUM_RECENT_ACCOUNTS = 2;

    public MiniDrawerView(Context context) {
        this(context, null);
    }

    public MiniDrawerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mInflater = LayoutInflater.from(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSpacer = findViewById(R.id.spacer);
        mDotdotdot = findViewById(R.id.dotdotdot);
        mDotdotdot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.toggleDrawerState();
            }
        });
    }

    public void setController(FolderListFragment flf) {
        mController = flf;
        final ListAdapter adapter = mController.getMiniDrawerAccountsAdapter();
        adapter.registerDataSetObserver(new Observer());
    }

    private class Observer extends DataSetObserver {

        @Override
        public void onChanged() {
            refresh();
        }
    }

    public void refresh() {
        if (mController == null || !mController.isAdded()) {
            return;
        }

        final ListAdapter adapter =
                mController.getMiniDrawerAccountsAdapter();

        if (adapter.getCount() > 0) {
            final View oldCurrentAccountView = getChildAt(0);
            if (oldCurrentAccountView != null) {
                removeView(oldCurrentAccountView);
            }
            final View newCurrentAccountView = adapter.getView(0, oldCurrentAccountView, this);
            addView(newCurrentAccountView, 0);
        }

        final int removePos = indexOfChild(mSpacer) + 1;
        final int recycleCount = getChildCount() - removePos;
        final Queue<View> recycleViews = new ArrayDeque<>(recycleCount);
        for (int recycleIndex = 0; recycleIndex < recycleCount; recycleIndex++) {
            final View recycleView = getChildAt(removePos);
            recycleViews.add(recycleView);
            removeView(recycleView);
        }

        final int adapterCount = Math.min(adapter.getCount(), NUM_RECENT_ACCOUNTS + 1);
        for (int accountIndex = 1; accountIndex < adapterCount; accountIndex++) {
            final View recycleView = recycleViews.poll();
            final View accountView = adapter.getView(accountIndex, recycleView, this);
            addView(accountView);
        }

        View child;

        // reset the inbox views for this account
        while ((child=getChildAt(1)) != mDotdotdot) {
            removeView(child);
        }
        final ObjectCursor<Folder> folderCursor = mController.getFoldersCursor();
        if (folderCursor != null && !folderCursor.isClosed()) {
            int pos = -1;
            int numInboxes = 0;
            while (folderCursor.moveToPosition(++pos)) {
                final Folder f = folderCursor.getModel();
                if (f.isInbox()) {
                    final ImageView iv = (ImageView) mInflater.inflate(
                            R.layout.mini_drawer_folder_item, this, false /* attachToRoot */);
                    iv.setTag(new FolderItem(f, iv));
                    iv.setContentDescription(f.name);
                    addView(iv, 1 + numInboxes);
                    numInboxes++;
                }
            }
        }
    }

    private class FolderItem implements View.OnClickListener {
        public final Folder folder;
        public final ImageView view;

        public FolderItem(Folder f, ImageView iv) {
            folder = f;
            view = iv;
            Folder.setIcon(folder, view);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            mController.onFolderSelected(folder, "mini-drawer");
        }
    }


}
