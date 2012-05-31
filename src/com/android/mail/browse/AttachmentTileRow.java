/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.android.mail.providers.Attachment;

import java.util.List;

/**
 * Acts as a row item composed of {@link MessageAttachmentTile}s.
 */
public class AttachmentTileRow extends FrameLayout {
    private int mColumnCount;
    private LayoutInflater mInflater;
    private Uri mAttachmentsListUri;

    public AttachmentTileRow(Context context, Uri attachmentsListUri, int columnCount) {
        super(context);
        mColumnCount = columnCount;
        mInflater = LayoutInflater.from(context);
        mAttachmentsListUri = attachmentsListUri;
    }

    /**
     * Configures the row to add {@link Attachment}s information to the views
     */
    public void configureRow(List<Attachment> list, int rowIndex) {
        // Adding tiles to row and filling in attachment information
        for (int columnCounter = 0; columnCounter < mColumnCount; columnCounter++) {
            Attachment attachment =
                    columnCounter < list.size() ? list.get(columnCounter) : null;
            addTileFromEntry(attachment, columnCounter, rowIndex);
        }
    }

    private void addTileFromEntry(Attachment attachment, int columnIndex, int rowIndex) {
        final MessageAttachmentTile attachmentTile;

        if (getChildCount() <= columnIndex) {
            attachmentTile = MessageAttachmentTile.inflate(mInflater, this);
            addView(attachmentTile);
        } else {
            attachmentTile = (MessageAttachmentTile) getChildAt(columnIndex);
        }

        attachmentTile.render(attachment, mAttachmentsListUri,
                mColumnCount*rowIndex + columnIndex); // determine the attachment's index
                                                      // using number of columns and the
                                                      // current row and column
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        onLayoutForTiles();
    }

    private void onLayoutForTiles() {
        final int count = getChildCount();

        // Just line up children horizontally.
        int childLeft = 0;
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);

            // Note MeasuredWidth includes the padding.
            final int childWidth = child.getMeasuredWidth();
            child.layout(childLeft, 0, childLeft + childWidth, child.getMeasuredHeight());
            childLeft += childWidth;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        onMeasureForTiles(widthMeasureSpec);
    }

    private void onMeasureForTiles(int widthMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);

        final int childCount = getChildCount();
        if (childCount == 0) {
            // Just in case...
            setMeasuredDimension(width, 0);
            return;
        }

        // 1. Calculate image size.
        //      = [total width] / [child count]
        //
        // 2. Set it to width/height of each children.
        //    If we have a remainder, some tiles will have 1 pixel larger width than its height.
        //
        // 3. Set the dimensions of itself.
        //    Let width = given width.
        //    Let height = image size + bottom paddding.
        final int imageSize = (width) / mColumnCount;
        final int remainder = width - (imageSize * mColumnCount);

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final int childWidth = imageSize + child.getPaddingRight()
                    // Compensate for the remainder
                    + (i < remainder ? 1 : 0);
            final int childHeight = imageSize + child.getPaddingBottom();
            child.measure(
                    MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                    );
        }
        setMeasuredDimension(width, imageSize + getChildAt(0).getPaddingBottom());
    }

    @Override
    public void sendAccessibilityEvent(int eventType) {
        // This method is called when the child tile is INVISIBLE (meaning "empty"), and the
        // Accessibility Manager needs to find alternative content description to speak.
        // Here, we ignore the default behavior, since we don't want to let the manager speak
        // a contact name for the tile next to the INVISIBLE tile.
    }
}
