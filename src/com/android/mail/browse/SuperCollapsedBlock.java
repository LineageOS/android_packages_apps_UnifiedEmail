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

package com.android.mail.browse;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationViewAdapter.SuperCollapsedBlockItem;
import com.android.mail.utils.LogTag;

/**
 * A header block that expands to a list of collapsed message headers. Will notify a listener on tap
 * so the listener can hide the block and reveal the corresponding collapsed message headers.
 *
 */
public class SuperCollapsedBlock extends FrameLayout implements View.OnClickListener {

    public interface OnClickListener {
        /**
         * Handle a click on a super-collapsed block.
         *
         */
        void onSuperCollapsedClick(SuperCollapsedBlockItem item);
    }

    private SuperCollapsedBlockItem mModel;
    private OnClickListener mClick;
    private View mIconView;
    private TextView mCountView;
    private View mBackgroundView;

    private static final String LOG_TAG = LogTag.getLogTag();

    public SuperCollapsedBlock(Context context) {
        this(context, null);
    }

    public SuperCollapsedBlock(Context context, AttributeSet attrs) {
        super(context, attrs);
        setActivated(false);
        setOnClickListener(this);
    }

    public void initialize(OnClickListener onClick) {
        mClick = onClick;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIconView = findViewById(R.id.super_collapsed_icon);
        mCountView = (TextView) findViewById(R.id.super_collapsed_count);
        mBackgroundView = findViewById(R.id.super_collapsed_background);

        // Work around Honeycomb bug where BitmapDrawable's tileMode is unreliable in XML (5160739)
        BitmapDrawable bd = (BitmapDrawable) getResources().getDrawable(
                R.drawable.header_convo_view_thread_bg_holo);
        bd.setTileModeXY(TileMode.REPEAT, TileMode.REPEAT);
        mBackgroundView.setBackgroundDrawable(bd);
    }

    public void bind(SuperCollapsedBlockItem item) {
        mModel = item;
        setCount(item.getEnd() - item.getStart() + 1);
    }

    public void setCount(int count) {
        mCountView.setText(Integer.toString(count));
        mIconView.getBackground().setLevel(count);
    }

    @Override
    public void onClick(final View v) {
        ((TextView) findViewById(R.id.super_collapsed_label)).setText(
                R.string.loading_conversation);
        mCountView.setVisibility(GONE);

        if (mClick != null) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    mClick.onSuperCollapsedClick(mModel);
                }
            });
        }
    }

    public static int getCannedHeight(Context context) {
        Resources r = context.getResources();
        // Rather than try to measure the height a super-collapsed block, just add up the known
        // vertical dimension components.
        return r.getDimensionPixelSize(R.dimen.super_collapsed_height)
                + r.getDimensionPixelOffset(R.dimen.message_header_vertical_margin);
    }

}
