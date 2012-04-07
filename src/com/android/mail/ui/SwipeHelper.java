/*
 * Copyright (C) 2012 Google Inc.
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.android.mail.browse.ConversationItemView;

import java.util.ArrayList;
import java.util.Collection;

public class SwipeHelper {
    static final String TAG = "com.android.systemui.SwipeHelper";
    private static final boolean DEBUG_INVALIDATE = false;
    private static final boolean SLOW_ANIMATIONS = false; // DEBUG;
    private static final boolean CONSTRAIN_SWIPE = true;
    private static final boolean FADE_OUT_DURING_SWIPE = true;
    private static final boolean DISMISS_IF_SWIPED_FAR_ENOUGH = true;
    private static final boolean LOG_SWIPE_DISMISS_VELOCITY = false; // STOPSHIP - DEBUG ONLY

    public static final int X = 0;
    public static final int Y = 1;

    private static LinearInterpolator sLinearInterpolator = new LinearInterpolator();

    private float SWIPE_ESCAPE_VELOCITY = 100f; // dp/sec
    private int DEFAULT_ESCAPE_ANIMATION_DURATION = 200; // ms
    private int MAX_ESCAPE_ANIMATION_DURATION = 400; // ms
    private int MAX_DISMISS_VELOCITY = 2000; // dp/sec
    private static final int SNAP_ANIM_LEN = SLOW_ANIMATIONS ? 1000 : 1; // ms

    public static float ALPHA_FADE_START = 0f; // fraction of thumbnail width
                                                 // where fade starts
    static final float ALPHA_FADE_END = 0.5f; // fraction of thumbnail width
                                              // beyond which alpha->0
    private float mMinAlpha = 0f;

    private float mPagingTouchSlop;
    private Callback mCallback;
    private int mSwipeDirection;
    private VelocityTracker mVelocityTracker;

    private float mInitialTouchPosX;
    private boolean mDragging;
    private SwipeableItemView mCurrView;
    private View mCurrAnimView;
    private boolean mCanCurrViewBeDimissed;
    private float mDensityScale;
    private float mLastY;
    private Collection<ConversationItemView> mAssociatedViews;
    private final float mScrollSlop;
    private float mInitialTouchPosY;
    private float mMinSwipe;
    private float mMinVert;
    private float mMinLock;

    public SwipeHelper(int swipeDirection, Callback callback, float densityScale,
            float pagingTouchSlop, float scrollSlop, float minSwipe, float minVert, float minLock) {
        mCallback = callback;
        mSwipeDirection = swipeDirection;
        mVelocityTracker = VelocityTracker.obtain();
        mDensityScale = densityScale;
        mPagingTouchSlop = pagingTouchSlop;
        mScrollSlop = scrollSlop;
        mMinSwipe = minSwipe;
        mMinVert = minVert;
        mMinLock = minLock;
    }

    public void setDensityScale(float densityScale) {
        mDensityScale = densityScale;
    }

    public void setPagingTouchSlop(float pagingTouchSlop) {
        mPagingTouchSlop = pagingTouchSlop;
    }

    private float getVelocity(VelocityTracker vt) {
        return mSwipeDirection == X ? vt.getXVelocity() :
                vt.getYVelocity();
    }

    private ObjectAnimator createTranslationAnimation(View v, float newPos) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(v,
                mSwipeDirection == X ? "translationX" : "translationY", newPos);
        return anim;
    }

    private ObjectAnimator createDismissAnimation(View v, float newPos, int duration) {
        ObjectAnimator anim = createTranslationAnimation(v, newPos);
        anim.setInterpolator(sLinearInterpolator);
        anim.setDuration(duration);
        return anim;
    }

    private float getPerpendicularVelocity(VelocityTracker vt) {
        return mSwipeDirection == X ? vt.getYVelocity() :
                vt.getXVelocity();
    }

    private void setTranslation(View v, float translate) {
        if (mSwipeDirection == X) {
            v.setTranslationX(translate);
        } else {
            v.setTranslationY(translate);
        }
    }

    private float getSize(View v) {
        return mSwipeDirection == X ? v.getMeasuredWidth() :
                v.getMeasuredHeight();
    }

    public void setMinAlpha(float minAlpha) {
        mMinAlpha = minAlpha;
    }

    private float getAlphaForOffset(View view) {
        float viewSize = getSize(view);
        final float fadeSize = ALPHA_FADE_END * viewSize;
        float result = 1.0f;
        float pos = view.getTranslationX();
        if (pos >= viewSize * ALPHA_FADE_START) {
            result = 1.0f - (pos - viewSize * ALPHA_FADE_START) / fadeSize;
        } else if (pos < viewSize * (1.0f - ALPHA_FADE_START)) {
            result = 1.0f + (viewSize * ALPHA_FADE_START + pos) / fadeSize;
        }
        return Math.max(mMinAlpha, result);
    }

    // invalidate the view's own bounds all the way up the view hierarchy
    public static void invalidateGlobalRegion(View view) {
        invalidateGlobalRegion(
            view,
            new RectF(view.getLeft(), view.getTop(), view.getRight(), view.getBottom()));
    }

    // invalidate a rectangle relative to the view's coordinate system all the way up the view
    // hierarchy
    public static void invalidateGlobalRegion(View view, RectF childBounds) {
        //childBounds.offset(view.getTranslationX(), view.getTranslationY());
        if (DEBUG_INVALIDATE)
            Log.v(TAG, "-------------");
        while (view.getParent() != null && view.getParent() instanceof View) {
            view = (View) view.getParent();
            view.getMatrix().mapRect(childBounds);
            view.invalidate((int) Math.floor(childBounds.left),
                            (int) Math.floor(childBounds.top),
                            (int) Math.ceil(childBounds.right),
                            (int) Math.ceil(childBounds.bottom));
            if (DEBUG_INVALIDATE) {
                Log.v(TAG, "INVALIDATE(" + (int) Math.floor(childBounds.left)
                        + "," + (int) Math.floor(childBounds.top)
                        + "," + (int) Math.ceil(childBounds.right)
                        + "," + (int) Math.ceil(childBounds.bottom));
            }
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastY = ev.getY();
                mDragging = false;
                View view = mCallback.getChildAtPosition(ev);
                if (view instanceof SwipeableItemView) {
                    mCurrView = (SwipeableItemView) view;
                }
                mVelocityTracker.clear();
                if (mCurrView != null) {
                    mCurrAnimView = mCurrView.getView();
                    mCanCurrViewBeDimissed = mCallback.canChildBeDismissed(mCurrView);
                    mVelocityTracker.addMovement(ev);
                    mInitialTouchPosX = ev.getX();
                    mInitialTouchPosY = ev.getY();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mCurrView != null) {
                    // Check the movement direction.
                    if (mLastY >= 0) {
                        float currY = ev.getY();
                        if (Math.abs(currY - mLastY) > mScrollSlop) {
                            mLastY = ev.getY();
                            mCurrView.cancelTap();
                            return false;
                        }
                    }
                    mVelocityTracker.addMovement(ev);
                    float pos = ev.getX();
                    float delta = pos - mInitialTouchPosX;
                    if (Math.abs(delta) > mPagingTouchSlop) {
                        if (mCurrView.canSwipe()) {
                            mCallback.onBeginDrag(mCurrView.getView());
                            mDragging = true;
                            mInitialTouchPosX = ev.getX() - mCurrAnimView.getTranslationX();
                            mInitialTouchPosY = ev.getY();
                            mCurrView.cancelTap();
                        }
                    }
                }
                mLastY = ev.getY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                mCurrView = null;
                mCurrAnimView = null;
                mLastY = -1;
                break;
        }
        return mDragging;
    }

    public void setAssociatedViews(Collection<ConversationItemView> associated) {
        mAssociatedViews = associated;
    }

    public void clearAssociatedViews() {
        mAssociatedViews = null;
    }

    /**
     * @param view The view to be dismissed
     * @param velocity The desired pixels/second speed at which the view should
     *            move
     */
    private void dismissChild(final SwipeableItemView view, float velocity) {
        final View animView = mCurrView.getView();
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(view);
        float newPos = determinePos(animView, velocity);
        int duration = determineDuration(animView, newPos, velocity);

        animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ObjectAnimator anim = createDismissAnimation(animView, newPos, duration);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCallback.onChildDismissed(mCurrView);
                animView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        anim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (FADE_OUT_DURING_SWIPE && canAnimViewBeDismissed) {
                    animView.setAlpha(getAlphaForOffset(animView));
                }
                invalidateGlobalRegion(animView);
            }
        });
        anim.start();
    }

    private void dismissChildren(final Collection<ConversationItemView> views, float velocity) {
        AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCallback.onChildrenDismissed(mCurrView, views);
                mCurrView.getView().setLayerType(View.LAYER_TYPE_NONE, null);
            }
        };
        dismissChildren(views, velocity, listener);
    }

    private void dismissChildren(final Collection<ConversationItemView> views, float velocity,
            AnimatorListenerAdapter listener) {
        final View animView = mCurrView.getView();
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(mCurrView);
        float newPos = determinePos(animView, velocity);
        int duration = determineDuration(animView, newPos, velocity);
        ArrayList<Animator> animations = new ArrayList<Animator>();
        ObjectAnimator anim;
        for (final ConversationItemView view : views) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            anim = createDismissAnimation(view, newPos, duration);
            anim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (FADE_OUT_DURING_SWIPE && canAnimViewBeDismissed) {
                        view.setAlpha(getAlphaForOffset(view));
                    }
                    invalidateGlobalRegion(view);
                }
            });
            animations.add(anim);
        }
        AnimatorSet transitionSet = new AnimatorSet();
        transitionSet.playTogether(animations);
        transitionSet.addListener(listener);
        transitionSet.start();
    }

    public void dismissChildren(ConversationItemView first,
            final Collection<ConversationItemView> views, AnimatorListenerAdapter listener) {
        mCurrView = first;
        dismissChildren(views, 0f, listener);
    }

    private int determineDuration(View animView, float newPos, float velocity) {
        int duration = MAX_ESCAPE_ANIMATION_DURATION;
        if (velocity != 0) {
            duration = Math
                    .min(duration,
                            (int) (Math.abs(newPos - animView.getTranslationX()) * 1000f / Math
                                    .abs(velocity)));
        } else {
            duration = DEFAULT_ESCAPE_ANIMATION_DURATION;
        }
        return duration;
    }

    private float determinePos(View animView, float velocity) {
        float newPos = 0;
        if (velocity < 0 || (velocity == 0 && animView.getTranslationX() < 0)
        // if we use the Menu to dismiss an item in landscape, animate up
                || (velocity == 0 && animView.getTranslationX() == 0 && mSwipeDirection == Y)) {
            newPos = -getSize(animView);
        } else {
            newPos = getSize(animView);
        }
        return newPos;
    }

    public void snapChild(final SwipeableItemView view, float velocity) {
        final View animView = view.getView();
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(view);
        ObjectAnimator anim = createTranslationAnimation(animView, 0);
        int duration = SNAP_ANIM_LEN;
        anim.setDuration(duration);
        anim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (FADE_OUT_DURING_SWIPE && canAnimViewBeDismissed) {
                    animView.setAlpha(getAlphaForOffset(animView));
                }
                invalidateGlobalRegion(animView);
            }
        });
        anim.start();
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (!mDragging) {
            return false;
        }
        // If this item is being dragged, cancel any tap handlers/ events/
        // actions for this item.
        if (mCurrView != null) {
            mCurrView.cancelTap();
        }
        mVelocityTracker.addMovement(ev);
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_MOVE:
                if (mCurrView != null) {
                    float deltaX = ev.getX() - mInitialTouchPosX;
                    float deltaY = Math.abs(ev.getY() - mInitialTouchPosY);
                    // If the user has gone vertical and not gone horizontal AT
                    // LEAST minBeforeLock, switch to scroll. Otherwise, cancel
                    // the swipe.
                    if (deltaY > mMinVert && (Math.abs(deltaX)) < mMinLock) {
                        return false;
                    }
                    float minDistance = mMinSwipe;
                    if (Math.abs(deltaX) < minDistance) {
                        // Don't start the drag until at least X distance has
                        // occurred.
                        return true;
                    }
                    // don't let items that can't be dismissed be dragged more
                    // than maxScrollDistance
                    if (CONSTRAIN_SWIPE && !mCallback.canChildBeDismissed(mCurrView)) {
                        float size = getSize(mCurrAnimView);
                        float maxScrollDistance = 0.15f * size;
                        if (Math.abs(deltaX) >= size) {
                            deltaX = deltaX > 0 ? maxScrollDistance : -maxScrollDistance;
                        } else {
                            deltaX = maxScrollDistance
                                    * (float) Math.sin((deltaX / size) * (Math.PI / 2));
                        }
                    }
                    if (mAssociatedViews != null && mAssociatedViews.size() > 1) {
                        for (View v : mAssociatedViews) {
                            setTranslation(v, deltaX);
                        }
                    } else {
                        setTranslation(mCurrAnimView, deltaX);
                    }
                    if (FADE_OUT_DURING_SWIPE && mCanCurrViewBeDimissed) {
                        if (mAssociatedViews != null && mAssociatedViews.size() > 1) {
                            for (View v : mAssociatedViews) {
                                v.setAlpha(getAlphaForOffset(mCurrAnimView));
                            }
                        } else {
                            mCurrAnimView.setAlpha(getAlphaForOffset(mCurrAnimView));
                        }
                    }
                    invalidateGlobalRegion(mCurrView.getView());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mCurrView != null) {
                    float maxVelocity = MAX_DISMISS_VELOCITY * mDensityScale;
                    mVelocityTracker.computeCurrentVelocity(1000 /* px/sec */, maxVelocity);
                    float escapeVelocity = SWIPE_ESCAPE_VELOCITY * mDensityScale;
                    float velocity = getVelocity(mVelocityTracker);
                    float perpendicularVelocity = getPerpendicularVelocity(mVelocityTracker);

                    // Decide whether to dismiss the current view
                    // Tweak constants below as required to prevent erroneous
                    // swipe/dismiss
                    float translation = Math.abs(mCurrAnimView.getTranslationX());
                    float currAnimViewSize = getSize(mCurrAnimView);
                    // Long swipe = translation of .4 * width
                    boolean childSwipedFarEnough = DISMISS_IF_SWIPED_FAR_ENOUGH
                            && translation > 0.4 * currAnimViewSize;
                    // Fast swipe = > escapeVelocity and translation of .1 *
                    // width
                    boolean childSwipedFastEnough = (Math.abs(velocity) > escapeVelocity)
                            && (Math.abs(velocity) > Math.abs(perpendicularVelocity))
                            && (velocity > 0) == (mCurrAnimView.getTranslationX() > 0)
                            && translation > 0.05 * currAnimViewSize;
                    if (LOG_SWIPE_DISMISS_VELOCITY) {
                        Log.v(TAG, "Swipe/Dismiss: " + velocity + "/" + escapeVelocity + "/"
                                + perpendicularVelocity + ", x: " + translation + "/"
                                + currAnimViewSize);
                    }

                    boolean dismissChild = mCallback.canChildBeDismissed(mCurrView)
                            && (childSwipedFastEnough || childSwipedFarEnough);

                    if (dismissChild) {
                        if (mAssociatedViews != null && mAssociatedViews.size() > 1) {
                            dismissChildren(mAssociatedViews, childSwipedFastEnough ?
                                    velocity : 0f);
                        } else {
                            dismissChild(mCurrView, childSwipedFastEnough ? velocity : 0f);
                        }
                    } else {
                        // snappity
                        mCallback.onDragCancelled(mCurrView);

                        if (mAssociatedViews != null && mAssociatedViews.size() > 1) {
                            for (SwipeableItemView v : mAssociatedViews) {
                                snapChild(v, velocity);
                            }
                        } else {
                            snapChild(mCurrView, velocity);
                        }
                    }
                }
                break;
        }
        return true;
    }

    public interface Callback {
        View getChildAtPosition(MotionEvent ev);

        boolean canChildBeDismissed(SwipeableItemView v);

        void onBeginDrag(View v);

        void onChildDismissed(SwipeableItemView v);

        void onChildrenDismissed(SwipeableItemView target, Collection<ConversationItemView> v);

        void onDragCancelled(SwipeableItemView v);

        ConversationSelectionSet getSelectionSet();
    }
}
