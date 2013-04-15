package com.android.mail.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.FrameLayout;

import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

/**
 * Conversation list view contains a {@link SwipeableListView} and a sync status bar above it.
 */
public class ConversationListView extends FrameLayout implements SwipeableListView.SwipeListener {

    private static final int DISTANCE_TO_TRIGGER_SYNC = 200; // dp
    private static final int DISTANCE_TO_IGNORE = 15; // dp
    private static final int VELOCITY_THRESHOLD_TO_TRIGGER_SYNC = 1000;
    private static final int DISTANCE_TO_TRIGGER_CANCEL = 10; // dp

    private static final String LOG_TAG = LogTag.getLogTag();

    private View mSyncTriggerBar;
    private View mSyncProgressBar;
    private SwipeableListView mListView;

    // Whether to ignore events in {#dispatchTouchEvent}.
    private boolean mIgnoreTouchEvents = false;

    private VelocityTracker mVelocityTracker;
    private boolean mTrackingScrollMovement = false;
    // Y coordinate of where scroll started
    private float mTrackingScrollStartY;
    // Max Y coordinate reached since starting scroll, this is used to know whether
    // user moved back up which should cancel the current tracking state and hide the
    // sync trigger bar.
    private float mTrackingScrollMaxY;
    private boolean mIsSyncing = false;

    private FolderController mFolderController = null;

    private float mDensity;

    // Instantiated through view inflation
    @SuppressWarnings("unused")
    public ConversationListView(Context context) {
        this(context, null);
    }

    public ConversationListView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public ConversationListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, -1);
        mVelocityTracker = VelocityTracker.obtain();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSyncTriggerBar = findViewById(R.id.sync_trigger);
        mSyncProgressBar = findViewById(R.id.progress);
        mListView = (SwipeableListView) findViewById(android.R.id.list);
        mListView.setSwipeListener(this);
        mDensity = getResources().getDisplayMetrics().density;
    }

    protected void setFolderController(FolderController folderController) {
        mFolderController = folderController;
    }

    @Override
    public void onBeginSwipe() {
        mIgnoreTouchEvents = true;
        if (mTrackingScrollMovement) {
            cancelMovementTracking();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // First check for any events that can trigger end of a swipe, so we can reset
        // mIgnoreTouchEvents back to false (it can only be set to true at beginning of swipe)
        // via {#onBeginSwipe()} callback.
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIgnoreTouchEvents = false;
        }

        if (mIgnoreTouchEvents) {
            return super.dispatchTouchEvent(event);
        }

        float y = event.getY(0);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mIsSyncing) {
                    break;
                }
                // Only if we have reached the top of the list, any further scrolling
                // can potentially trigger a sync.
                if (mListView.getChildCount() == 0 || mListView.getChildAt(0).getTop() == 0) {
                    mTrackingScrollMovement = true;
                    mVelocityTracker.clear();
                    mVelocityTracker.addMovement(event);
                    mTrackingScrollStartY = y;
                    mTrackingScrollMaxY = mTrackingScrollStartY;

                    mSyncTriggerBar.setScaleX(0f);
                    mSyncTriggerBar.setVisibility(VISIBLE);
                    mSyncTriggerBar.setAlpha(1f);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTrackingScrollMovement) {
                    // Sync can be triggered in 2 ways.
                    // 1. Velocity goes over a threshold (quick swipe)
                    /* Temporarily disable velocity trigger and only use distance to see
                       how this feels.
                    mVelocityTracker.addMovement(event);
                    mVelocityTracker.computeCurrentVelocity(1000);
                    float yVelocityDip = mVelocityTracker.getYVelocity() / mDensity;
                    if (yVelocityDip > VELOCITY_THRESHOLD_TO_TRIGGER_SYNC) {
                        LogUtils.i(LOG_TAG, "Sync triggered from velocity %f", yVelocityDip);
                        triggerSync();
                        break;
                    }
                    */

                    // 2. Tap and drag distance goes over a certain threshold
                    float verticalDistancePx = y - mTrackingScrollStartY;
                    float verticalDistanceDp = verticalDistancePx / mDensity;
                    if (verticalDistanceDp > DISTANCE_TO_TRIGGER_SYNC) {
                        LogUtils.i(LOG_TAG, "Sync triggered from distance");
                        triggerSync();
                        break;
                    }

                    // Moving back up vertically should be handled the same as CANCEL / UP:
                    float verticalDistanceFromMaxPx = mTrackingScrollMaxY - y;
                    float verticalDistanceFromMaxDp = verticalDistanceFromMaxPx / mDensity;
                    if (verticalDistanceFromMaxDp > DISTANCE_TO_TRIGGER_CANCEL) {
                        cancelMovementTracking();
                        break;
                    }

                    // Otherwise hint how much further user needs to drag to trigger sync by
                    // expanding the sync status bar proportional to how far they have dragged.
                    if (verticalDistanceDp < DISTANCE_TO_IGNORE) {
                        // Ignore small movements such as tap
                        verticalDistanceDp = 0;
                    }
                    mSyncTriggerBar.setScaleX(verticalDistanceDp/DISTANCE_TO_TRIGGER_SYNC);

                    if (y > mTrackingScrollMaxY) {
                        mTrackingScrollMaxY = y;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mTrackingScrollMovement) {
                    cancelMovementTracking();
                }
                break;
        }

        return super.dispatchTouchEvent(event);
    }

    private void cancelMovementTracking() {
        if (mTrackingScrollMovement) {
            // Fade out the status bar when user lifts finger and no sync has happened yet
            mSyncTriggerBar.animate().alpha(0f).setDuration(200).start();
        }
        mTrackingScrollMovement = false;
    }

    private void triggerSync() {
        // Show trigger bar expand to full width
        mSyncTriggerBar.animate().scaleX(1).setDuration(200).start();

        // This will call back to showSyncStatusBar():
        mFolderController.requestFolderRefresh();

        // Any continued dragging after this should have no effect
        mTrackingScrollMovement = false;
    }

    protected void showSyncStatusBar() {
        mIsSyncing = true;

        LogUtils.i(LOG_TAG, "ConversationListView show sync status bar");
        mSyncTriggerBar.animate().alpha(0f).setDuration(200).start();

        mSyncProgressBar.setVisibility(VISIBLE);
        mSyncProgressBar.setAlpha(0f);
        mSyncProgressBar.animate().alpha(1f).setDuration(200).start();
    }

    protected void onSyncFinished() {
        // onSyncFinished() can get called several times as result of folder updates that maybe
        // or may not be related to sync.
        if (mIsSyncing) {
            LogUtils.i(LOG_TAG, "ConversationListView hide sync status bar");
            // Hide both the sync progress bar and sync trigger bar
            mSyncProgressBar.animate().alpha(0f).setDuration(200).start();
            mSyncTriggerBar.setVisibility(GONE);
            mIsSyncing = false;
        }
    }
}
