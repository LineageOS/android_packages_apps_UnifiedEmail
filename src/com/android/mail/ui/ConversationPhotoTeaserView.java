package com.android.mail.ui;

import android.animation.ObjectAnimator;
import android.app.LoaderManager;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Folder;

/**
 * A teaser to introduce people to the contact photo check boxes
 */
public class ConversationPhotoTeaserView extends FrameLayout
        implements ConversationSpecialItemView, SwipeableItemView {
    private static int sScrollSlop = 0;
    private static int sShrinkAnimationDuration;

    private final MailPrefs mMailPrefs;
    private AnimatedAdapter mAdapter;

    private View mSwipeableContent;

    private boolean mShown;
    private int mAnimatedHeight = -1;
    private boolean mNeedLayout;
    private int mTextTop;

    public ConversationPhotoTeaserView(final Context context) {
        this(context, null);
    }

    public ConversationPhotoTeaserView(final Context context, final AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public ConversationPhotoTeaserView(
            final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        final Resources resources = context.getResources();

        synchronized (ConversationPhotoTeaserView.class) {
            if (sScrollSlop == 0) {
                sScrollSlop = resources.getInteger(R.integer.swipeScrollSlop);
                sShrinkAnimationDuration = resources.getInteger(
                        R.integer.shrink_animation_duration);
            }
        }

        mMailPrefs = MailPrefs.get(context);

        mNeedLayout = true;
    }

    @Override
    protected void onFinishInflate() {
        mSwipeableContent = findViewById(R.id.swipeable_content);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        final TextView text = (TextView) findViewById(R.id.text);
        final ImageView arrow = (ImageView) findViewById(R.id.arrow);

        // We post to avoid calling layout within layout
        arrow.post(new Runnable() {
            @Override
            public void run() {

                // The text top is changed when we move the arrow, so we need to
                // do multiple passes
                int textTop = text.getTop();
                if (mNeedLayout || textTop != mTextTop) {
                    mNeedLayout = false;
                    mTextTop = textTop;

                    final int lineHeight = text.getLineHeight();
                    final LinearLayout.LayoutParams arrowParams = (LinearLayout.LayoutParams) arrow
                            .getLayoutParams();
                    arrowParams.topMargin = mTextTop + lineHeight / 2;
                    arrow.setLayoutParams(arrowParams);
                }
                arrow.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onUpdate(String account, Folder folder, ConversationCursor cursor) {
    }

    @Override
    public boolean getShouldDisplayInList() {
        // show if 1) sender images are enabled 2) there are items
        mShown = shouldShowSenderImage() && !mAdapter.isEmpty()
                && !mMailPrefs.isConversationPhotoTeaserAlreadyShown();
        return mShown;
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
    public void dismiss() {
        setDismissed();
        startDestroyAnimation();
    }

    private void setDismissed() {
        if (mShown) {
            mMailPrefs.setConversationPhotoTeaserAlreadyShown();
            mShown = false;
        }
    }

    protected boolean shouldShowSenderImage() {
        return false;
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
