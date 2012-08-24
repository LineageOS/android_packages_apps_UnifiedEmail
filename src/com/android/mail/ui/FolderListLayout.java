package com.android.mail.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * temporary annonated FrameLayout to help find cases of b/6946182
 */
public class FolderListLayout extends FrameLayout {

    public FolderListLayout(Context c) {
        this(c, null);
    }

    public FolderListLayout(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        LogUtils.d(Utils.VIEW_DEBUGGING_TAG, "FolderListLayout(%s).onMeasure() called", this);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        LogUtils.d(Utils.VIEW_DEBUGGING_TAG, "FolderListLayout(%s).onLayout() called", this);
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    public void requestLayout() {
        Utils.checkRequestLayout(this);
        super.requestLayout();
    }

}
