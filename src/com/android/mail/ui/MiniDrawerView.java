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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;

import com.android.mail.R;
import com.android.mail.content.ObjectCursor;
import com.android.mail.providers.Folder;
import com.android.mail.utils.LogUtils;

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

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        // This ViewGroup is focusable purely so it can act as a stable target for other views to
        // designate as their left/right focus ID. When focus comes to this view, the XML
        // declaration of descendantFocusability=FOCUS_AFTER_DESCENDANTS means it will always try
        // to focus one of its children before resorting to this (great! we basically never want
        // this container to gain focus).
        //
        // But the usual focus search towards the LEFT (in LTR) actually starts at the bottom,
        // which is weird. So override all focus requests that land on this parent to use the
        // FORWARD direction so the top-most item gets first focus. This will not affect focus
        // traversal within this ViewGroup as the descendantFocusability prevents the parent from
        // gaining focus.
        return super.requestFocus(FOCUS_DOWN, previouslyFocusedRect);
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
            newCurrentAccountView.setClickable(false);
            newCurrentAccountView.setFocusable(false);
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
        // Reset ellipsis visibility
        mDotdotdot.setVisibility(View.VISIBLE);
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
                    iv.setActivated(mController.isSelectedFolder(f));
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

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() == 0) {
            return;
        }
        final int availableHeight = getMeasuredHeight() - getPaddingBottom() - getPaddingTop();

        int childHeight = 0;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.equals(mSpacer) || child.getVisibility() == View.GONE) {
                continue;
            }
            final LayoutParams params = (LayoutParams) child.getLayoutParams();
            childHeight += params.topMargin + params.bottomMargin + child.getMeasuredHeight();
        }

        if (childHeight <= availableHeight) {
            // Nothing to do here
            super.onLayout(changed, l, t, r, b);
            return;
        }

        mDotdotdot.setVisibility(View.GONE);

        final LayoutParams dotDotDotParams = (LayoutParams) mDotdotdot.getLayoutParams();
        childHeight -= dotDotDotParams.topMargin + dotDotDotParams.bottomMargin
                + mDotdotdot.getMeasuredHeight();

        // Check again
        if (childHeight <= availableHeight) {
            // Fit the spacer to the remaining height
            measureSpacer(availableHeight - childHeight);
            super.onLayout(changed, l, t, r, b);
            return;
        }

        // Sanity check
        if (getChildAt(getChildCount() - 1).equals(mSpacer)) {
            LogUtils.wtf(LogUtils.TAG, "The ellipsis was the last item in the minidrawer and " +
                    "hiding it didn't help fit all the views");
        }

        final View childToHide = getChildAt(indexOfChild(mSpacer) + 1);
        childToHide.setVisibility(View.GONE);

        final LayoutParams childToHideParams = (LayoutParams) childToHide.getLayoutParams();
        childHeight -= childToHideParams.topMargin + childToHideParams.bottomMargin +
                childToHide.getMeasuredHeight();

        // Check again
        if (childHeight <= availableHeight) {
            // Fit the spacer to the remaining height
            measureSpacer(availableHeight - childHeight);
            super.onLayout(changed, l, t, r, b);
            return;
        }

        LogUtils.wtf(LogUtils.TAG,
                "Hid two children in the minidrawer and still couldn't fit " +
                        "all the views");
    }

    private void measureSpacer(int height) {
        final LayoutParams spacerParams = (LayoutParams) mSpacer.getLayoutParams();
        final int spacerHeight = height -
                spacerParams.bottomMargin - spacerParams.topMargin;
        final int spacerWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        mSpacer.measure(MeasureSpec.makeMeasureSpec(spacerWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(spacerHeight, MeasureSpec.EXACTLY));

    }
}
