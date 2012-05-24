/*
 * Copyright (C) 2011 Google Inc.
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


package com.android.mail.photo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.ActionProvider;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.photo.util.ImageCache;

import java.util.ArrayList;

/**
 * The base fragment activity
 */
public abstract class BaseFragmentActivity extends FragmentActivity {

    // Logging
    private static final String TAG = "BaseFragmentActivity";

    // Instance variables
    private final MenuItem[] mMenuItems = new MenuItem[3];
    /** Whether or not to hide the title bar */
    private boolean mHideTitleBar;

    /**
     * A simple implementation of the Menu interface
     */
    private static class TitleMenu implements Menu {
        private final ArrayList<TitleMenuItem> mItems = new ArrayList<TitleMenuItem>();
        private final Context mContext;

        /**
         * Constructor
         *
         * @param context The context
         */
        public TitleMenu(Context context) {
            mContext = context;
        }

        @Override
        public int size() {
            return mItems.size();
        }

        @Override
        public void setQwertyMode(boolean isQwerty) {
        }

        @Override
        public void setGroupVisible(int group, boolean visible) {
        }

        @Override
        public void setGroupEnabled(int group, boolean enabled) {
        }

        @Override
        public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
        }

        @Override
        public void removeItem(int id) {
        }

        @Override
        public void removeGroup(int groupId) {
        }

        @Override
        public boolean performShortcut(int keyCode, KeyEvent event, int flags) {
            return false;
        }

        @Override
        public boolean performIdentifierAction(int id, int flags) {
            return false;
        }

        @Override
        public boolean isShortcutKey(int keyCode, KeyEvent event) {
            return false;
        }

        @Override
        public boolean hasVisibleItems() {
            return false;
        }

        @Override
        public MenuItem getItem(int index) {
            return mItems.get(index);
        }

        @Override
        public MenuItem findItem(int id) {
            for (MenuItem item : mItems) {
                if (item.getItemId() == id) {
                    return item;
                }
            }
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public void clear() {
            mItems.clear();
        }

        @Override
        public SubMenu addSubMenu(int groupId, int itemId, int order, int titleRes) {
            return null;
        }

        @Override
        public SubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
            return null;
        }

        @Override
        public SubMenu addSubMenu(int titleRes) {
            return null;
        }

        @Override
        public SubMenu addSubMenu(CharSequence title) {
            return null;
        }

        @Override
        public int addIntentOptions(int groupId, int itemId, int order, ComponentName caller,
                Intent[] specifics, Intent intent, int flags, MenuItem[] outSpecificItems) {
            return 0;
        }

        @Override
        public MenuItem add(int groupId, int itemId, int order, int titleRes) {
            TitleMenuItem item = new TitleMenuItem(mContext, itemId, titleRes);
            mItems.add(item);
            return item;
        }

        @Override
        public MenuItem add(int groupId, int itemId, int order, CharSequence title) {
            TitleMenuItem item = new TitleMenuItem(mContext, itemId, title);
            mItems.add(item);
            return item;
        }

        @Override
        public MenuItem add(int titleRes) {
            TitleMenuItem item = new TitleMenuItem(mContext, 0, titleRes);
            mItems.add(item);
            return item;
        }

        @Override
        public MenuItem add(CharSequence title) {
            TitleMenuItem item = new TitleMenuItem(mContext, 0, title);
            mItems.add(item);
            return item;
        }
    }

    /**
     * A simple MenuItem implementation
     */
    private static class TitleMenuItem implements MenuItem {
        private final Resources mResources;
        private CharSequence mTitle;
        private final int mItemId;
        private Drawable mIcon;
        private boolean mVisible;
        private boolean mEnabled;
        @SuppressWarnings("unused")
        private int mActionEnum;

        /**
         * Constructor
         *
         * @param context The context
         * @param itemId The item id
         * @param titleRes The title resource
         */
        public TitleMenuItem(Context context, int itemId, int titleRes) {
            mResources = context.getResources();
            mTitle = mResources.getString(titleRes);
            mItemId = itemId;
        }

        /**
         * Constructor
         *
         * @param context The context
         * @param itemId The item id
         * @param title The title
         */
        public TitleMenuItem(Context context, int itemId, CharSequence title) {
            mResources = context.getResources();
            mTitle = title;
            mItemId = itemId;
        }

        @Override
        public View getActionView() {
            return null;
        }

        @Override
        public char getAlphabeticShortcut() {
            return 0;
        }

        @Override
        public int getGroupId() {
            return 0;
        }

        @Override
        public Drawable getIcon() {
            return mIcon;
        }

        @Override
        public Intent getIntent() {
            return null;
        }

        @Override
        public int getItemId() {
            return mItemId;
        }

        @Override
        public ContextMenuInfo getMenuInfo() {
            return null;
        }

        @Override
        public char getNumericShortcut() {
            return 0;
        }

        @Override
        public int getOrder() {
            return 0;
        }

        @Override
        public SubMenu getSubMenu() {
            return null;
        }

        @Override
        public CharSequence getTitle() {
            return mTitle;
        }

        @Override
        public CharSequence getTitleCondensed() {
            return null;
        }

        @Override
        public boolean hasSubMenu() {
            return false;
        }

        @Override
        public boolean isCheckable() {
            return false;
        }

        @Override
        public boolean isChecked() {
            return false;
        }

        @Override
        public boolean isEnabled() {
            return mEnabled;
        }

        @Override
        public boolean isVisible() {
            return mVisible;
        }

        @Override
        public MenuItem setActionView(View view) {
            return this;
        }

        @Override
        public MenuItem setActionView(int resId) {
            return this;
        }

        @Override
        public MenuItem setAlphabeticShortcut(char alphaChar) {
            return this;
        }

        @Override
        public MenuItem setCheckable(boolean checkable) {
            return this;
        }

        @Override
        public MenuItem setChecked(boolean checked) {
            return this;
        }

        @Override
        public MenuItem setEnabled(boolean enabled) {
            mEnabled = enabled;
            return this;
        }

        @Override
        public MenuItem setIcon(Drawable icon) {
            mIcon = icon;
            return this;
        }

        @Override
        public MenuItem setIcon(int iconRes) {
            if (iconRes != 0) {
                mIcon = mResources.getDrawable(iconRes);
            }
            return this;
        }

        @Override
        public MenuItem setIntent(Intent intent) {
            return this;
        }

        @Override
        public MenuItem setNumericShortcut(char numericChar) {
            return this;
        }

        @Override
        public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener) {
            return this;
        }

        @Override
        public MenuItem setShortcut(char numericChar, char alphaChar) {
            return this;
        }

        @Override
        public void setShowAsAction(int actionEnum) {
            mActionEnum = actionEnum;
        }

        @Override
        public MenuItem setTitle(CharSequence title) {
            mTitle = title;
            return this;
        }

        @Override
        public MenuItem setTitle(int title) {
            mTitle = mResources.getString(title);
            return this;
        }

        @Override
        public MenuItem setTitleCondensed(CharSequence title) {
            return this;
        }

        @Override
        public MenuItem setVisible(boolean visible) {
            mVisible = visible;
            return this;
        }

        @Override
        public MenuItem setShowAsActionFlags(int actionEnum) {
            return null;
        }

       @Override
        public MenuItem setActionProvider(ActionProvider actionProvider) {
            return null;
        }

        @Override
        public ActionProvider getActionProvider() {
            return null;
        }

        @Override
        public boolean expandActionView() {
            return false;
        }

        @Override
        public boolean collapseActionView() {
            return false;
        }

        @Override
        public boolean isActionViewExpanded() {
            return false;
        }

        @Override
        public MenuItem setOnActionExpandListener(OnActionExpandListener listener) {
            return null;
        }
    }

    // The title bar click listener
    private final View.OnClickListener mTitleClickListener = new TitleClickListener();
    private class TitleClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            final int id = v.getId();
            if (id == R.id.titlebar_icon_layout) {
                onTitlebarLabelClick();
            } else if (id == R.id.title_button_1) {
                if (mMenuItems[0] != null) {
                    onOptionsItemSelected(mMenuItems[0]);
                }
            } else if (id == R.id.title_button_2) {
                if (mMenuItems[1] != null) {
                    onOptionsItemSelected(mMenuItems[1]);
                }
            } else if (id == R.id.title_button_3) {
                if (mMenuItems[2] != null) {
                    onOptionsItemSelected(mMenuItems[2]);
                }
            } else {
            }
        }
    }

    /**
     * Constructor
     */
    public BaseFragmentActivity() {
    }

    /**
     * Show the title bar without animation
     *
     * @param enableUp true to enable up action
     */
    protected void showTitlebar(boolean enableUp) {
        showTitlebar(false, enableUp);
    }

    /**
     * Shows the title bar with optional animation.
     *
     * @param showAnimation If {@code true}, animate the title bar show.
     * @param enableUp true to enable up action
     */
    protected void showTitlebar(boolean showAnimation, boolean enableUp) {
        final View titleLayout = findViewById(R.id.title_layout);

        if (mHideTitleBar == false && titleLayout.getVisibility() == View.VISIBLE) {
            return;
        }
        mHideTitleBar = false;

        final Animation currentAnimation = titleLayout.getAnimation();
        if (currentAnimation != null) {
            currentAnimation.cancel();
        }

        if (showAnimation) {
            final Animation titleAnimation = AnimationUtils.loadAnimation(this,
                    R.anim.fade_in);
            titleLayout.startAnimation(titleAnimation);
        }

        titleLayout.findViewById(R.id.titlebar_up).setVisibility(
                enableUp ? View.VISIBLE : View.GONE);

        final View touchView = titleLayout.findViewById(R.id.titlebar_icon_layout);
        if (enableUp) {
            touchView.setOnClickListener(mTitleClickListener);
        } else {
            // If the title is not clickable, we want to make sure the
            // background doesn't change in response to touch events (this will
            // happen if something containing the title is clickable). To
            // accomplish this, we replace the selectable drawable with the
            // color transparent.
            touchView.setBackgroundColor(Color.TRANSPARENT);
        }

        titleLayout.setVisibility(View.VISIBLE);
    }

    /**
     * Hide the title bar without animation
     */
    protected void hideTitlebar() {
        hideTitlebar(false);
    }

    /**
     * Hides the title bar with optional animation.
     *
     * @param showAnimation If {@code true}, animate the title bar hide.
     */
    protected void hideTitlebar(boolean showAnimation) {
        final View titleLayout = findViewById(R.id.title_layout);

        if (mHideTitleBar == true) {
            return;
        }
        mHideTitleBar = true;

        final Animation currentAnimation = titleLayout.getAnimation();
        if (currentAnimation != null) {
            currentAnimation.cancel();
        }

        if (showAnimation) {
            final Animation titleAnimation = AnimationUtils.loadAnimation(this,
                    R.anim.fade_out);
            titleAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (mHideTitleBar) {
                        titleLayout.setVisibility(View.GONE);
                    }
                }
            });
            titleLayout.startAnimation(titleAnimation);
        } else {
            titleLayout.setVisibility(View.GONE);
        }
    }

    /**
     * Set the sub-title text in the title bar
     *
     * @param subtitle The text to display in the title
     */
    protected void setTitlebarSubtitle(String subtitle) {
        final TextView textView = (TextView)findViewById(R.id.titlebar_label_2);

        if (subtitle == null) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            textView.setText(subtitle);
        }
    }

    /**
     * Create the title menu buttons
     *
     * @param menuResId The menu id
     */
    public void createTitlebarButtons(int menuResId) {
        clearTitleButtons();

        final Menu menu = new TitleMenu(this);
        getMenuInflater().inflate(menuResId, menu);

        // Allow the activity to specify which menu items shall be displayed
        // in the titlebar
        onPrepareTitlebarButtons(menu);

        int visibleMenuCount = 0;
        for (int i = 0; i < menu.size(); i++) {
            if (menu.getItem(i).isVisible()) {
                visibleMenuCount++;
            }
        }

        switch (visibleMenuCount) {
            case 0: {
                break;
            }

            case 1: {
                setupTitleButton3(getVisibleItem(menu, 0));
                break;
            }

            case 2: {
                setupTitleButton2(getVisibleItem(menu, 0));
                setupTitleButton3(getVisibleItem(menu, 1));
                break;
            }

            case 3: {
                setupTitleButton1(getVisibleItem(menu, 0));
                setupTitleButton2(getVisibleItem(menu, 1));
                setupTitleButton3(getVisibleItem(menu, 2));
                break;
            }

            default: {
                Log.e("EsFragmentActivity", "Maximum title buttons is 3. You have "
                        + visibleMenuCount + " visible menu items");
                break;
            }
        }
    }

    /**
     * Override this method and set to visible the items you want to
     * show in the titlebar.
     *
     * @param menu The menu item
     */
    protected void onPrepareTitlebarButtons(Menu menu) {
    }

    /**
     * The title bar label was clicked
     */
    public void onTitlebarLabelClick() {
    }

    /**
     * Setup button 1
     *
     * @param menuItem The menu item
     */
    private void setupTitleButton1(MenuItem menuItem) {
        final ImageButton button = (ImageButton)findViewById(R.id.title_button_1);

        if (menuItem != null) {
            button.setImageDrawable(menuItem.getIcon());
            button.setVisibility(View.VISIBLE);
            button.setEnabled(menuItem.isEnabled());
            button.setOnClickListener(mTitleClickListener);
        } else {
            button.setVisibility(View.GONE);
        }

        mMenuItems[0] = menuItem;

    }

    /**
     * Setup button 2
     *
     * @param menuItem The menu item
     */
    private void setupTitleButton2(MenuItem menuItem) {
        final ImageButton button = (ImageButton)findViewById(R.id.title_button_2);

        if (menuItem != null) {
            button.setImageDrawable(menuItem.getIcon());
            button.setVisibility(View.VISIBLE);
            button.setEnabled(menuItem.isEnabled());
            button.setOnClickListener(mTitleClickListener);
        } else {
            button.setVisibility(View.GONE);
        }

        mMenuItems[1] = menuItem;
    }

    /**
     * Setup button 3
     *
     * @param menuItem The menu item
     */
    private void setupTitleButton3(MenuItem menuItem) {
        final ImageButton button = (ImageButton)findViewById(R.id.title_button_3);

        if (menuItem != null) {
            button.setImageDrawable(menuItem.getIcon());
            button.setVisibility(View.VISIBLE);
            button.setEnabled(menuItem.isEnabled());
            button.setOnClickListener(mTitleClickListener);
        } else {
            button.setVisibility(View.GONE);
        }

        mMenuItems[2] = menuItem;
    }

    /**
     * Clear the action buttons
     */
    private void clearTitleButtons() {
        setupTitleButton1(null);
        setupTitleButton2(null);
        setupTitleButton3(null);
    }

    /**
     * Get the visible item with the specified index
     *
     * @param menu The menu
     * @param index The index
     *
     * @return The menu item
     */
    private MenuItem getVisibleItem(Menu menu, int index) {
        int visibleItemIndex = 0;
        for (int i = 0; i < menu.size(); i++) {
            if (menu.getItem(i).isVisible()) {
                if (visibleItemIndex == index) {
                    return menu.getItem(i);
                }

                visibleItemIndex++;
            }
        }

        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        ImageCache.getInstance(this).refresh();
    }
}
