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

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.photo.PhotoViewPager.InterceptType;
import com.android.mail.photo.PhotoViewPager.OnInterceptTouchListener;
import com.android.mail.photo.adapters.PhotoPagerAdapter;
import com.android.mail.photo.adapters.BaseFragmentPagerAdapter.OnFragmentPagerListener;
import com.android.mail.photo.fragments.PhotoViewFragment;
import com.android.mail.photo.fragments.PhotoViewFragment.PhotoViewCallbacks;
import com.android.mail.photo.loaders.PhotoCursorLoader;
import com.android.mail.photo.loaders.PhotoPagerLoader;

import java.util.HashSet;
import java.util.Set;

/**
 * Activity to view the contents of an album.
 */
public class PhotoViewActivity extends BaseFragmentActivity implements PhotoViewCallbacks,
        LoaderCallbacks<Cursor>, OnPageChangeListener, OnInterceptTouchListener,
        OnFragmentPagerListener {

    /**
     * Listener to be invoked for screen events.
     */
    public static interface OnScreenListener {

        /**
         * The full screen state has changed.
         */
        public void onFullScreenChanged(boolean fullScreen, boolean animate);

        /**
         * A new view has been activated and the previous view de-activated.
         */
        public void onViewActivated();

        /**
         * Updates the view that can be used to show progress.
         *
         * @param progressView a View that can be used to show progress
         */
        public void onUpdateProgressView(ProgressBar progressView);

        /**
         * Called when a right-to-left touch move intercept is about to occur.
         *
         * @param origX the raw x coordinate of the initial touch
         * @param origY the raw y coordinate of the initial touch
         * @return {@code true} if the touch should be intercepted.
         */
        public boolean onInterceptMoveLeft(float origX, float origY);

        /**
         * Called when a left-to-right touch move intercept is about to occur.
         *
         * @param origX the raw x coordinate of the initial touch
         * @param origY the raw y coordinate of the initial touch
         * @return {@code true} if the touch should be intercepted.
         */
        public boolean onInterceptMoveRight(float origX, float origY);

        /**
         * Called when the action bar height is calculated.
         *
         * @param actionBarHeight The height of the action bar.
         */
        public void onActionBarHeightCalculated(int actionBarHeight);
    }

    /**
     * Listener to be invoked for menu item events.
     */
    public static interface OnMenuItemListener {

        /**
         * Prepare the title bar buttons.
         *
         * @return {@code true} if the title bar buttons were processed. Otherwise, {@code false}.
         */
        public boolean onPrepareTitlebarButtons(Menu menu);

        /**
         * Signals an item in your options menu was selected.
         *
         * @return {@code true} if the item selection was consumed. Otherwise, {@code false}.
         */
        public boolean onOptionsItemSelected(MenuItem item);
    }

    private final static String STATE_ITEM_KEY =
            "com.google.android.apps.plus.PhotoViewFragment.ITEM";
    private final static String STATE_FULLSCREEN_KEY =
            "com.google.android.apps.plus.PhotoViewFragment.FULLSCREEN";

    private static final int LOADER_PHOTO_LIST = R.id.photo_view_photo_list_loader_id;

    /** Count used when the real photo count is unknown [but, may be determined] */
    public static final int ALBUM_COUNT_UNKNOWN = -1;
    /** Count used when the real photo count can't be know [eg for a photo stream] */
    public static final int ALBUM_COUNT_UNKNOWABLE = -2;

    /** Argument key for the dialog message */
    public static final String KEY_MESSAGE = "dialog_message";

    public static int sMemoryClass;

    /** The URI of the photos we're viewing; may be {@code null} */
    private String mPhotosUri;
    /** The resolved URI of the photo to view; may be {@code null}. */
    private String mResolvedPhotoUri;
    /** The index of the currently viewed photo */
    private int mPhotoIndex;
    /** A hint for which cursor page the photo is located on */
    private int mPageHint = PhotoCursorLoader.LOAD_LIMIT_UNLIMITED;
    /** The name of the album */
    private String mAlbumName;
    /** The total number of photos; only valid if {@link #mIsEmpty} is {@code false}. */
    private int mAlbumCount = ALBUM_COUNT_UNKNOWN;
    /** {@code true} if the view is empty. Otherwise, {@code false}. */
    private boolean mIsEmpty;
    /** The root view of the activity */
    private View mRootView;
    /** The main pager; provides left/right swipe between photos */
    private PhotoViewPager mViewPager;
    /** Adapter to create pager views */
    private PhotoPagerAdapter mAdapter;
    /** Whether or not the view is currently scrolling between photos */
    private boolean mViewScrolling;
    /** Whether or not we're in "full screen" mode */
    private boolean mFullScreen;
    /** Whether or not we should only show the photo and no extra information */
    private boolean mShowPhotoOnly;
    /** The set of listeners wanting full screen state */
    private Set<OnScreenListener> mScreenListeners = new HashSet<OnScreenListener>();
    /** The set of listeners wanting title bar state */
    private Set<OnMenuItemListener> mMenuItemListeners = new HashSet<OnMenuItemListener>();
    /** When {@code true}, restart the loader when the activity becomes active */
    private boolean mRestartLoader;
    /** Whether or not this activity is paused */
    private boolean mIsPaused = true;
    /** The action bar height */
    private int mActionBarHeight;
    /** A layout listener to track when the action bar is laid out */
    private ActionBarLayoutListener mActionBarLayoutListener;
    // TODO(toddke) Find a better way to do this. We basically want the activity to display the
    // "loading..." progress until the fragment takes over and shows it's own "loading..."
    // progress [located in photo_header_view.xml]. We could potentially have all status displayed
    // by the activity, but, that gets tricky when it comes to screen rotation. For now, we
    // track the loading by this variable which is fragile and may cause phantom "loading..."
    // text.
    /** {@code true} if the fragment is loading. */
    private boolean mFragmentIsLoading;

    /** Listener to handle dialog button clicks for the failed dialog. */
    private DialogInterface.OnClickListener mFailedListener =
            new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActivityManager mgr = (ActivityManager) getApplicationContext().
                getSystemService(Activity.ACTIVITY_SERVICE);
        sMemoryClass = mgr.getMemoryClass();

        Intent mIntent = getIntent();
        mShowPhotoOnly = mIntent.getBooleanExtra(Intents.EXTRA_SHOW_PHOTO_ONLY, false);

        int currentItem = -1;
        if (savedInstanceState != null) {
            currentItem = savedInstanceState.getInt(STATE_ITEM_KEY, -1);
            mFullScreen = savedInstanceState.getBoolean(STATE_FULLSCREEN_KEY, false);
        } else {
            mFullScreen = mShowPhotoOnly;
        }

        // album name; if not set, use a default name
        if (mIntent.hasExtra(Intents.EXTRA_ALBUM_NAME)) {
            mAlbumName = mIntent.getStringExtra(Intents.EXTRA_ALBUM_NAME);
        } else {
            mAlbumName = getResources().getString(R.string.photo_view_default_title);
        }

        // uri of the photos to view; optional
        if (mIntent.hasExtra(Intents.EXTRA_PHOTOS_URI)) {
            mPhotosUri = mIntent.getStringExtra(Intents.EXTRA_PHOTOS_URI);
        }

        // the loader page hint
        if (mIntent.hasExtra(Intents.EXTRA_PAGE_HINT) && currentItem < 0) {
            mPageHint = mIntent.getIntExtra(Intents.EXTRA_PAGE_HINT,
                    PhotoCursorLoader.LOAD_LIMIT_UNLIMITED);
        }
        // Set the current item from the intent if wasn't in the saved instance
        if (mIntent.hasExtra(Intents.EXTRA_PHOTO_INDEX) && currentItem < 0) {
            currentItem = mIntent.getIntExtra(Intents.EXTRA_PHOTO_INDEX, -1);
        }
        mPhotoIndex = currentItem;

        setContentView(R.layout.photo_activity_view);
        mRootView = findViewById(R.id.photo_activity_root_view);
        // Create the adapter and add the view pager
        final Long forceLoadId = (mIntent.hasExtra(Intents.EXTRA_REFRESH))
                ? mIntent.getLongExtra(Intents.EXTRA_REFRESH, 0L)
                : null;

        mAdapter = new PhotoPagerAdapter(this, getSupportFragmentManager(), null,
                forceLoadId, mAlbumName);
        mAdapter.setFragmentPagerListener(this);

        mViewPager = (PhotoViewPager) findViewById(R.id.photo_view_pager);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setOnPageChangeListener(this);
        mViewPager.setOnInterceptTouchListener(this);

        // Kick off the loaders
        getSupportLoaderManager().initLoader(LOADER_PHOTO_LIST, null, this);

        if (Build.VERSION.SDK_INT >= 11) {
            final ActionBar actionBar = getActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
        } else {
            showTitlebar(false, true);
            createTitlebarButtons(R.menu.photo_view_menu);
        }

        updateView(mRootView);
    }

    @Override
    protected void onResume() {
        super.onResume();
//        if (isIntentAccountActive()) {
//            createTitlebarButtons(R.menu.photo_view_menu);
            setFullScreen(mFullScreen, false);

            mIsPaused = false;
            if (mRestartLoader) {
                mRestartLoader = false;
                getSupportLoaderManager().restartLoader(LOADER_PHOTO_LIST, null, this);
            }
//        } else {
//            finish();
//        }
    }

    @Override
    protected void onPause() {
        mIsPaused = true;

        if (mActionBarLayoutListener != null) {
            clearListener();
        }

        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // If in full screen mode, toggle mode & eat the 'back'
        if (mFullScreen && !mShowPhotoOnly) {
            toggleFullScreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        PhotoViewFragment photoFragment = null;
        if (fragment instanceof PhotoViewFragment) {
            photoFragment = (PhotoViewFragment) fragment;
        }

        // Set the progress view as new fragments are attached
        final ProgressBar progressView;
        if (Build.VERSION.SDK_INT < 11) {
            progressView = (ProgressBar) findViewById(R.id.progress_spinner);
        } else {
            progressView = (ProgressBar) findViewById(R.id.action_bar_progress_spinner_view);
        }

        if (photoFragment != null && progressView != null) {
            photoFragment.onUpdateProgressView(progressView);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (Build.VERSION.SDK_INT < 11) {
            // On SDK >= 11, we cannot set the progress bar view here; the menu may not be
            // inflated yet. We will set the progress view later, in #onCreateOptionsMenu().
            final ProgressBar progressView =
                    (ProgressBar) findViewById(R.id.progress_spinner);

            if (progressView != null) {
                for (OnScreenListener listener : mScreenListeners) {
                    listener.onUpdateProgressView(progressView);
                }
            }
        }
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        super.onPrepareDialog(id, dialog, args);
        if (id == R.id.photo_view_pending_dialog) {
            // Update the message each time this dialog is shown in order
            // to ensure it matches the current operation.
            if (dialog instanceof ProgressDialog) {
                // This should always be true
                final ProgressDialog pd = (ProgressDialog) dialog;
                pd.setMessage(args.getString(KEY_MESSAGE));
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        String tag = args.getString(Intents.EXTRA_TAG);
        if (id == R.id.photo_view_pending_dialog) {
            final ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(args.getString(KEY_MESSAGE));
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setCancelable(false);
            return progressDialog;
        } else if (id == R.id.photo_view_download_full_failed_dialog) {
            final RetryDialogListener retryListener = new RetryDialogListener(tag);
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.download_photo_retry)
                    .setPositiveButton(R.string.yes, retryListener)
                    .setNegativeButton(R.string.no, retryListener);
            return builder.create();
        } else if (id == R.id.photo_view_download_nonfull_failed_dialog) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.download_photo_error)
                    .setNeutralButton(R.string.ok, mFailedListener);
            return builder.create();
        }

        return null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(STATE_ITEM_KEY, mViewPager.getCurrentItem());
        outState.putBoolean(STATE_FULLSCREEN_KEY, mFullScreen);
    }

    @Override
    protected void onPrepareTitlebarButtons(Menu menu) {
        // Clear the menu items
        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setVisible(false);
        }

        // Let the fragments add back the ones it wants
        for (OnMenuItemListener listener : mMenuItemListeners) {
            if (listener.onPrepareTitlebarButtons(menu)) {
                // First listener to claim the title bar, gets it
                break;
            }
        }
    }

    @Override
    public void onTitlebarLabelClick() {
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        for (OnMenuItemListener listener : mMenuItemListeners) {
            if (listener.onOptionsItemSelected(item)) {
                // First listener to claim the item selection, gets it
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void addScreenListener(OnScreenListener listener) {
        mScreenListeners.add(listener);
    }

    @Override
    public void removeScreenListener(OnScreenListener listener) {
        mScreenListeners.remove(listener);
    }

    @Override
    public void addMenuItemListener(OnMenuItemListener listener) {
        mMenuItemListeners.add(listener);
    }

    @Override
    public void removeMenuItemListener(OnMenuItemListener listener) {
        mMenuItemListeners.remove(listener);
    }

    @Override
    public boolean isFragmentFullScreen(Fragment fragment) {
        if (mViewPager == null || mAdapter == null || mAdapter.getCount() == 0) {
            return mFullScreen;
        }
        return mFullScreen || (mViewPager.getCurrentItem() != mAdapter.getItemPosition(fragment));
    }

    @Override
    public boolean isShowPhotoOnly() {
        return mShowPhotoOnly;
    }

    @Override
    public void toggleFullScreen() {
        setFullScreen(!mFullScreen, true);
    }

    @Override
    public void onPhotoRemoved(long photoId) {
        final Cursor data = mAdapter.getCursor();
        if (data == null) {
            // Huh?! How would this happen?
            return;
        }

        final int dataCount = data.getCount();
        if (dataCount <= 1) {
            // The last photo was removed ... finish the activity & go to photos-home
//            final Intent intent = Intents.getPhotosHomeIntent(this, mAccount, mAccount.getGaiaId());
//
//            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//            startActivity(intent);
            finish();
            return;
        }

        getSupportLoaderManager().restartLoader(LOADER_PHOTO_LIST, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == LOADER_PHOTO_LIST) {
            mFragmentIsLoading = true;
            return new PhotoPagerLoader(this, Uri.parse(mPhotosUri), mPageHint);
        }
        return null;
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        final int id = loader.getId();
        if (id == LOADER_PHOTO_LIST) {
            if (data == null || data.getCount() == 0) {
                mIsEmpty = true;
                mFragmentIsLoading = false;
                updateView(mRootView);
            } else {
                // Cannot do this directly; need to be out of the loader
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        // We're paused; don't do anything now, we'll get re-invoked
                        // when the activity becomes active again
                        if (mIsPaused) {
                            mRestartLoader = true;
                            return;
                        }
                        mIsEmpty = false;

                        // set the selected photo; if the index is invalid, default to '0'
                        int itemIndex = mPhotoIndex;
//                            if (itemIndex < 0 && mPhotoRef != null) {
//                                itemIndex = getCursorPosition(data, mPhotoRef);
//                            }

                        // Use an index of 0 if the index wasn't specified or couldn't be found
                        if (itemIndex < 0) {
                            itemIndex = 0;
                        }

                        mAdapter.setPageable((Pageable) loader);
                        mAdapter.swapCursor(data);
                        updateView(mRootView);
                        mViewPager.setCurrentItem(itemIndex, false);
                    }
                });
            }
            /** Loads the album name, if necessary */
            final boolean needName = TextUtils.isEmpty(mAlbumName);
            if (!needName) {
                // At least show the album name if we have it
                updateTitleAndSubtitle();
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        setFullScreen(mFullScreen || mViewScrolling, true);
        setViewActivated();
        updateTitleAndSubtitle();
        mPhotoIndex = position;
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        mViewScrolling = (state != ViewPager.SCROLL_STATE_IDLE);
    }

    @Override
    public void onPageActivated(Fragment fragment) {
        setViewActivated();
    }

    @Override
    public boolean isFragmentActive(Fragment fragment) {
        if (mViewPager == null || mAdapter == null) {
            return false;
        }
        return mViewPager.getCurrentItem() == mAdapter.getItemPosition(fragment);
    }

    @Override
    public void onFragmentVisible(Fragment fragment) {
        if (mViewPager == null || mAdapter == null) {
            return;
        }
        if (mViewPager.getCurrentItem() == mAdapter.getItemPosition(fragment)) {
            mFragmentIsLoading = false;
        }
        updateView(mRootView);
    }

    @Override
    public void updateMenuItems() {
        if (Build.VERSION.SDK_INT >= 11) {
            // Invalidate the options menu
            invalidateOptionsMenu();
        } else {
            // Set the title bar buttons
            createTitlebarButtons(R.menu.photo_view_menu);
        }
    }

    @Override
    public InterceptType onTouchIntercept(float origX, float origY) {
        boolean interceptLeft = false;
        boolean interceptRight = false;

        for (OnScreenListener listener : mScreenListeners) {
            if (!interceptLeft) {
                interceptLeft = listener.onInterceptMoveLeft(origX, origY);
            }
            if (!interceptRight) {
                interceptRight = listener.onInterceptMoveRight(origX, origY);
            }
            listener.onViewActivated();
        }

        if (interceptLeft) {
            if (interceptRight) {
                return InterceptType.BOTH;
            }
            return InterceptType.LEFT;
        } else if (interceptRight) {
            return InterceptType.RIGHT;
        }
        return InterceptType.NONE;
    }

    /**
     * Updates the title bar according to the value of {@link #mFullScreen}.
     */
    private void setFullScreen(boolean fullScreen, boolean animate) {
        final boolean fullScreenChanged = (fullScreen != mFullScreen);
        mFullScreen = fullScreen;

        if (Build.VERSION.SDK_INT < 11) {
            if (mFullScreen) {
                hideTitlebar(animate);
            } else {
                showTitlebar(animate, true);
            }
        } else {
            ActionBar actionBar = getActionBar();
            if (mFullScreen) {
                actionBar.hide();
            } else {
                // Workaround alert!
                // Set a callback to listen for when the action bar is set, so
                // that we can get its height and pass it along to all the
                // adapters.
                if (Build.VERSION.SDK_INT >= 11 && mActionBarHeight == 0) {
                    final ViewTreeObserver observer = mRootView.getViewTreeObserver();
                    mActionBarLayoutListener = new ActionBarLayoutListener();
                    observer.addOnGlobalLayoutListener(mActionBarLayoutListener);
                }
                // Workaround alert!

                actionBar.show();
            }
        }

        if (fullScreenChanged) {
            for (OnScreenListener listener : mScreenListeners) {
                listener.onFullScreenChanged(mFullScreen, animate);
            }
        }
    }

    /**
     * Updates the title bar according to the value of {@link #mFullScreen}.
     */
    private void setViewActivated() {
        for (OnScreenListener listener : mScreenListeners) {
            listener.onViewActivated();
        }
    }

    /**
     * Updates the view to show the correct content. If album data is available, show the album
     * list. Otherwise, show either progress or no album view.
     */
    private void updateView(View view) {
        if (view == null) {
            return;
        }

        if (mFragmentIsLoading || (mAdapter.getCursor() == null && !mIsEmpty)) {
            showEmptyViewProgress(view);
        } else {
            if (!mIsEmpty) {
                showContent(view);
            } else {
                showEmptyView(view, getResources().getString(R.string.camera_photo_error));
            }
        }
    }

//    /**
//     * Returns the index of the given photo ID within the cursor data.
//     * If the ID is not found, return {@code -1}.
//     */
//    private int getCursorPosition(Cursor data, MediaRef photoRef) {
//        int cursorPosition = -1;
//        final long photoId = photoRef.getPhotoId();
//        final Uri localUri = photoRef.getLocalUri();
//        final String localUrl = (localUri == null) ? null : localUri.toString();
//
//        data.moveToPosition(-1);
//        // Prefer local photos over remote photos
//        if (!TextUtils.isEmpty(localUrl)) {
//            while (data.moveToNext()) {
//                String cursorLocalUrl = data.getString(PhotoQuery.INDEX_URL);
//                if (localUrl.equals(cursorLocalUrl)) {
//                    cursorPosition = data.getPosition();
//                    break;
//                }
//            }
//        } else if (photoId != 0L) {
//            while (data.moveToNext()) {
//                long cursorPhotoId = data.getLong(PhotoQuery.INDEX_PHOTO_ID);
//                if (photoId == cursorPhotoId) {
//                    cursorPosition = data.getPosition();
//                    break;
//                }
//            }
//        }
//        return cursorPosition;
//    }

    /**
     * Display loading progress
     *
     * @param view The layout view
     */
    private void showEmptyViewProgress(View view) {
        view.findViewById(R.id.photo_activity_empty_text).setVisibility(View.GONE);
        view.findViewById(R.id.photo_activity_empty_progress).setVisibility(View.VISIBLE);
        view.findViewById(R.id.photo_activity_empty).setVisibility(View.VISIBLE);
    }

    /**
     * Show only the empty view
     *
     * @param view The layout view
     */
    private void showEmptyView(View view, CharSequence emptyText) {
        view.findViewById(R.id.photo_activity_empty_progress).setVisibility(View.GONE);
        final TextView etv = (TextView) view.findViewById(R.id.photo_activity_empty_text);
        etv.setText(emptyText);
        etv.setVisibility(View.VISIBLE);
        view.findViewById(R.id.photo_activity_empty).setVisibility(View.VISIBLE);
    }

    /**
     * Hide the empty view and show the content
     *
     * @param view The layout view
     */
    private void showContent(View view) {
        view.findViewById(R.id.photo_activity_empty).setVisibility(View.GONE);
    }

    /**
     * Adjusts the activity title and subtitle to reflect the circle name and count.
     */
    private void updateTitleAndSubtitle() {
        final int position = mViewPager.getCurrentItem() + 1;
        final String subtitle;
        final boolean hasAlbumCount = mAlbumCount >= 0;

        if (mIsEmpty || !hasAlbumCount || position <= 0) {
            subtitle = null;
        } else {
            subtitle = getResources().getString(R.string.photo_view_count, position, mAlbumCount);
        }

        if (Build.VERSION.SDK_INT >= 11) {
            final ActionBar actionBar = getActionBar();

            actionBar.setTitle(mAlbumName);
            actionBar.setSubtitle(subtitle);
        } else {
//            setTitlebarTitle(mAlbumName);
//            setTitlebarSubtitle(subtitle);
//            createTitlebarButtons(R.menu.photo_view_menu);
        }
    }

    /**
     * @return The action bar height.
     */
    @Override
    public int getActionBarHeight() {
        return mActionBarHeight;
    }

    /**
     * Clears the layout listener and removes any reference to it.
     */
    private void clearListener() {
        if (mRootView != null) {
            mRootView.getViewTreeObserver().removeGlobalOnLayoutListener(mActionBarLayoutListener);
        }
        mActionBarLayoutListener = null;
    }

    /**
     * Listener to handle dialog button clicks for the retry dialog.
     */
    class RetryDialogListener implements DialogInterface.OnClickListener {
        /** The tag of the fragment this dialog is opened for */
        final String mTag;

        public RetryDialogListener(String tag) {
            mTag = tag;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE: {
                    final PhotoViewFragment fragment =
                            (PhotoViewFragment) getSupportFragmentManager().findFragmentByTag(mTag);
                    if (fragment != null) {
                        fragment.downloadPhoto(PhotoViewActivity.this, false);
                    }
                    break;
                }

                case DialogInterface.BUTTON_NEGATIVE: {
                    break;
                }
            }
            dialog.dismiss();
        }
    }

    /**
     * Layout listener whose sole purpose is to determine when the Action Bar is laid out.
     */
    class ActionBarLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
        @Override
        public void onGlobalLayout() {
            final ActionBar ab = getActionBar();
            final int abHeight = ab.getHeight();
            if (ab.isShowing() && abHeight > 0) {
                mActionBarHeight = abHeight;

                for (OnScreenListener listener : mScreenListeners) {
                    listener.onActionBarHeightCalculated(abHeight);
                }

                // The action bar has been laid out; no need to listen to layout changes any more
                clearListener();
            }
        }
    }
}
