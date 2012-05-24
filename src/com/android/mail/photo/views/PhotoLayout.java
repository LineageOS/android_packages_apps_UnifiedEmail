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

package com.android.mail.photo.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.android.mail.R;

/**
 * Custom layout for the photo view.
 * <p>
 * The photo view gives the photo a dynamic height -- it always takes up whatever's left of the
 * screen. A normal {@link LinearLayout} does not allow this [at least not in the context of a
 * list]. So, we create a layout that can fix it's height and ensures its children [such as the
 * photo itself] are sized appropriately.
 */
public class PhotoLayout extends LinearLayout {
    /** The fixed height of this view. If {@code -1}, calculate the height */
    private int mFixedHeight = -1;
    /** The view containing primary photo information */
    private PhotoView mPhotoView;

    public PhotoLayout(Context context) {
        super(context);
    }

    public PhotoLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PhotoLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void addView(View child, int index, android.view.ViewGroup.LayoutParams params) {
        super.addView(child, index, params);

        if (child.getId() == R.id.photo_view) {
            mPhotoView = (PhotoView) child;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setFixedHeight(mFixedHeight);
    }

    /**
     * Clears any state or resources from the views. The layout cannot be used after this method
     * is called.
     */
    public void clear() {
        removeAllViews();
        mPhotoView = null;
    }

    /**
     * Sets the fixed height for this layout. If the given height is <= 0, it is ignored.
     */
    public void setFixedHeight(int fixedHeight) {
        if (fixedHeight <= 0) {
            return;
        }

        final boolean adjustBounds = (fixedHeight != mFixedHeight);
        mFixedHeight = fixedHeight;

        if (mPhotoView != null) {
            int adjustHeight = 0;
            mPhotoView.setFixedHeight(mFixedHeight - adjustHeight);
        }
        setMeasuredDimension(getMeasuredWidth(), mFixedHeight);

        if (adjustBounds) {
            requestLayout();
        }
    }
}
