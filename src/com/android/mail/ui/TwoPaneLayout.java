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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.RelativeLayout;

import com.android.mail.R;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;

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
final class TwoPaneLayout extends RelativeLayout implements ModeChangeListener {

    /**
     * Scaling modifier for sAnimationSlideRightDuration.
     */
    private static final double SLIDE_DURATION_SCALE = 2.0 / 3.0;
    private static final String LOG_TAG = new LogUtils().getLogTag();
    private static final TimeInterpolator sLeftInterpolator = new DecelerateInterpolator(2.25f);
    private static final TimeInterpolator sRightInterpolator = new DecelerateInterpolator(2.5f);

    private static int sAnimationSlideLeftDuration;
    private static int sAnimationSlideRightDuration;
    private static double sScaledConversationListWeight;
    private static double sScaledFolderListWeight;
    /**
     * The current mode that the tablet layout is in. This is a constant integer that holds values
     * that are {@link ViewMode} constants like {@link ViewMode#CONVERSATION}.
     */
    private int currentMode;
    /**
     * Whether or not the layout is currently in the middle of a cross-fade animation that requires
     * custom rendering.
     */
    private boolean mAnimatingFade;

    private Context mContext;
    private int mConversationLeft;

    private View mConversationListContainer;

    private View mConversationView;
    private View mConversationViewOverlay;
    /** Left position of each fragment. */
    private int mFoldersLeft;
    private View mFoldersView;
    private int mListAlpha;

    /** Captured bitmap of each fragment. */
    private Bitmap mListBitmap;
    private int mListBitmapLeft;
    /** Whether or not the conversation list can be collapsed all the way to hidden on the left.
     * This is used only in portrait view*/
    private Boolean mListCollapsed;
    private LayoutListener mListener;
    private int mListLeft;
    private Paint mListPaint;
    private View mListView;
    /**
     * A handle to any out standing animations that are in progress.
     */
    private Animator mOutstandingAnimator;

    /** Paint to be used for each fragment. */
    private Paint mPaint;

    private final AnimatorListener mConversationListListener =
            new AnimatorListener(AnimatorListener.CONVERSATION_LIST);
    private final AnimatorListener mConversationListener =
            new AnimatorListener(AnimatorListener.CONVERSATION);
    private boolean mIsSearchResult = false;

    private class AnimatorListener implements Animator.AnimatorListener {
        public static final int CONVERSATION_LIST = 1;
        public static final int CONVERSATION = 2;

        /**
         * Different animator listeners need to perform different actions on start and finish based
         * on their type. The types are assigned at object creation using only the constants:
         * {@link #CONVERSATION_LIST}, {@link #COLLAPSE_LIST}, {@link #CONVERSATION} or
         * {@link #UNCOLLAPSE_LIST}
         */
        private final int listener_type;

        /**
         * Create an animator listener of a specific type. The types are created using the constants
         * {@link #CONVERSATION_LIST}, {@link #COLLAPSE_LIST}, {@link #CONVERSATION} or
         * {@link #UNCOLLAPSE_LIST}
         * @param type
         */
        AnimatorListener(int type){
            this.listener_type = type;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            LogUtils.d(LOG_TAG, "Cancelling animation (this=%s)", animation);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mAnimatingFade = false;
            mOutstandingAnimator = null;
            destroyBitmaps();
            // Now close the animation depending on the type of animator selected.
            switch (listener_type) {
                case CONVERSATION_LIST:
                    onFinishEnteringConversationListMode();
                    return;
                case CONVERSATION:
                    onFinishEnteringConversationMode();
                    return;
            }
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
            // Do nothing.
        }

        @Override
        public void onAnimationStart(Animator animation) {
            switch (listener_type) {
                case CONVERSATION_LIST:
                    mFoldersView.setVisibility(View.VISIBLE);
            }
        }
    }

    public TwoPaneLayout(Context context) {
        super(context);
    }

    public TwoPaneLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TwoPaneLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Sets the {@link ViewMode} that this layout is synchronized to.
     * @param viewMode The view mode object to listen to changes on.
     */
    // TODO(viki): Change this to have the ActivityController provide the viewMode only for adding
    // as a listener.
    public void attachToViewMode(ViewMode viewMode) {
        viewMode.addListener(this);
        currentMode = viewMode.getMode();
    }

    /**
     * Captures list view.
     */
    private void captureListBitmaps() {
        if (mListBitmap != null || mListView == null || mListView.getWidth() == 0
                || mListView.getHeight() == 0) {
            return;
        }

        try {
            mListBitmap = Bitmap.createBitmap(mListView.getWidth(), mListView.getHeight(),
                    Config.ARGB_8888);
            Canvas canvas = new Canvas(mListBitmap);
            mListView.draw(canvas);
        } catch (OutOfMemoryError e) {
            LogUtils.e(LOG_TAG, e, "Could not create a bitmap due to OutOfMemoryError");
        }
    }

    /**
     * Computes left position of the conversation list relative to its uncollapsed position.
     * This is only relevant in a collapsible view, and will be 0 otherwise.
     */
    private int computeConversationListLeft(int width) {
        return isConversationListCollapsed() ? -width : 0;
    }

    /**
     * Computes the width of the conversation list in stable state of the current mode.
     */
    protected int computeConversationListWidth() {
        return computeConversationListWidth(getMeasuredWidth());
    }

    /**
     * Computes the width of the conversation list in stable state of the current mode.
     */
    private int computeConversationListWidth(int totalWidth) {
        switch (currentMode) {
            case ViewMode.CONVERSATION_LIST:
            case ViewMode.SEARCH_RESULTS_LIST:
                return totalWidth - computeFolderListWidth();
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
            case ViewMode.CONVERSATION:
                return (int) (totalWidth * sScaledConversationListWeight);
        }
        return 0;
    }

    /**
     * Computes the width of the conversation pane in stable state of the current mode.
     */
    private int computeConversationWidth() {
        return computeConversationWidth(getMeasuredWidth());
    }

    /**
     * Computes the width of the conversation pane in stable state of the
     * current mode.
     */
    private int computeConversationWidth(int totalWidth) {
        switch (currentMode) {
            case ViewMode.CONVERSATION:
                // Fallthrough
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                if (isConversationListCollapsed()) {
                    return totalWidth;
                }
                return totalWidth - (int) (totalWidth * sScaledConversationListWeight);
        }
        return 0;
    }

    /**
     * Computes the width of the folder list in stable state of the current mode.
     */
    private int computeFolderListWidth() {
        return isFolderListCollapsed() ?
                0 : (int) (getMeasuredWidth() * sScaledFolderListWeight);
    }

    /**
     * Frees up the bitmaps.
     */
    private void destroyBitmaps() {
        if (mListBitmap != null) {
            mListBitmap.recycle();
            mListBitmap = null;
        }
    }

    private void dispatchConversationListVisibilityChange() {
        if (mListener != null) {
            // Post the visibility change using a handler, so other views
            // will not be modified while we are performing a layout of the
            // TriStateSplitLayout
            final Handler handler = new Handler();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConversationListVisibilityChanged(!isConversationListCollapsed());
                }
            });
        }
    }

    private void dispatchConversationVisibilityChanged(boolean visible) {
        if (mListener != null) {
            mListener.onConversationVisibilityChanged(visible);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!isAnimatingFade()) {
            super.dispatchDraw(canvas);
            return;
        }

        canvas.save();
        canvas.translate(mFoldersLeft, 0);
        mFoldersView.draw(canvas);
        canvas.restore();

        // The bitmap can be null if the view hasn't been drawn by the time we capture the bitmap.
        if (mListBitmap != null) {
            canvas.drawBitmap(mListBitmap, mListBitmapLeft, 0, mListPaint);
        }

        canvas.saveLayerAlpha(mListLeft, 0, mListLeft + mListView.getWidth(), getHeight(),
                mListAlpha, Canvas.ALL_SAVE_FLAG);
        canvas.translate(mListLeft, 0);
        mListView.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(mConversationLeft, 0);
        mConversationView.draw(canvas);
        canvas.restore();
    }

    private void enterConversationListMode() {
        mListView.setPadding(mListView.getPaddingLeft(), 0, mListView.getPaddingRight(),
                mListView.getPaddingBottom());

        // On the initial call, measurements may not have been done (i.e. this
        // Layout has never been rendered), so no animation will be done.
        if (getMeasuredWidth() == 0) {
            mFoldersView.setVisibility(View.VISIBLE);
            onFinishEnteringConversationListMode();
            return;
        }

        // Slide folder list in from the left.
        final int folderListWidth = computeFolderListWidth();
        setFolderListWidth(folderListWidth);

        // Prepare animation.
        mAnimatingFade = true;
        captureListBitmaps();
        ArrayList<PropertyValuesHolder> values = Lists.newArrayList();

        values.add(PropertyValuesHolder.ofInt("foldersLeft", -folderListWidth, 0));

        // Reset the relative left of the list view.
        setConversationListLeft(0);

        // Push conversation list out to fill remaining space.
        setConversationListWidth(computeConversationListWidth());

        // Fading out the conversation bitmap should finish before
        // the final transition to the conversation list view.
        ObjectAnimator animator = ObjectAnimator.ofInt(this, "listBitmapAlpha", 255, 0);
        animator.setDuration((long) (sAnimationSlideRightDuration * SLIDE_DURATION_SCALE));
        animator.setInterpolator(sRightInterpolator);

        values.add(PropertyValuesHolder.ofInt("listBitmapLeft", 0, folderListWidth));
        values.add(PropertyValuesHolder.ofInt("listLeft", 0, folderListWidth));
        values.add(PropertyValuesHolder.ofInt("listAlpha", 0, 255));

        // Slide conversation out to the right.
        values.add(PropertyValuesHolder.ofInt("conversationLeft", mListView.getMeasuredWidth(),
                getWidth()));
        ObjectAnimator valuesAnimator = ObjectAnimator.ofPropertyValuesHolder(this,
                values.toArray(new PropertyValuesHolder[values.size()])).setDuration(
                sAnimationSlideRightDuration);
        valuesAnimator.setInterpolator(sRightInterpolator);
        valuesAnimator.addListener(mConversationListListener);

        mOutstandingAnimator = valuesAnimator;
        AnimatorSet transitionSet = new AnimatorSet();
        transitionSet.playTogether(animator, valuesAnimator);
        transitionSet.start();
    }

    private void enterConversationMode() {
        mConversationView.setVisibility(View.VISIBLE);

        // On the initial call, measurements may not have been done (i.e. this Layout has never
        // been rendered), so no animation will be done.
        if (getMeasuredWidth() == 0) {
            onFinishEnteringConversationMode();
            return;
        }

        // Prepare for animation.
        mAnimatingFade = true;
        captureListBitmaps();
        ArrayList<PropertyValuesHolder> values = Lists.newArrayList();

        // Slide folders out towards the left off screen.
        int foldersWidth = mFoldersView.getMeasuredWidth();
        values.add(PropertyValuesHolder.ofInt("foldersLeft", 0, -foldersWidth));

        // Shrink the conversation list to make room for the conversation, and default
        // it to collapsed in case it is collapsible.
        int targetWidth = computeConversationListWidth();
        setConversationListWidth(targetWidth);

        int currentListLeft = foldersWidth + getConversationListLeft();
        int targetListLeft = computeConversationListLeft(targetWidth);
        setConversationListLeft(targetListLeft);
        if (currentListLeft != targetListLeft) {
            values.add(
                    PropertyValuesHolder.ofInt("listBitmapLeft", currentListLeft, targetListLeft));
            values.add(PropertyValuesHolder.ofInt("listBitmapAlpha", 255, 0));
            values.add(
                    PropertyValuesHolder.ofInt("listLeft",
                            currentListLeft + mListView.getWidth() - targetWidth, targetListLeft));
            values.add(PropertyValuesHolder.ofInt("listAlpha", 0, 255));
        }

        // Set up the conversation view.
        // Performance note: do not animate the width of this, as it is very
        // expensive to reflow in the WebView.
        setConversationWidth(computeConversationWidth());
        values.add(PropertyValuesHolder.ofInt(
                "conversationLeft", getWidth(), targetListLeft + targetWidth));

        startLayoutAnimation(sAnimationSlideLeftDuration, mConversationListener, sLeftInterpolator,
                values.toArray(new PropertyValuesHolder[values.size()]));
    }

    /**
     * @return The left position of the conversation list relative to its uncollapsed position.
     *     This is only relevant in a collapsible view, and will be 0 otherwise.
     */
    public int getConversationListLeft() {
        return ((ViewGroup.MarginLayoutParams) mConversationListContainer.getLayoutParams())
                .leftMargin;
    }

    public void initializeLayout(Context context) {
        initializeLayout(context, false);
    }

    /**
     * Initializes the layout with a specific context.
     */
    @VisibleForTesting
    public void initializeLayout(Context context, boolean isSearchResult) {
        mContext = context;
        mIsSearchResult = isSearchResult;

        Resources res = getResources();
        mFoldersView = findViewById(R.id.content_pane);
        if (mIsSearchResult) {
            mFoldersView.setVisibility(View.GONE);
        }
        mConversationListContainer = findViewById(R.id.conversation_column_container);
        mListView = findViewById(R.id.conversation_list);
        mConversationView = findViewById(R.id.conversation_pane_container);
        mConversationViewOverlay = findViewById(R.id.conversation_overlay);

        sAnimationSlideLeftDuration = res.getInteger(R.integer.activity_slide_left_duration);
        sAnimationSlideRightDuration = res.getInteger(R.integer.activity_slide_right_duration);
        final int sFolderListWeight = res.getInteger(R.integer.folder_list_weight);
        final int sConversationListWeight = res.getInteger(R.integer.conversation_list_weight);
        final int sConversationViewWeight = res.getInteger(R.integer.conversation_view_weight);
        sScaledFolderListWeight = (double) sFolderListWeight
                / (sFolderListWeight + sConversationListWeight);
        sScaledConversationListWeight = (double) sConversationListWeight
                / (sConversationListWeight + sConversationViewWeight);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(android.R.color.white);
        mListPaint = new Paint();
        mListPaint.setAntiAlias(true);
    }

    public View getConversationView() {
        return mConversationView;
    }

    private boolean isAnimatingFade() {
        return mAnimatingFade;
    }

    private boolean isFolderListCollapsed() {
        return mIsSearchResult;
    }

    /**
     * @return Whether or not the conversation list is visible on screen.
     */
    public boolean isConversationListCollapsed() {
        if (mListCollapsed == null) {
            mListCollapsed = new Boolean(mContext.getResources()
                    .getBoolean(R.bool.list_collapsed));
        }
        return mListCollapsed;
    }

    /**
     * Finalizes state after animations settle when entering the conversation list mode.
     */
    private void onFinishEnteringConversationListMode() {
        mConversationView.setVisibility(View.GONE);
        mConversationViewOverlay.setVisibility(View.GONE);
        mFoldersView.setVisibility(View.VISIBLE);

        // Once animations settle, the conversation list always takes up the
        // remaining space that is on the right, so avoid hard pixel values,
        // since this avoids manual re-computations when the parent container
        // size changes for any reason (e.g. orientation change).
        mConversationListContainer.getLayoutParams().width =
                ViewGroup.LayoutParams.MATCH_PARENT;
        requestLayout();
        dispatchConversationListVisibilityChange();
        dispatchConversationVisibilityChanged(false);
    }

    /**
     * Finalizes state after animations settle when entering conversation mode.
     */
    private void onFinishEnteringConversationMode() {
        mFoldersView.setVisibility(View.GONE);
        setConversationListWidth(computeConversationListWidth());
        dispatchConversationVisibilityChanged(true);
    }

    /**
     * Handles a size change and sets layout parameters as necessary.
     * Most of the time this occurs for an orientation change, but theoretically could occur
     * if the Gmail layout was included in a larger ViewGroup.
     *
     * {@inheritDoc}
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w == oldw) {
            // Only width changes are relevant to our logic.
            return;
        }

        switch (currentMode) {
            case ViewMode.SEARCH_RESULTS_LIST:
            case ViewMode.CONVERSATION_LIST:
                setFolderListWidth(computeFolderListWidth());
                break;

            case ViewMode.SEARCH_RESULTS_CONVERSATION:
            case ViewMode.CONVERSATION:
                final int conversationListWidth = computeConversationListWidth(w);
                setConversationListWidth(conversationListWidth);
                setConversationWidth(computeConversationWidth(w));
                setConversationListLeft(computeConversationListLeft(conversationListWidth));
                break;
        }

        // Request a measure pass here so all children views can be measured correctly before
        // layout.
        int widthSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        measure(widthSpec, heightSpec);
    }

    @Override
    public void onViewModeChanged(int newMode) {
        currentMode = newMode;
        // Finish the current animation before changing mode.
        if (mOutstandingAnimator != null) {
            mOutstandingAnimator.cancel();
        }
        switch (currentMode) {
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
            case ViewMode.CONVERSATION:
                enterConversationMode();
                break;
            case ViewMode.SEARCH_RESULTS_LIST:
            case ViewMode.CONVERSATION_LIST:
                enterConversationListMode();
                break;
            case ViewMode.FOLDER_LIST:
                break;
        }
    }

    /**
     * Sets the left position of the conversation fragment. Used by animators.
     * Not to be used externally.
     * @hide
     */
    public void setConversationLeft(int left) {
        mConversationLeft = left;
        invalidate();
    }

    /**
     * Sets the relative left position of the conversation list.
     */
    private void setConversationListLeft(int left) {
        ((ViewGroup.MarginLayoutParams) mConversationListContainer.getLayoutParams())
            .leftMargin = left;
        requestLayout();
    }

    /**
     * Sets the width of the conversation list.
     */
    private void setConversationListWidth(int width) {
        mConversationListContainer.getLayoutParams().width = width;
        requestLayout();
    }

    /**
     * Sets the width of the conversation pane.
     */
    private void setConversationWidth(int width) {
        mConversationView.getLayoutParams().width = width;
        requestLayout();
    }

    /**
     * Sets the width of the folder list pane.
     * Used internally and by animators. Not to be used externally.
     */
    private void setFolderListWidth(int width) {
        mFoldersView.getLayoutParams().width = width;
        // Mindy points out that this is strange. Instead of requesting a layout for the folders
        // view, we should be requesting a layout for the entire view.
        // TODO(viki): Change to this.requestLayout() and see if there is any improvement or loss
        requestLayout();
    }

    /**
     * Sets the left position of the folders fragment. Used by animators. Not to
     * be used externally.
     * @hide
     */
    public void setFoldersLeft(int left) {
        mFoldersLeft = left;
        invalidate();
    }

    /**
     * Sets the alpha of the conversation list. Used by animators. Not to be used externally.
     * @hide
     */
    public void setListAlpha(int alpha) {
        mListAlpha = alpha;
        invalidate();
    }

    /**
     * Sets the alpha of the conversation list bitmap. Used by animators. Not to be used externally.
     * @hide
     */
    public void setListBitmapAlpha(int alpha) {
        mListPaint.setAlpha(alpha);
        invalidate();
    }

    /**
     * Sets the left position of the conversation list bitmap. Used by animators. Not to be used
     * externally.
     * @hide
     */
    public void setListBitmapLeft(int left) {
        mListBitmapLeft = left;
        invalidate();
    }

    /**
     * Sets the {@link LayoutListener} for this object.
     */
    public void setListener(LayoutListener listener) {
        mListener = listener;
    }

    /**
     * Sets the left position of the conversation list. Used by animators. Not to be used
     * externally.
     * @hide
     */
    public void setListLeft(int left) {
        mListLeft = left;
        invalidate();
    }

    /**
     * Helper method to start animation.
     */
    private void startLayoutAnimation(
            int duration, AnimatorListener listener, TimeInterpolator interpolator,
            PropertyValuesHolder... values) {
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(
                this, values).setDuration(duration);
        animator.setInterpolator(interpolator);
        if (listener != null) {
            animator.addListener(listener);
        }

        mOutstandingAnimator = animator;
        animator.start();
    }
}
