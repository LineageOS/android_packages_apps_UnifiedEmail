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

package com.android.mail.photo.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.DisplayMetrics;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.mail.R;
import com.android.mail.photo.Intents;
import com.android.mail.photo.MultiChoiceActionModeStub;
import com.android.mail.photo.PhotoViewActivity.OnScreenListener;
import com.android.mail.photo.loaders.PhotoBitmapLoader;
import com.android.mail.photo.util.ImageUtils;
import com.android.mail.photo.views.PhotoView;

/**
 * Displays photo, comments and tags for a picasa photo id.
 */
public class PhotoViewFragment extends BaseFragment implements
        LoaderCallbacks<Bitmap>, OnClickListener, OnScreenListener {

    /**
     * Interface that activities must implement in order to use this fragment.
     */
    public static interface PhotoViewCallbacks {
        /**
         * Returns true of the given fragment is the currently active fragment.
         */
        public boolean isFragmentActive(Fragment fragment);

        /**
         * Called when the given fragment becomes visible.
         */
        public void onFragmentVisible(Fragment fragment);

        /**
         * Toggles full screen mode.
         */
        public void toggleFullScreen();

        /**
         * Returns {@code true} if full screen mode is enabled for the given fragment.
         * Otherwise, {@code false}.
         */
        public boolean isFragmentFullScreen(Fragment fragment);

        /**
         * Returns {@code true} if only the photo should be displayed. All ancillary
         * information [eg album name, photo owner, comment counts, etc...] will be hidden.
         */
        public boolean isShowPhotoOnly();

        /**
         * Adds a full screen listener.
         */
        public void addScreenListener(OnScreenListener listener);

        /**
         * Removes a full screen listener.
         */
        public void removeScreenListener(OnScreenListener listener);

        /**
         * A photo has been deleted.
         */
        public void onPhotoRemoved(long photoId);

        /**
         * Get the action bar height.
         */
        public int getActionBarHeight();
    }

    /**
     * Interface for components that are internally scrollable left-to-right.
     */
    public static interface HorizontallyScrollable {
        /**
         * Return {@code true} if the component needs to receive right-to-left
         * touch movements.
         *
         * @param origX the raw x coordinate of the initial touch
         * @param origY the raw y coordinate of the initial touch
         */

        public boolean interceptMoveLeft(float origX, float origY);

        /**
         * Return {@code true} if the component needs to receive left-to-right
         * touch movements.
         *
         * @param origX the raw x coordinate of the initial touch
         * @param origY the raw y coordinate of the initial touch
         */
        public boolean interceptMoveRight(float origX, float origY);
    }

    private final static String STATE_INTENT_KEY =
            "com.android.mail.photo.fragments.PhotoViewFragment.INTENT";
    private final static String STATE_FRAGMENT_ID_KEY =
            "com.android.mail.photo.fragments.PhotoViewFragment.FRAGMENT_ID";
    private final static String STATE_FORCE_LOAD_KEY =
            "com.android.mail.photo.fragments.PhotoViewFragment.FORCE_LOAD";
    private final static String STATE_DOWNLOADABLE_KEY =
            "com.android.mail.photo.fragments.PhotoViewFragment.DOWNLOADABLE";

    // Loader IDs
    private final static int LOADER_ID_PHOTO = R.id.photo_view_photo_loader_id;

    /** The size of the photo */
    public static Integer sPhotoSize;

    /** The URL of a photo to display */
    private String mResolvedPhotoUri;
    /** Whether or not this photo can be downloaded */
    private Boolean mDownloadable;
    /** The intent we were launched with */
    private Intent mIntent;
    private PhotoViewCallbacks mCallback;
    private ProgressBar mProgressBarView;
    /** If {@code true}, we will load photo data from the network instead of the database */
    private Long mForceLoadId;
    /** The ID of this fragment. {@code -1} is a special value meaning no ID. */
    private int mFragmentId = -1;
    private MultiChoiceActionModeStub mActionMode;
    /** Whether or not the photo is a place holder */
    private boolean mIsPlaceHolder = true;

    private PhotoView mPhotoView;

    /** Whether or not the fragment should make the photo full-screen */
    private boolean mFullScreen;

    public PhotoViewFragment() {
    }

    public PhotoViewFragment(Intent intent, int fragmentId) {
        this();
        mIntent = intent;
        mFragmentId = fragmentId;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof PhotoViewCallbacks) {
            mCallback = (PhotoViewCallbacks) activity;
        } else {
            throw new IllegalArgumentException("Activity must implement PhotoViewCallbacks");
        }

        if (sPhotoSize == null) {
            final DisplayMetrics metrics = new DisplayMetrics();
            final WindowManager wm =
                    (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
            final ImageUtils.ImageSize imageSize = ImageUtils.sUseImageSize;
            wm.getDefaultDisplay().getMetrics(metrics);
            switch (imageSize) {
                case EXTRA_SMALL: {
                    // Use a photo that's 80% of the "small" size
                    sPhotoSize = (Math.min(metrics.heightPixels, metrics.widthPixels) * 800) / 1000;
                    break;
                }

                case SMALL:
                case NORMAL:
                default: {
                    sPhotoSize = Math.min(metrics.heightPixels, metrics.widthPixels);
                    break;
                }
            }
        }
    }

    @Override
    public void onDetach() {
        mCallback = null;
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mIntent = new Intent().putExtras(savedInstanceState.getBundle(STATE_INTENT_KEY));
            mFragmentId = savedInstanceState.getInt(STATE_FRAGMENT_ID_KEY);
            if (savedInstanceState.containsKey(STATE_FORCE_LOAD_KEY)) {
                mForceLoadId = savedInstanceState.getLong(STATE_FORCE_LOAD_KEY);
            }
            if (savedInstanceState.containsKey(STATE_DOWNLOADABLE_KEY)) {
                mDownloadable = savedInstanceState.getBoolean(STATE_DOWNLOADABLE_KEY);
            }
        } else {
            if (mIntent.hasExtra(Intents.EXTRA_REFRESH)) {
                mForceLoadId = mIntent.getLongExtra(Intents.EXTRA_REFRESH, 0L);
            }
        }

        mResolvedPhotoUri = mIntent.getStringExtra(Intents.EXTRA_RESOLVED_PHOTO_URI);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState,
                R.layout.photo_fragment_view);

        mPhotoView = (PhotoView) view.findViewById(R.id.photo_view);

        mIsPlaceHolder = true;
        mPhotoView.setPhotoLoading(true);

        mPhotoView.setOnClickListener(this);
        mPhotoView.setFullScreen(mFullScreen, false);
//        mPhotoView.setVideoBlob(videoData);

        // Don't call until we've setup the entire view
        setViewVisibility();

        return view;
    }

    @Override
    public void onResume() {
        mCallback.addScreenListener(this);

        getLoaderManager().initLoader(LOADER_ID_PHOTO, null, this);

        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Remove listeners
        mCallback.removeScreenListener(this);
        resetPhotoView();
    }

    @Override
    public void onDestroyView() {
        // Clean up views and other components
        mProgressBarView = null;
        mIsPlaceHolder = true;

        if (mPhotoView != null) {
            mPhotoView.clear();
            mPhotoView = null;
        }

        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mIntent != null) {
            outState.putParcelable(STATE_INTENT_KEY, mIntent.getExtras());
            outState.putInt(STATE_FRAGMENT_ID_KEY, mFragmentId);
            if (mForceLoadId != null) {
                outState.putLong(STATE_FORCE_LOAD_KEY, mForceLoadId);
            }
            if (mDownloadable != null) {
                outState.putBoolean(STATE_DOWNLOADABLE_KEY, mDownloadable);
            }
        }
    }

    @Override
    public Loader<Bitmap> onCreateLoader(int id, Bundle args) {
        if (id == LOADER_ID_PHOTO) {
            return new PhotoBitmapLoader(getActivity(), mResolvedPhotoUri);
        } else {
            return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Bitmap> loader, Bitmap data) {
        // If we don't have a view, the fragment has been paused. We'll get the cursor again later.
        if (getView() == null) {
            return;
        }

        final int id = loader.getId();
        if (id == LOADER_ID_PHOTO) {
            if (data == null) {
                Toast.makeText(getActivity(), R.string.photo_view_load_error, Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            final View view = getView();
            if (view != null) {
                bindPhoto(data);
                updateView(view);
            }
            mForceLoadId = null;
            //mAdapter.swapCursor(data);
            mIsPlaceHolder = false;
            if (Build.VERSION.SDK_INT >= 11 && mActionMode != null) {
                // Invalidate the action mode menu
                mActionMode.invalidate();
            }
            setViewVisibility();
        }
    }

    /**
     * Binds an image to the photo view.
     */
    private void bindPhoto(Bitmap bitmap) {
        if (mPhotoView != null) {
            mPhotoView.setPhotoLoading(false);
            mPhotoView.bindPhoto(bitmap);
        }
    }

    /**
     * Resets the photo view to it's default state w/ no bound photo.
     */
    private void resetPhotoView() {
        if (mPhotoView != null) {
            mPhotoView.setPhotoLoading(true);
            mPhotoView.bindPhoto(null);
        }
    }

    @Override
    public void onLoaderReset(Loader<Bitmap> loader) {
        // Do nothing
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            default: {
                if (!isPhotoBound()) {
                    // If there is no photo, don't allow any actions except to exit
                    // full-screen mode. We want to let the user view comments, etc...
                    if (mCallback.isFragmentFullScreen(this)) {
                        mCallback.toggleFullScreen();
                    }
                    break;
                }

                // TODO: enable video
                if (isVideo() && mCallback.isFragmentFullScreen(this)) {
                    if (isVideoReady()) {
//                        final Intent startIntent = Intents.getVideoViewActivityIntent(getActivity(),
//                                mAccount, mOwnerId, mPhotoId, mAdapter.getVideoData());
//                        startActivity(startIntent);
                    } else {
                        final String toastText = getString(R.string.photo_view_video_not_ready);
                        Toast.makeText(getActivity(), toastText, Toast.LENGTH_LONG).show();
                    }
                } else {
                    mCallback.toggleFullScreen();
                }
                break;
            }
        }
    }

    @Override
    public void onFullScreenChanged(boolean fullScreen, boolean animate) {
        setViewVisibility();
    }

    @Override
    public void onViewActivated() {
        if (!mCallback.isFragmentActive(this)) {
            // we're not in the foreground; reset our view
            resetViews();
        } else {
            mCallback.onFragmentVisible(this);
        }
    }

    /**
     * Reset the views to their default states
     */
    public void resetViews() {
        if (mPhotoView != null) {
            mPhotoView.resetTransformations();
        }
    }

    @Override
    public boolean onInterceptMoveLeft(float origX, float origY) {
        if (!mCallback.isFragmentActive(this)) {
            // we're not in the foreground; don't intercept any touches
            return false;
        }

        return (mPhotoView != null && mPhotoView.interceptMoveLeft(origX, origY));
    }

    @Override
    public boolean onInterceptMoveRight(float origX, float origY) {
        if (!mCallback.isFragmentActive(this)) {
            // we're not in the foreground; don't intercept any touches
            return false;
        }

        return (mPhotoView != null && mPhotoView.interceptMoveRight(origX, origY));
    }

    @Override
    public void onActionBarHeightCalculated(int actionBarHeight) {
    }

    @Override
    protected boolean isEmpty() {
        final View view = getView();
        final boolean isViewAvailable =
                (view != null && (view.findViewById(android.R.id.empty) != null));

        return isViewAvailable && !isPhotoBound();
    }

    /**
     * Returns {@code true} if a photo has been bound. Otherwise, returns {@code false}.
     */
    public boolean isPhotoBound() {
        return (mPhotoView != null && mPhotoView.isPhotoBound());
    }

    /**
     * Returns {@code true} if a photo is loading. Otherwise, returns {@code false}.
     */
    public boolean isPhotoLoading() {
        return (mPhotoView != null && mPhotoView.isPhotoLoading());
    }

    /**
     * Returns {@code true} if the photo represents a video. Otherwise, returns {@code false}.
     */
    public boolean isVideo() {
        return (mPhotoView != null && mPhotoView.isVideo());
    }

    /**
     * Returns {@code true} if the video is ready to play. Otherwise, returns {@code false}.
     */
    public boolean isVideoReady() {
        return (mPhotoView != null && mPhotoView.isVideoReady());
    }

    /**
     * Returns video data for the photo. Otherwise, {@code null} if the photo is not a video.
     */
    public byte[] getVideoData() {
        return (mPhotoView == null ? null : mPhotoView.getVideoData());
    }

    /**
     * Sets the progress bar.
     */
    @Override
    public void onUpdateProgressView(ProgressBar progressBarView) {
        mProgressBarView = progressBarView;
        updateSpinner(mProgressBarView);

        final View myView = getView();
        if (myView != null) {
            updateView(myView);
        }
    }

    /**
     * Sets view visibility depending upon whether or not we're in "full screen" mode.
     *
     * @param animate If {@code true}, animate views in/out. Otherwise, snap views.
     */
    private void setViewVisibility() {
        final boolean fullScreen = mCallback.isFragmentFullScreen(this);
        final boolean hide = fullScreen;

        setFullScreen(hide);
    }

    /**
     * Sets full-screen mode for the views.
     */
    public void setFullScreen(boolean fullScreen) {
        mFullScreen = fullScreen;
        mPhotoView.enableImageTransforms(true);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (!mCallback.isFragmentActive(this)) {
            return false;
        }

        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            return false;
        }

        // Ignore the header view long click
        if (info.position == 0) {
            return false;
        }

        return false;
    }

    /**
     * Updates the view to show the correct content. If the view is null or does not contain
     * the special id {@link android.R.id#empty}, performs no action.
     */
    private void updateView(View view) {
        if (view == null || (view.findViewById(android.R.id.empty) == null)) {
            return;
        }

        final boolean hasImage = isPhotoBound();
        final boolean imageLoading = isPhotoLoading();

        if (imageLoading) {
            showEmptyViewProgress(view);
        } else {
            if (hasImage) {
                showContent(view);
            } else if (mIsPlaceHolder) {
                setupEmptyView(view, R.string.photo_view_placeholder_image);
                showEmptyView(view);
            } else {
                setupEmptyView(view, R.string.photo_network_error);
                showEmptyView(view);
            }
        }
        updateSpinner(mProgressBarView);
    }
}
