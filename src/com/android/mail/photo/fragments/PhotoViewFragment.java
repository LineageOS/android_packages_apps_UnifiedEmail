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
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.mail.R;
import com.android.mail.photo.BaseFragmentActivity;
import com.android.mail.photo.Intents;
import com.android.mail.photo.MultiChoiceActionModeStub;
import com.android.mail.photo.PhotoViewActivity.OnMenuItemListener;
import com.android.mail.photo.PhotoViewActivity.OnScreenListener;
import com.android.mail.photo.loaders.PhotoBitmapLoader;
import com.android.mail.photo.util.ImageUtils;
import com.android.mail.photo.views.PhotoLayout;
import com.android.mail.photo.views.PhotoView;

import java.io.File;

/**
 * Displays photo, comments and tags for a picasa photo id.
 */
public class PhotoViewFragment extends BaseFragment implements
        LoaderCallbacks<Bitmap>, OnClickListener, OnScreenListener, OnMenuItemListener {

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
         * Adds a title bar listener.
         */
        public void addMenuItemListener(OnMenuItemListener listener);

        /**
         * Removes a title bar listener.
         */
        public void removeMenuItemListener(OnMenuItemListener listener);

        /**
         * A photo has been deleted.
         */
        public void onPhotoRemoved(long photoId);

        /**
         * Get the action bar height.
         */
        public int getActionBarHeight();

        /**
         * Updates the title bar menu.
         */
        public void updateMenuItems();
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

    private final static String TAG = "PhotoViewFragment";

    /** An invalid ID */
    private final static long INVALID_ID = 0L;

    // Loader IDs
    private final static int LOADER_ID_PHOTO = R.id.photo_view_photo_loader_id;

    /** The size of the photo */
    public static Integer sPhotoSize;

    /** The ID of this photo */
    private long mPhotoId;
    /** The gaia ID of the photo owner */
    private String mOwnerId;
    /** The URL of a photo to display */
    private String mPhotoUrl;
    /** Name of the photo */
    private String mDisplayName;
    /** Album name used if the photo doesn't have one. See b/5678229. */
    private String mDefaultAlbumName;
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

    private PhotoLayout mPhotoLayout;
    private PhotoView mPhotoView;

    /** The height of the action bar; may be {@code 0} if there is no action bar available */
    private int mActionBarHeight;
    /** When {@code true}, don't use a spacer */
    private boolean mDisableSpacer = Build.VERSION.SDK_INT < 11;
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

        mPhotoId = mIntent.getLongExtra(Intents.EXTRA_PHOTO_ID, INVALID_ID);
        mOwnerId = mIntent.getStringExtra(Intents.EXTRA_OWNER_ID);
        mPhotoUrl = mIntent.getStringExtra(Intents.EXTRA_PHOTO_URL);
        mDefaultAlbumName = mIntent.getStringExtra(Intents.EXTRA_ALBUM_NAME);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState,
                R.layout.photo_fragment_view);

        mPhotoLayout = (PhotoLayout) view.findViewById(R.id.photo_layout);
        mPhotoView = (PhotoView) view.findViewById(R.id.photo_view);

        mIsPlaceHolder = true;
        mPhotoView.setPhotoLoading(true);

        // Bind the photo data
        setPhotoLayoutFixedHeight();

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
        mCallback.addMenuItemListener(this);

        // the forceLoad call feels like a hack
        getLoaderManager().initLoader(LOADER_ID_PHOTO, null, this);

        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Remove listeners
        mCallback.removeScreenListener(this);
        mCallback.removeMenuItemListener(this);
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

        if (mPhotoLayout != null) {
            mPhotoLayout.clear();
            mPhotoLayout = null;
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
            return new PhotoBitmapLoader(getActivity(), mPhotoUrl);
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
            updateMenuItems();
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
            // The action bar will already be updated for HC and later and updating them
            // here will corrupt the display.
            if (Build.VERSION.SDK_INT < 11) {
                updateMenuItems();
            }
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
        final boolean heightChanged = (actionBarHeight != mActionBarHeight);
        mActionBarHeight = actionBarHeight;
        if (heightChanged && mActionBarHeight > 0) {
            setPhotoLayoutFixedHeight();
        }
    }

    private void setPhotoLayoutFixedHeight() {
        if (mPhotoLayout != null) {
            ViewParent viewParent = mPhotoLayout.getParent();
            if (viewParent instanceof View) {
                mPhotoLayout.setFixedHeight(
                        ((View) mPhotoLayout.getParent()).getMeasuredHeight() -
                        (mDisableSpacer ? 0 : mActionBarHeight));
            }
        }
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
     * Returns {@code true} if the user is allowed to download the photo.
     * Otherwise, {@code false}.
     */
    private boolean canDownload() {
        return mDownloadable != null && mDownloadable;
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

    @Override
    public boolean onPrepareTitlebarButtons(Menu menu) {
        if (!mCallback.isFragmentActive(this)) {
            return false;
        }

//        final Uri photoUri = (mPhotoUrl != null) ? Uri.parse(mPhotoUrl) : null;
//        final boolean isRemotePhoto =
//                (mPhotoId != INVALID_ID) && !MediaStoreUtils.isMediaStoreUri(photoUri);
//        final boolean myPhoto = //(mAccount.isMyGaiaId(mOwnerId)) ||
//                (mOwnerId == null && MediaStoreUtils.isMediaStoreUri(photoUri));
//        final boolean onlyPhotoUrl = (mPhotoId == INVALID_ID) && photoUri != null;
//        final boolean allowDownload = onlyPhotoUrl || (isRemotePhoto && (myPhoto || canDownload()));

//        if (hasPlusOned()) {
//            setVisible(menu, R.id.remove_plus1, true);
//            setVisible(menu, R.id.plus1, false);
//        } else {
//            setVisible(menu, R.id.remove_plus1, false);
//            setVisible(menu, R.id.plus1, mAllowPlusOne && isRemotePhoto);
//        }
//        setVisible(menu, R.id.download_photo, allowDownload);

        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mCallback.isFragmentActive(this)) {
            return;
        }

        // some menu stuff in the action bar
//        inflater.inflate(R.menu.photo_view_menu, menu);
//        if (Build.VERSION.SDK_INT >= 11) {
//            // On SDK < 11, we cannot set the progress bar view here; the menu is only inflated
//            // after the user presses the menu button. Since we want to be able to show the
//            // progress bar at any time, we create it manually in onCreate().
//            final View barLayout =
//                    menu.findItem(R.id.action_bar_progress_spinner).getActionView();
//            final ProgressBar progressBarView =
//                    (ProgressBar) barLayout.findViewById(R.id.action_bar_progress_spinner_view);
//            onUpdateProgressView(progressBarView);
//        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (!mCallback.isFragmentActive(this)) {
            return;
        }

//        final Long shapeId = (mAdapter == null)
//                ? null : mAdapter.getMyApprovedShapeId();
//        final boolean taggedAsMe = (shapeId != null);
//        final Uri photoUri = (mPhotoUrl != null) ? Uri.parse(mPhotoUrl) : null;
//        final boolean isRemotePhoto =
//                (mPhotoId != INVALID_ID) && !MediaStoreUtils.isMediaStoreUri(photoUri);
//        final boolean onlyPhotoUrl = (mPhotoId == INVALID_ID) && photoUri != null;
//        final boolean myPhoto = (mAccount.isMyGaiaId(mOwnerId)) ||
//                (mOwnerId == null && MediaStoreUtils.isMediaStoreUri(photoUri));
//        final String photoStream = mIntent.getStringExtra(Intents.EXTRA_STREAM_ID);
//        final boolean isInstantUpload = ApiUtils.CAMERA_SYNC_STREAM_ID.equals(photoStream);
//        final boolean allowDownload = onlyPhotoUrl || (isRemotePhoto && (myPhoto || canDownload()));
//
//        if (Build.VERSION.SDK_INT < 11) {
//            setVisible(menu, R.id.remove_plus1, false);
//            setVisible(menu, R.id.plus1, false);
//        } else if (hasPlusOned()) {
//            setVisible(menu, R.id.remove_plus1, true);
//            setVisible(menu, R.id.plus1, false);
//        } else {
//            setVisible(menu, R.id.remove_plus1, false);
//            setVisible(menu, R.id.plus1, mAllowPlusOne && isRemotePhoto);
//        }
//
//        // For now, only allow sharing of a photo in the "Instant Upload" album
//        setVisible(menu, R.id.share_photo, isInstantUpload);
//
//        // Only allow deletion of your own photos & reporting of other's photos
//        setVisible(menu, R.id.set_profile_photo, myPhoto || taggedAsMe);
//        setVisible(menu, R.id.set_wallpaper_photo, myPhoto);
//        setVisible(menu, R.id.delete_photo, myPhoto);
//        setVisible(menu, R.id.download_photo, allowDownload);
//        setVisible(menu, R.id.report_photo, !myPhoto && isRemotePhoto);
//        setVisible(menu, R.id.refresh_photo, isRemotePhoto);
//        setVisible(menu, R.id.remove_tag, taggedAsMe);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mCallback.isFragmentActive(this)) {
            return false;
        }

        final Activity activity = getActivity();

        switch (item.getItemId()) {
            case android.R.id.home: {
                ((BaseFragmentActivity) activity).onTitlebarLabelClick();
                return true;
            }
        }

        return false;
//            case R.id.plus1: {
//                if (canTogglePlusOne()) {
//                    EsService.photoPlusOne(getActivity(), mAccount, mOwnerId, mAlbumId, mPhotoId,
//                            true);
//                }
//                return true;
//            }
//
//            case R.id.remove_plus1: {
//                if (canTogglePlusOne()) {
//                    EsService.photoPlusOne(getActivity(), mAccount, mOwnerId, mAlbumId, mPhotoId,
//                            false);
//                }
//                return true;
//            }
//
//            case R.id.share_photo: {
//                final Uri photoUri = (mPhotoUrl != null) ? Uri.parse(mPhotoUrl) : null;
//                final Uri localUri = MediaStoreUtils.isMediaStoreUri(photoUri) ? photoUri : null;
//                final String remoteUrl = (localUri != null) ? null : mPhotoUrl;
//                final MediaRef ref = new MediaRef(mOwnerId, mPhotoId, remoteUrl,
//                        localUri, MediaRef.MediaType.IMAGE);
//                final ArrayList<MediaRef> refList = new ArrayList<MediaRef>();
//                refList.add(ref);
//
//                final Context context = getActivity();
//                final Intent intent = Intents.getPostActivityIntent(context, mAccount, refList);
//                startActivity(intent);
//                return true;
//            }
//
//            case R.id.set_profile_photo: {
//                final Uri photoUri = (mPhotoUrl != null) ? Uri.parse(mPhotoUrl) : null;
//                final Uri localUri = MediaStoreUtils.isMediaStoreUri(photoUri) ? photoUri : null;
//                final String remoteUrl = (localUri != null) ? null : mPhotoUrl;
//                final MediaRef mediaRef = new MediaRef(mOwnerId, mPhotoId, remoteUrl,
//                        localUri, MediaRef.MediaType.IMAGE);
//                startActivityForResult(Intents.getPhotoPickerIntent(getActivity(), mAccount,
//                        mDisplayName, mediaRef, true, Intents.PICKER_DEST_PROFILE),
//                        REQUEST_PHOTO_PICKER);
//                return true;
//            }
//
//            case R.id.download_photo: {
//                downloadPhoto(activity, true);
//                return true;
//            }
//
//            case R.id.set_wallpaper_photo: {
//                showProgressDialog(OP_SET_WALLPAPER_PHOTO,
//                        getString(R.string.set_wallpaper_photo_pending));
//                new AsyncTask<Void, Void, Boolean>() {
//                    @Override
//                    protected void onPostExecute(Boolean result) {
//                        final Resources res = getResources();
//                        final String toastText;
//
//                        if (result) {
//                            toastText = res.getString(R.string.set_wallpaper_photo_success);
//                        } else {
//                            toastText = res.getString(R.string.set_wallpaper_photo_error);
//                        }
//                        Toast.makeText(activity, toastText, Toast.LENGTH_SHORT).show();
//
//                        hideProgressDialog();
//                    }
//
//                    @Override
//                    protected Boolean doInBackground(Void... params) {
//                        try {
//                            final Bitmap bitmap = mAdapter.getPhotoImage();
//                            if (bitmap != null) {
//                                final WallpaperManager manager =
//                                        WallpaperManager.getInstance(getActivity());
//                                manager.setBitmap(bitmap);
//
//                                return Boolean.TRUE;
//                            }
//                        } catch (IOException e) {
//                            Log.e(TAG, "Exception setting wallpaper", e);
//                        }
//                        return Boolean.FALSE;
//                    }
//                }.execute((Void) null);
//
//                return true;
//            }
//
//            case R.id.remove_tag: {
//                final AlertFragmentDialog dialog = AlertFragmentDialog.newInstance(
//                        getString(R.string.menu_remove_tag),
//                        getString(R.string.remove_tag_question),
//                        getString(R.string.ok),
//                        getString(R.string.cancel));
//                dialog.setTargetFragment(this, 0);
//                dialog.show(getFragmentManager(), DIALOG_TAG_REMOVE_TAG);
//                return true;
//            }
//
//            case R.id.refresh_photo: {
//                refresh();
//                return true;
//            }
//
//            case R.id.delete_photo: {
//                final Resources res = getResources();
//                final Uri photoUri = (mPhotoUrl != null) ? Uri.parse(mPhotoUrl) : null;
//                final Uri localUri =
//                        MediaStoreUtils.isMediaStoreUri(photoUri) ? photoUri : null;
//                final int messageId = localUri == null
//                        ? R.plurals.delete_remote_photo_dialog_message
//                        : R.plurals.delete_local_photo_dialog_message;
//                final AlertFragmentDialog dialog = AlertFragmentDialog.newInstance(
//                        res.getQuantityString(R.plurals.delete_photo_dialog_title, 1),
//                        res.getQuantityString(messageId, 1),
//                        res.getQuantityString(R.plurals.delete_photo, 1),
//                        getString(R.string.cancel));
//                dialog.setTargetFragment(this, 0);
//                dialog.show(getFragmentManager(), DIALOG_TAG_REMOVE_PHOTO);
//                return true;
//            }
//
//            case R.id.report_photo: {
//                final AlertFragmentDialog dialog = AlertFragmentDialog.newInstance(
//                        getString(R.string.menu_report_photo),
//                        getString(R.string.report_photo_question),
//                        getString(R.string.ok),
//                        getString(R.string.cancel));
//                dialog.setTargetFragment(this, 0);
//                dialog.show(getFragmentManager(), DIALOG_TAG_REPORT_PHOTO);
//                return true;
//            }
//
//            case R.id.settings: {
//                final Intent intent = Intents.getSettingsActivityIntent(activity, mAccount);
//                startActivity(intent);
//                return true;
//            }
//
//            case R.id.feedback: {
//                recordUserAction(Logging.Targets.Action.SETTINGS_FEEDBACK);
//                GoogleFeedback.launch(getActivity());
//                return true;
//            }
//
//            case R.id.help:
//                startExternalActivity(new Intent(Intent.ACTION_VIEW,
//                        HelpUrl.getHelpUrl(activity, HELP_LINK_PARAMETER)));
//                return true;
//
//            default: {
//                return false;
//            }
//        }
    }

    /**
     * Download the currently showing photo.
     *
     * @param context The context
     * @param fullRes If {@code true}, download the photo at max resolution. Otherwise, download
     *          the photo no larger than {@link DownloadPhotoTask#MAX_DOWNLOAD_SIZE}.
     */
    public void downloadPhoto(Context context, boolean fullRes) {
//        if (mAdapter == null) {
//            return;
//        }
//
//        final MediaRef mediaRef = mAdapter.getPhotoRef();
//        final String albumName = mAdapter.getAlbumName();
//
//        final String imageUrl;
//        if (mPhotoId == INVALID_ID) {
//            imageUrl = mPhotoUrl;
//        } else {
//            imageUrl = (mediaRef == null) ? null : mediaRef.getUrl();
//        }
//
//        // Modify the image URL to adjust the size parameters. If this is the first attempt,
//        // try to download the full image. If this is not the first attempt, cap the image
//        // size to {@link #MAX_DOWNLOAD_SIZE}.
//        final String downloadUrl;
//        if (FIFEUtil.isFifeHostedUrl(imageUrl)) {
//            if (fullRes) {
//                downloadUrl = FIFEUtil.setImageUrlOptions("d", imageUrl).toString();
//            } else {
//                downloadUrl = FIFEUtil.setImageUrlSize(REDUCED_DOWNLOAD_SIZE, imageUrl, false);
//            }
//        } else {
//            downloadUrl = ImageProxyUtil.setImageUrlSize(
//                    fullRes ? ImageProxyUtil.ORIGINAL_SIZE : REDUCED_DOWNLOAD_SIZE, imageUrl);
//        }
//
//        if (downloadUrl != null) {
//            if (EsLog.isLoggable(TAG, Log.DEBUG)) {
//                Log.d(TAG, "Downloading image from: " + downloadUrl);
//            }
//
//            mNewerReqId = EsService.savePhoto(context, mAccount, downloadUrl, fullRes, albumName);
//            showProgressDialog(OP_DOWNLOAD_PHOTO, getString(R.string.download_photo_pending));
//        } else {
//            final String toastText = getResources().getString(R.string.download_photo_error);
//            Toast.makeText(context, toastText, Toast.LENGTH_LONG).show();
//        }
    }

    /**
     * Adds the given file to the system. This makes it available through the Media Store
     * and, optionally, the Downloads application.
     *
     * @param context The context
     * @param file The file to add.
     * @param description A description of the photo.
     * @param mimeType The type of the image file.
     */
    private void addDownloadToSystem(Context context, File file,
            String description, String mimeType) {
        if (Build.VERSION.SDK_INT >= 12) {
            // Can't add a file to the Downloads application until SDK v12
            try {
                final DownloadManager dm =
                        (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                dm.addCompletedDownload(file.getName(), description, true, mimeType,
                        file.getAbsolutePath(), file.length(), false);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Could not add photo to the Downloads application", e);
            }
        }

        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.parse(file.toURI().toString()));
        context.sendBroadcast(intent);
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
        mPhotoView.enableImageTransforms(mFullScreen);
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
     * Helper function to set the visibility of a given menu item.
     */
    private void setVisible(Menu menu, int menuItemId, boolean visible) {
        MenuItem item;
        item = menu.findItem(menuItemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    /** Updates the menu items */
    private void updateMenuItems() {
        if (mCallback != null) {
            mCallback.updateMenuItems();
        }
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
