package com.android.mail.ui;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Folder;

import android.animation.ObjectAnimator;
import android.app.LoaderManager;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * A tip to educate users about long press to enter CAB mode.  Appears on top of conversation list.
 */
// TODO: this class was shamelessly copied from ConversationPhotoTeaserView.  Look into
// extracting a common base class.
public class ConversationLongPressTipView extends FrameLayout
        implements ConversationSpecialItemView, SwipeableItemView {

    private static int sScrollSlop = 0;
    private static int sShrinkAnimationDuration;

    private final MailPrefs mMailPrefs;
    private AnimatedAdapter mAdapter;

    private View mSwipeableContent;

    private boolean mShow;
    private int mAnimatedHeight = -1;

    public ConversationLongPressTipView(final Context context) {
        this(context, null);
    }

    public ConversationLongPressTipView(final Context context, final AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public ConversationLongPressTipView(
            final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        final Resources resources = context.getResources();

        if (sScrollSlop == 0) {
            sScrollSlop = resources.getInteger(R.integer.swipeScrollSlop);
            sShrinkAnimationDuration = resources.getInteger(
                    R.integer.shrink_animation_duration);
        }

        mMailPrefs = MailPrefs.get(context);
    }

    @Override
    protected void onFinishInflate() {
        mSwipeableContent = findViewById(R.id.swipeable_content);

        findViewById(R.id.dismiss_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    @Override
    public void onUpdate(String account, Folder folder, ConversationCursor cursor) {
        // It's possible user has enabled/disabled sender images in settings, which affects
        // whether we want to show this tip or not.
        mShow = checkWhetherToShow();
    }

    @Override
    public boolean getShouldDisplayInList() {
        mShow = checkWhetherToShow();
        return mShow;
    }

    private boolean checkWhetherToShow() {
        // show if 1) sender images are disabled 2) there are items
        return !shouldShowSenderImage() && !mAdapter.isEmpty()
                && !mMailPrefs.isLongPressToSelectTipAlreadyShown();
    }

    @Override
    public int getPosition() {
        // We want this teaser to go before the first real conversation
        // If another teaser wants position 0, we will want position 1
        return mAdapter.getPositionOffset(0);
    }

    @Override
    public void setAdapter(AnimatedAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public void bindLoaderManager(LoaderManager loaderManager) {
    }

    @Override
    public void cleanup() {
    }

    @Override
    public void onConversationSelected() {
        // DO NOTHING
    }

    @Override
    public void onCabModeEntered() {
        dismiss();
    }


    @Override
    public boolean acceptsUserTaps() {
        // No, we don't allow user taps.
        return false;
    }

    @Override
    public void dismiss() {
        setDismissed();
        startDestroyAnimation();
    }

    private void setDismissed() {
        if (mShow) {
            mMailPrefs.setLongPressToSelectTipAlreadyShown();
            mShow = false;
        }
    }

    protected boolean shouldShowSenderImage() {
        return mMailPrefs.getShowSenderImages();
    }

    @Override
    public SwipeableView getSwipeableView() {
        return SwipeableView.from(mSwipeableContent);
    }

    @Override
    public boolean canChildBeDismissed() {
        return true;
    }

    @Override
    public float getMinAllowScrollDistance() {
        return sScrollSlop;
    }

    private void startDestroyAnimation() {
        final int start = getHeight();
        final int end = 0;
        mAnimatedHeight = start;
        final ObjectAnimator heightAnimator =
                ObjectAnimator.ofInt(this, "animatedHeight", start, end);
        heightAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        heightAnimator.setDuration(sShrinkAnimationDuration);
        heightAnimator.start();
    }

    /**
     * This method is used by the animator.  It is explicitly kept in proguard.flags to prevent it
     * from being removed, inlined, or obfuscated.
     * Edit ./packages/apps/UnifiedEmail/proguard.flags
     * In the future, we want to use @Keep
     */
    public void setAnimatedHeight(final int height) {
        mAnimatedHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (mAnimatedHeight == -1) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mAnimatedHeight);
        }
    }

}
