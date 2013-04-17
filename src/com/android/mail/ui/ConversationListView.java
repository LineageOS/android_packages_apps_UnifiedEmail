package com.android.mail.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

/**
 * Conversation list view contains a {@link SwipeableListView} and a sync status bar above it.
 */
public class ConversationListView extends FrameLayout implements SwipeableListView.SwipeListener {

    private static final int MIN_DISTANCE_TO_TRIGGER_SYNC = 150; // dp
    private static final int MAX_DISTANCE_TO_TRIGGER_SYNC = 300; // dp

    private static final int DISTANCE_TO_IGNORE = 15; // dp
    private static final int DISTANCE_TO_TRIGGER_CANCEL = 10; // dp
    private static final int SHOW_CHECKING_FOR_MAIL_DURATION_IN_MILLIS = 1 * 1000; // 1 seconds
    private static final int TEXT_FADE_DURATION_IN_MILLIS = 300;

    private static final String LOG_TAG = LogTag.getLogTag();

    private View mSyncTriggerBar;
    private View mSyncProgressBar;
    private SwipeableListView mListView;

    // Whether to ignore events in {#dispatchTouchEvent}.
    private boolean mIgnoreTouchEvents = false;

    private boolean mTrackingScrollMovement = false;
    // Y coordinate of where scroll started
    private float mTrackingScrollStartY;
    // Max Y coordinate reached since starting scroll, this is used to know whether
    // user moved back up which should cancel the current tracking state and hide the
    // sync trigger bar.
    private float mTrackingScrollMaxY;
    private boolean mIsSyncing = false;
    private Interpolator mInterpolator = new AccelerateInterpolator();

    private FolderController mFolderController = null;

    private float mDensity;

    private Activity mActivity;
    private WindowManager mWindowManager;
    private HintText mHintText;
    private boolean mHasHintTextViewBeenAdded = false;

    // Minimum vertical distance (in dips) of swipe to trigger a sync.
    // This value can be different based on the device.
    private float mDistanceToTriggerSyncDp = MIN_DISTANCE_TO_TRIGGER_SYNC;

    private ConversationListContext mConvListContext;

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

        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mHintText = new ConversationListView.HintText(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSyncTriggerBar = findViewById(R.id.sync_trigger);
        mSyncProgressBar = findViewById(R.id.progress);
        mListView = (SwipeableListView) findViewById(android.R.id.list);
        mListView.setSwipeListener(this);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        mDensity = displayMetrics.density;

        // Calculate distance threshold for triggering a sync based on
        // screen height.  Apply a min and max cutoff.
        float threshold = ((float) displayMetrics.heightPixels) / mDensity / 2.5f;
        mDistanceToTriggerSyncDp = Math.max(
                Math.min(threshold, MAX_DISTANCE_TO_TRIGGER_SYNC),
                MIN_DISTANCE_TO_TRIGGER_SYNC);
    }

    protected void setFolderController(FolderController folderController) {
        mFolderController = folderController;
    }

    protected void setActivity(Activity activity) {
        mActivity = activity;
    }

    protected void setConversationContext(ConversationListContext convListContext) {
        mConvListContext = convListContext;
    }

    @Override
    public void onBeginSwipe() {
        mIgnoreTouchEvents = true;
        if (mTrackingScrollMovement) {
            cancelMovementTracking();
        }
    }

    private void addHintTextViewIfNecessary() {
        if (!mHasHintTextViewBeenAdded) {
            mWindowManager.addView(mHintText, getRefreshHintTextLayoutParams());
            mHasHintTextViewBeenAdded = true;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Delayed to this step because activity has to be running in order for view to be
        // successfully added to the window manager.
        addHintTextViewIfNecessary();

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
                // Disable swipe to refresh in search results page
                if (ConversationListContext.isSearchResult(mConvListContext)) {
                    break;
                }
                // Only if we have reached the top of the list, any further scrolling
                // can potentially trigger a sync.
                if (mListView.getChildCount() == 0 || mListView.getChildAt(0).getTop() == 0) {
                    startMovementTracking(y);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTrackingScrollMovement) {
                    // Sync is triggered when tap and drag distance goes over a certain threshold
                    float verticalDistancePx = y - mTrackingScrollStartY;
                    float verticalDistanceDp = verticalDistancePx / mDensity;
                    if (verticalDistanceDp > mDistanceToTriggerSyncDp) {
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
                    } else {
                        mHintText.displaySwipeToRefresh();
                    }
                    mSyncTriggerBar.setScaleX(mInterpolator.getInterpolation(
                            verticalDistanceDp/mDistanceToTriggerSyncDp));

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

    private void startMovementTracking(float y) {
        mTrackingScrollMovement = true;
        mTrackingScrollStartY = y;
        mTrackingScrollMaxY = mTrackingScrollStartY;

        mSyncTriggerBar.setScaleX(0f);
        mSyncTriggerBar.setAlpha(1f);
        mSyncTriggerBar.setVisibility(VISIBLE);
    }

    private void cancelMovementTracking() {
        if (mTrackingScrollMovement) {
            // Fade out the status bar when user lifts finger and no sync has happened yet
            mSyncTriggerBar.animate().alpha(0f).setDuration(200).start();
        }
        mTrackingScrollMovement = false;
        mHintText.hide();
    }

    private void triggerSync() {
        mSyncTriggerBar.setVisibility(View.GONE);

        // This will call back to showSyncStatusBar():
        mFolderController.requestFolderRefresh();

        // Any continued dragging after this should have no effect
        mTrackingScrollMovement = false;

        mHintText.displayCheckingForMailAndHideAfterDelay();
    }

    protected void showSyncStatusBar() {
        mIsSyncing = true;

        LogUtils.i(LOG_TAG, "ConversationListView show sync status bar");
        mSyncTriggerBar.setVisibility(GONE);
        mSyncProgressBar.setVisibility(VISIBLE);
    }

    protected void onSyncFinished() {
        // onSyncFinished() can get called several times as result of folder updates that maybe
        // or may not be related to sync.
        if (mIsSyncing) {
            LogUtils.i(LOG_TAG, "ConversationListView hide sync status bar");
            // Hide both the sync progress bar and sync trigger bar
            mSyncProgressBar.setVisibility(GONE);
            mSyncTriggerBar.setVisibility(GONE);
            mIsSyncing = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mHasHintTextViewBeenAdded) {
            try {
                mWindowManager.removeView(mHintText);
            } catch (IllegalArgumentException e) {
                // Have seen this happen on occasion during orientation change.
            }
        }
    }

    private WindowManager.LayoutParams getRefreshHintTextLayoutParams() {
        // Create the "Swipe down to refresh" text view that covers the action bar.
        Rect rect= new Rect();
        Window window = mActivity.getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rect);
        int statusBarHeight = rect.top;
        int contentViewTop=
                window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
        int titleBarHeight= contentViewTop - statusBarHeight;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                titleBarHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        params.x = 0;
        params.y = statusBarHeight;
        return params;
    }

    /**
     * A test view that covers the entire action bar, used for displaying
     * "Swipe down to refresh" hint text if user has initiated a downward swipe.
     */
    protected static class HintText extends TextView {

        private static final int NONE = 0;
        private static final int SWIPE_TO_REFRESH = 1;
        private static final int CHECKING_FOR_MAIL = 2;

        // Can be one of NONE, SWIPE_TO_REFRESH, CHECKING_FOR_MAIL
        private int mDisplay;

        public HintText(final Context context) {
            super(context);

            mDisplay = NONE;
            setVisibility(View.GONE);

            // Set background color to be same as action bar color
            TypedValue actionBarStyle = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.actionBarStyle, actionBarStyle, true) &&
                    actionBarStyle.type == TypedValue.TYPE_REFERENCE) {
                TypedValue backgroundValue = new TypedValue();
                TypedArray attr = context.obtainStyledAttributes(actionBarStyle.resourceId,
                        new int[] {android.R.attr.background});
                attr.getValue(0, backgroundValue);
                setBackgroundResource(backgroundValue.resourceId);
                attr.recycle();
            } else {
                // Default color
                setBackgroundColor(R.color.list_background_color);
            }

            setTextAppearance(context, android.R.attr.textAppearanceMedium);
            setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        }

        private void displaySwipeToRefresh() {
            if (mDisplay != SWIPE_TO_REFRESH) {
                setText(getResources().getText(R.string.swipe_down_to_refresh));
                setVisibility(View.VISIBLE);
                setAlpha(0f);
                animate().alpha(1f).setDuration(TEXT_FADE_DURATION_IN_MILLIS).start();
                mDisplay = SWIPE_TO_REFRESH;
            }
        }

        private void displayCheckingForMailAndHideAfterDelay() {
            setText(getResources().getText(R.string.checking_for_mail));
            setVisibility(View.VISIBLE);
            mDisplay = CHECKING_FOR_MAIL;
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    hide();
                }
            }, SHOW_CHECKING_FOR_MAIL_DURATION_IN_MILLIS);
        }

        private void hide() {
            if (mDisplay != NONE) {
                animate().alpha(0f).setDuration(TEXT_FADE_DURATION_IN_MILLIS).start();
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setVisibility(View.GONE);
                    }
                }, TEXT_FADE_DURATION_IN_MILLIS);
                mDisplay = NONE;
            }
        }
    }
}
