/**
 * Copyright (c) 2013, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.ui;

import com.android.mail.R;

import android.widget.ImageView;
import android.widget.TextView;

import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * The view for each folder in the folder list.
 */
public class AccountItemView extends RelativeLayout {
    private final String LOG_TAG = LogTag.getLogTag();
    // Static colors
    private static int NON_DROPPABLE_TARGET_TEXT_COLOR;

    // Static bitmap
    private static Bitmap SHORTCUT_ICON;

    // These are fine to be static, as these Drawables only have one state
    private static Drawable DROPPABLE_HOVER_BACKGROUND;
    private static Drawable DRAG_STEADY_STATE_BACKGROUND;

    private Drawable mBackground;
    private ColorStateList mInitialFolderTextColor;
    private ColorStateList mInitialUnreadCountTextColor;

    private Folder mFolder;
    private TextView mFolderTextView;
    private TextView mUnreadCountTextView;
    private TextView mUnseenCountTextView;
    private ImageView mFolderParentIcon;

    public AccountItemView(Context context) {
        super(context);
    }

    public AccountItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AccountItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (SHORTCUT_ICON == null) {
            final Resources res = getResources();
            SHORTCUT_ICON = BitmapFactory.decodeResource(
                    res, R.mipmap.ic_launcher_shortcut_folder);
            DROPPABLE_HOVER_BACKGROUND =
                    res.getDrawable(R.drawable.folder_drag_target);
            DRAG_STEADY_STATE_BACKGROUND =
                    res.getDrawable(R.drawable.folder_no_hover);
            NON_DROPPABLE_TARGET_TEXT_COLOR =
                    res.getColor(R.color.folder_disabled_drop_target_text_color);
        }
        mFolderTextView = (TextView)findViewById(R.id.name);
        mUnreadCountTextView = (TextView)findViewById(R.id.unread);
        mUnseenCountTextView = (TextView)findViewById(R.id.unseen);
        mBackground = getBackground();
        mInitialFolderTextColor = mFolderTextView.getTextColors();
        mInitialUnreadCountTextColor = mUnreadCountTextView.getTextColors();
        mFolderParentIcon = (ImageView) findViewById(R.id.folder_parent_icon);
    }

    public void bind(Account account, int count) {
        mFolder = null;
        mFolderTextView.setText(account.name);
        mFolderParentIcon.setVisibility(View.GONE);
        mUnreadCountTextView.setVisibility(View.GONE);
        setUnseenCount(Color.BLACK, 0);
        setUnreadCount(count);
    }

    /**
     * Takes in true if current item view should be modified to look like
     * the current account header. This should not get called with inactive or
     * non-displayed accounts.
     *
     * @param isCurrentAccount true if account is active, false otherwise
     */
    public void setCurrentAccount(boolean isCurrentAccount) {
        if(isCurrentAccount) {
            mUnreadCountTextView.setVisibility(View.GONE);
            mFolderTextView.setAllCaps(true);
            mFolderTextView.setTextColor(R.color.folder_list_heading_text_color);
            mFolderTextView.setTextAppearance(getContext(), android.R.style.TextAppearance_Small);
        }
    }

    /**
     * Sets the unread count, taking care to hide/show the textview if the count is zero/non-zero.
     */
    private void setUnreadCount(int count) {
        mUnreadCountTextView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (count > 0) {
            mUnreadCountTextView.setText(Utils.getUnreadCountString(getContext(), count));
        }
    }

    /**
     * Sets the unseen count, taking care to hide/show the textview if the count is zero/non-zero.
     */
    private void setUnseenCount(final int color, final int count) {
        mUnseenCountTextView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (count > 0) {
            mUnseenCountTextView.setBackgroundColor(color);
            mUnseenCountTextView.setText(
                    getContext().getString(R.string.inbox_unseen_banner, count));
        }
    }

    /**
     * Used if we detect a problem with the unread count and want to force an override.
     * @param count
     */
    public final void overrideUnreadCount(int count) {
        LogUtils.e(LOG_TAG, "FLF->FolderItem.getFolderView: unread count mismatch found (%s vs %d)",
                mUnreadCountTextView.getText(), count);
        setUnreadCount(count);
    }

    private boolean isDroppableTarget(DragEvent event) {
        return false;
    }
}
