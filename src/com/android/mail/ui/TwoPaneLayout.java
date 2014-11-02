/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui;

import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import com.android.mail.R;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.android.mail.utils.ViewUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

/**
 * This is a custom layout that manages the possible views of Gmail's large screen (read: tablet)
 * activity, and the transitions between them.
 *
 * This is not intended to be a generic layout; it is specific to the {@code Fragment}s
 * available in {@link MailActivity} and assumes their existence. It merely configures them
 * according to the specific <i>modes</i> the {@link Activity} can be in.
 *
 * Currently, the layout differs in three dimensions: orientation, two aspects of view modes.
 * This results in essentially three states: One where the folders are on the left and conversation
 * list is on the right, and two states where the conversation list is on the left: one in which
 * it's collapsed and another where it is not.
 *
 * In folder or conversation list view, conversations are hidden and folders and conversation lists
 * are visible. This is the case in both portrait and landscape
 *
 * In Conversation List or Conversation View, folders are hidden, and conversation lists and
 * conversation view is visible. This is the case in both portrait and landscape.
 *
 * In the Gmail source code, this was called TriStateSplitLayout
 */
final class TwoPaneLayout extends FrameLayout implements ModeChangeListener {

    private static final String LOG_TAG = "TwoPaneLayout";
    private static final long SLIDE_DURATION_MS = 300;

    private final int mDrawerWidthMini;
    private final int mDrawerWidthOpen;
    private final double mConversationListWeight;
    private final TimeInterpolator mSlideInterpolator;
    /**
     * If true, this layout group will treat the thread list and conversation view as full-width
     * panes to switch between.<br>
     * <br>
     * If false, always show a conversation view right next to the conversation list. This view will
     * also be populated (preview / "peek" mode) with a default conversation if none is selected by
     * the user.
     */
    private final boolean mListCollapsible;

    /**
     * The current mode that the tablet layout is in. This is a constant integer that holds values
     * that are {@link ViewMode} constants like {@link ViewMode#CONVERSATION}.
     */
    private int mCurrentMode = ViewMode.UNKNOWN;
    /**
     * This is a copy of {@link #mCurrentMode} that layout/positioning/animating code uses to
     * compare to the 'new' current mode, to avoid unnecessarily calculation.
     */
    private int mPositionedMode = ViewMode.UNKNOWN;
    /**
     * Similar to {@link #mPositionedMode}; this is the value of {@link #isDrawerOpen()} from the
     * last time layout ran, so we know not to run layout again if this hasn't changed.
     */
    private boolean mPositionedIsDrawerOpen;

    private TwoPaneController mController;
    private LayoutListener mListener;

    private View mMiscellaneousView;
    private View mConversationView;
    private View mFoldersView;
    private View mListView;

    private final Drawable mShadowDrawable;
    private final int mShadowMinWidth;

    private final List<Runnable> mTransitionCompleteJobs = Lists.newArrayList();
    private final PaneAnimationListener mPaneAnimationListener = new PaneAnimationListener();

    public static final int MISCELLANEOUS_VIEW_ID = R.id.miscellaneous_pane;

    public interface ConversationListLayoutListener {
        /**
         * Used for two-pane landscape layout positioning when other views need to align themselves
         * to the list view. Should be called only in tablet landscape mode!
         * @param xEnd the ending x coordinate of the list view
         * @param drawerOpen
         */
        void onConversationListLayout(int xEnd, boolean drawerOpen);
    }

    // Responsible for invalidating the shadow region only to minimize drawing overhead (and jank)
    // Coordinated with ListView animation to ensure shadow and list slide together.
    private final ValueAnimator.AnimatorUpdateListener mListViewAnimationListener =
            new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    if (ViewUtils.isViewRtl(TwoPaneLayout.this)) {
                        // Get the right edge of list and use as left edge coord for shadow
                        final int leftEdgeCoord = (int) mListView.getX() + mListView.getWidth();
                        invalidate(leftEdgeCoord, 0, leftEdgeCoord + mShadowMinWidth,
                                getBottom());
                    } else {
                        // Get the left edge of list and use as right edge coord for shadow
                        final int rightEdgeCoord = (int) mListView.getX();
                        invalidate(rightEdgeCoord - mShadowMinWidth, 0, rightEdgeCoord,
                                getBottom());
                    }
                }
            };

    public TwoPaneLayout(Context context) {
        this(context, null);
    }

    public TwoPaneLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Resources res = getResources();

        // The conversation list might be visible now, depending on the layout: in portrait we
        // don't show the conversation list, but in landscape we do.  This information is stored
        // in the constants
        mListCollapsible = !res.getBoolean(R.bool.is_tablet_landscape);

        mDrawerWidthMini = res.getDimensionPixelSize(R.dimen.two_pane_drawer_width_mini);
        mDrawerWidthOpen = res.getDimensionPixelSize(R.dimen.two_pane_drawer_width_open);

        mSlideInterpolator = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.decelerate_cubic);

        final int convListWeight = res.getInteger(R.integer.conversation_list_weight);
        final int convViewWeight = res.getInteger(R.integer.conversation_view_weight);
        mConversationListWeight = (double) convListWeight
                / (convListWeight + convViewWeight);

        mShadowDrawable = getResources().getDrawable(R.drawable.ic_vertical_shadow_start_4dp);
        mShadowMinWidth = mShadowDrawable.getMinimumWidth();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw children/update the canvas first.
        super.dispatchDraw(canvas);

        if (ViewUtils.isViewRtl(this)) {
            // Get the right edge of list and use as left edge coord for shadow
            final int leftEdgeCoord = (int) mListView.getX() + mListView.getWidth();
            mShadowDrawable.setBounds(leftEdgeCoord, 0, leftEdgeCoord + mShadowMinWidth,
                    mListView.getBottom());
        } else {
            // Get the left edge of list and use as right edge coord for shadow
            final int rightEdgeCoord = (int) mListView.getX();
            mShadowDrawable.setBounds(rightEdgeCoord - mShadowMinWidth, 0, rightEdgeCoord,
                    mListView.getBottom());
        }

        mShadowDrawable.draw(canvas);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFoldersView = findViewById(R.id.drawer);
        mListView = findViewById(R.id.conversation_list_pane);
        mConversationView = findViewById(R.id.conversation_pane);
        mMiscellaneousView = findViewById(MISCELLANEOUS_VIEW_ID);

        // all panes start GONE in initial UNKNOWN mode to avoid drawing misplaced panes
        mCurrentMode = ViewMode.UNKNOWN;
        mFoldersView.setVisibility(GONE);
        mListView.setVisibility(GONE);
        mConversationView.setVisibility(GONE);
        mMiscellaneousView.setVisibility(GONE);
    }

    @VisibleForTesting
    public void setController(TwoPaneController controller) {
        mController = controller;
        mListener = controller;

        ((ConversationViewFrame) mConversationView).setDownEventListener(mController);
        ((ConversationViewFrame) mMiscellaneousView).setDownEventListener(mController);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        LogUtils.d(Utils.VIEW_DEBUGGING_TAG, "TPL(%s).onMeasure()", this);
        setupPaneWidths(MeasureSpec.getSize(widthMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        LogUtils.d(Utils.VIEW_DEBUGGING_TAG, "TPL(%s).onLayout()", this);
        super.onLayout(changed, l, t, r, b);
        // Position/animate the panes only if the layout has truly changed in a way that affects
        // their positioning (e.g. mode change or drawer state change).
        // And do so only after the normal layout has happened to give children their positions,
        // in case they depend on them (e.g. if they call child.getWidth()).
        if (changed || mPositionedMode != mCurrentMode
                || isDrawerOpen() != mPositionedIsDrawerOpen) {
            positionPanes(getMeasuredWidth());
        }
    }

    /**
     * Sizes up the three sliding panes. This method will ensure that the LayoutParams of the panes
     * have the correct widths set for the current overall size and view mode.
     *
     * @param parentWidth this view's new width
     */
    private void setupPaneWidths(int parentWidth) {
        // only adjust the pane widths when my width changes
        if (parentWidth != getMeasuredWidth()) {
            final int convWidth = computeConversationWidth(parentWidth);
            setPaneWidth(mMiscellaneousView, convWidth);
            setPaneWidth(mConversationView, convWidth);
            setPaneWidth(mListView, computeConversationListWidth(parentWidth));
        }
    }

    /**
     * Positions the three sliding panes at the correct X offset (using {@link android.view.View#setX(float)}).
     * When switching from list->conversation mode or vice versa, animate the change in X.
     *
     * @param width
     */
    private void positionPanes(int width) {
        final boolean isDrawerOpen = isDrawerOpen();

        final int convX, listX, foldersX;

        final int foldersW = isDrawerOpen ? mDrawerWidthOpen : mDrawerWidthMini;
        final int listW = getPaneWidth(mListView);

        final boolean isRtl = ViewUtils.isViewRtl(this);
        boolean cvOnScreen = true;

        if (!mListCollapsible) {
            if (isRtl) {
                foldersX = width - mDrawerWidthOpen;
                listX = width - foldersW - listW;
                convX = listX - getPaneWidth(mConversationView);
            } else {
                foldersX = 0;
                listX = foldersW;
                convX = listX + listW;
            }
        } else {
            // Avoid any other check for conversation mode as there are situations where
            // the conversation is currently not set but this layout/position occurs, throwing off
            // this app's view and controller synergy and resulting in an awkward state such as a
            // non-responsive TL or an empty grey screen.
            if (ViewMode.isConversationMode(mCurrentMode) || ViewMode.isAdMode(mCurrentMode)) {
                // CV mode
                if (isRtl) {
                    convX = 0;
                    listX = getPaneWidth(mConversationView);
                    foldersX = listX + width - mDrawerWidthOpen;
                } else {
                    convX = 0;
                    listX = -listW;
                    foldersX = listX - foldersW;
                }
            } else {
                // TL mode
                cvOnScreen = false;
                if (isRtl) {
                    foldersX = width - mDrawerWidthOpen;
                    listX = width - foldersW - listW;
                    convX = listX - getPaneWidth(mConversationView);
                } else {
                    foldersX = 0;
                    listX = foldersW;
                    convX = listX + listW;
                }
            }
        }

        animatePanes(foldersX, listX, convX);

        // For views that are not on the screen, let's set their visibility for accessibility.
        final boolean folderVisible = isRtl ?
                foldersX + mFoldersView.getWidth() >= 0 : foldersX >= 0;
        final boolean listVisible = isRtl ? listX + mListView.getWidth() >= 0 : listX >= 0;
        adjustPaneVisibility(folderVisible, listVisible, cvOnScreen);

        final int xEnd = isRtl ? listX : listX + listW;
        // Let's not layout until the positions are finalized
        if (!mListCollapsible && xEnd != 0) {
            final List<ConversationListLayoutListener> layoutListeners =
                    mController.getConversationListLayoutListeners();
            for (ConversationListLayoutListener listener : layoutListeners) {
                listener.onConversationListLayout(xEnd, isDrawerOpen);
            }
        }

        mPositionedMode = mCurrentMode;
        mPositionedIsDrawerOpen = isDrawerOpen;
    }

    private void animatePanes(int foldersX, int listX, int convX) {
        // If positioning has not yet happened, we don't need to animate panes into place.
        // This happens on first layout, rotate, and when jumping straight to a conversation from
        // a view intent.
        if (mPositionedMode == ViewMode.UNKNOWN) {
            mConversationView.setX(convX);
            mMiscellaneousView.setX(convX);
            mListView.setX(listX);
            mFoldersView.setX(foldersX);

            // listeners need to know that the "transition" is complete, even if one is not run.
            // defer notifying listeners because we're in a layout pass, and they might do layout.
            post(mPaneAnimationListener);
            return;
        }

        if (ViewMode.isAdMode(mCurrentMode)) {
            mMiscellaneousView.animate().x(convX);
        } else {
            mConversationView.animate().x(convX);
        }

        mFoldersView.animate().x(foldersX);

        final ViewPropertyAnimator listAnimation =  mListView.animate()
                .x(listX)
                .setListener(mPaneAnimationListener);

        // If we're running K+, we can use the update listener to transition the list's left shadow
        // and set different update listeners based on rtl to avoid doing a check on every frame
        if (Utils.isRunningKitkatOrLater()) {
            listAnimation.setUpdateListener(mListViewAnimationListener);
        }

        configureAnimations(mConversationView, mFoldersView, mListView, mMiscellaneousView);
    }

    private void configureAnimations(View... views) {
        for (View v : views) {
            v.animate()
                .setInterpolator(mSlideInterpolator)
                .setDuration(SLIDE_DURATION_MS);
        }
    }

    /**
     * Adjusts the visibility of each pane before and after a transition. After the transition,
     * any invisible panes should be marked invisible. But visible panes should not wait for the
     * transition to finish-- they should be marked visible immediately.
     *
     */
    private void adjustPaneVisibility(final boolean folderVisible, final boolean listVisible,
            final boolean cvVisible) {
        applyPaneVisibility(VISIBLE, folderVisible, listVisible, cvVisible);
        mTransitionCompleteJobs.add(new Runnable() {
            @Override
            public void run() {
                applyPaneVisibility(INVISIBLE, !folderVisible, !listVisible, !cvVisible);
            }
        });
    }

    private void applyPaneVisibility(int visibility, boolean applyToFolders, boolean applyToList,
            boolean applyToCV) {
        if (applyToFolders) {
            mFoldersView.setVisibility(visibility);
        }
        if (applyToList) {
            mListView.setVisibility(visibility);
        }
        if (applyToCV) {
            if (mConversationView.getVisibility() != GONE) {
                mConversationView.setVisibility(visibility);
            }
            if (mMiscellaneousView.getVisibility() != GONE) {
                mMiscellaneousView.setVisibility(visibility);
            }
        }
    }

    private void onTransitionComplete() {
        if (mController.isDestroyed()) {
            // quit early if the hosting activity was destroyed before the animation finished
            LogUtils.i(LOG_TAG, "IN TPL.onTransitionComplete, activity destroyed->quitting early");
            return;
        }

        for (Runnable job : mTransitionCompleteJobs) {
            job.run();
        }
        mTransitionCompleteJobs.clear();

        switch (mCurrentMode) {
            case ViewMode.CONVERSATION:
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                dispatchConversationVisibilityChanged(true);
                dispatchConversationListVisibilityChange(!isConversationListCollapsed());

                break;
            case ViewMode.CONVERSATION_LIST:
            case ViewMode.SEARCH_RESULTS_LIST:
                dispatchConversationVisibilityChanged(false);
                dispatchConversationListVisibilityChange(true);

                break;
            case ViewMode.AD:
                dispatchConversationVisibilityChanged(false);
                dispatchConversationListVisibilityChange(!isConversationListCollapsed());

                break;
            default:
                break;
        }
    }

    /**
     * Computes the width of the conversation list in stable state of the current mode.
     */
    public int computeConversationListWidth() {
        return computeConversationListWidth(getMeasuredWidth());
    }

    /**
     * Computes the width of the conversation list in stable state of the current mode.
     */
    private int computeConversationListWidth(int parentWidth) {
        final int availWidth = parentWidth - mDrawerWidthMini;
        return mListCollapsible ? availWidth : (int) (availWidth * mConversationListWeight);
    }

    public int computeConversationWidth() {
        return computeConversationWidth(getMeasuredWidth());
    }

    /**
     * Computes the width of the conversation pane in stable state of the
     * current mode.
     */
    private int computeConversationWidth(int parentWidth) {
        return mListCollapsible ? parentWidth :
                parentWidth - computeConversationListWidth(parentWidth) - mDrawerWidthMini;
    }

    private void dispatchConversationListVisibilityChange(boolean visible) {
        if (mListener != null) {
            mListener.onConversationListVisibilityChanged(visible);
        }
    }

    private void dispatchConversationVisibilityChanged(boolean visible) {
        if (mListener != null) {
            mListener.onConversationVisibilityChanged(visible);
        }
    }

    // does not apply to drawer children. will return zero for those.
    private int getPaneWidth(View pane) {
        return pane.getLayoutParams().width;
    }

    private boolean isDrawerOpen() {
        return mController != null && mController.isDrawerOpen();
    }

    /**
     * @return Whether or not the conversation list is visible on screen.
     */
    @Deprecated
    public boolean isConversationListCollapsed() {
        return !ViewMode.isListMode(mCurrentMode) && mListCollapsible;
    }

    @Override
    public void onViewModeChanged(int newMode) {
        // make all initially GONE panes visible only when the view mode is first determined
        if (mCurrentMode == ViewMode.UNKNOWN) {
            mFoldersView.setVisibility(VISIBLE);
            mListView.setVisibility(VISIBLE);
        }

        if (ViewMode.isAdMode(newMode)) {
            mMiscellaneousView.setVisibility(VISIBLE);
            mConversationView.setVisibility(GONE);
        } else {
            mConversationView.setVisibility(VISIBLE);
            mMiscellaneousView.setVisibility(GONE);
        }

        // detach the pager immediately from its data source (to prevent processing updates)
        if (ViewMode.isConversationMode(mCurrentMode)) {
            mController.disablePagerUpdates();
        }

        // notify of list visibility change up-front when going to list mode
        // (so the transition runs with the full TL in view)
        if (newMode == ViewMode.CONVERSATION_LIST) {
            dispatchConversationListVisibilityChange(true);
        }

        mCurrentMode = newMode;
        LogUtils.i(LOG_TAG, "onViewModeChanged(%d)", newMode);

        // do all the real work in onMeasure/onLayout, when panes are sized and positioned for the
        // current width/height anyway
        requestLayout();
    }

    public boolean isModeChangePending() {
        return mPositionedMode != mCurrentMode;
    }

    private void setPaneWidth(View pane, int w) {
        final ViewGroup.LayoutParams lp = pane.getLayoutParams();
        if (lp.width == w) {
            return;
        }
        lp.width = w;
        pane.setLayoutParams(lp);
        if (LogUtils.isLoggable(LOG_TAG, LogUtils.DEBUG)) {
            final String s;
            if (pane == mFoldersView) {
                s = "folders";
            } else if (pane == mListView) {
                s = "conv-list";
            } else if (pane == mConversationView) {
                s = "conv-view";
            } else if (pane == mMiscellaneousView) {
                s = "misc-view";
            } else {
                s = "???:" + pane;
            }
            LogUtils.d(LOG_TAG, "TPL: setPaneWidth, w=%spx pane=%s", w, s);
        }
    }

    public boolean shouldShowPreviewPanel() {
        return !mListCollapsible;
    }

    private class PaneAnimationListener extends AnimatorListenerAdapter implements Runnable {

        @Override
        public void run() {
            onTransitionComplete();
        }

        @Override
        public void onAnimationStart(Animator animation) {
            // If we're running pre-K, we don't have ViewPropertyAnimator's setUpdateListener.
            // This is a hack to get around it and uses a dummy ValueAnimator to allow us
            // to create an animation for the shadow along with the list view.
            if (!Utils.isRunningKitkatOrLater()) {
                final ValueAnimator shadowAnimator = ValueAnimator.ofFloat(0, 1);
                shadowAnimator.setDuration(SLIDE_DURATION_MS)
                        .addUpdateListener(mListViewAnimationListener);
                shadowAnimator.start();
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            onTransitionComplete();
        }

    }

}
