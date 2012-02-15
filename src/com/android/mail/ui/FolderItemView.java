/**
 * Copyright (c) 2012, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.ui;

import com.android.mail.R;

import android.widget.TextView;

import com.android.mail.providers.Folder;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.widget.RelativeLayout;

/**
 * The view for each label in the label list.
 */
public class FolderItemView extends RelativeLayout {
    // Static colors
    private static int NON_DROPPABLE_TARGET_TEXT_COLOR;

    // Static bitmap
    private static Bitmap SHORTCUT_ICON;

    // These are fine to be static, as these Drawables only have one state
    private static Drawable DROPPABLE_HOVER_BACKGROUND;
    private static Drawable DRAG_STEADY_STATE_BACKGROUND;

    private Drawable mBackground;
    private ColorStateList mInitialLabelTextColor;
    private ColorStateList mInitialUnreadCountTextColor;

    private Folder mFolder;
    private TextView mFolderTextView;
    private TextView mUnreadCountTextView;
    private DropHandler mDropHandler;


    /**
     * A delegate for a handler to handle a drop of an item.
     */
    public interface DropHandler {
        /**
         * Return whether or not the drag event is supported by the drop handler. The
         *     {@code FolderItemView} will present appropriate visual affordances if the drag is
         *     supported.
         */
        boolean supportsDrag(DragEvent event, Folder folder);

        /**
         * Handles a drop event, applying the appropriate logic.
         */
        void handleDrop(DragEvent event, Folder folder);
    }

    public FolderItemView(Context context) {
        super(context);
    }

    public FolderItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FolderItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (SHORTCUT_ICON == null) {
            final Resources res = getResources();
            SHORTCUT_ICON = BitmapFactory.decodeResource(
                    res, R.mipmap.ic_launcher_shortcut_folder);
            DROPPABLE_HOVER_BACKGROUND =
                    res.getDrawable(R.drawable.folder_drag_target);
            DRAG_STEADY_STATE_BACKGROUND =
                    res.getDrawable(R.drawable.folder_no_hover);
            NON_DROPPABLE_TARGET_TEXT_COLOR =
                    res.getColor(R.color.folder_disabled_drop_target_text_color);
        }
        mFolderTextView = (TextView)findViewById(R.id.name);
        mUnreadCountTextView = (TextView)findViewById(R.id.unread);
        mBackground = getBackground();
        mInitialLabelTextColor = mFolderTextView.getTextColors();
        mInitialUnreadCountTextColor = mUnreadCountTextView.getTextColors();
    }

    public void bind(Folder folder, DropHandler dropHandler) {
        mFolder = folder;
        mDropHandler = dropHandler;
        mFolderTextView.setText(folder.name);
    }

    private boolean isDroppableTarget(DragEvent event) {
        return (mDropHandler != null && mDropHandler.supportsDrag(event, mFolder));
    }

    /**
     * Handles the drag event.
     *
     * @param event the drag event to be handled
     */
    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                // If this label is not a drop target, dim the text.
                if (!isDroppableTarget(event)) {
                    // Make sure we update this at the time we drop on the target.
                    mInitialLabelTextColor = mFolderTextView.getTextColors();
                    mInitialUnreadCountTextColor = mUnreadCountTextView.getTextColors();
                    mFolderTextView.setTextColor(NON_DROPPABLE_TARGET_TEXT_COLOR);
                    mUnreadCountTextView.setTextColor(NON_DROPPABLE_TARGET_TEXT_COLOR);
                }
                // Set the background to a steady state background.
                setBackgroundDrawable(DRAG_STEADY_STATE_BACKGROUND);
                return true;

            case DragEvent.ACTION_DRAG_ENTERED:
                // Change background color to indicate this label is the drop target.
                if (isDroppableTarget(event)) {
                    setBackgroundDrawable(DROPPABLE_HOVER_BACKGROUND);
                    return true;
                }
                break;

            case DragEvent.ACTION_DRAG_EXITED:
                // If this is a droppable target, make sure that it is set back to steady state,
                // when the drag leaves the view.
                if (isDroppableTarget(event)) {
                    setBackgroundDrawable(DRAG_STEADY_STATE_BACKGROUND);
                    return true;
                }
                break;

            case DragEvent.ACTION_DRAG_ENDED:
                // Reset the text of the non draggable views back to the color it had been..
                if (!isDroppableTarget(event)) {
                    mFolderTextView.setTextColor(mInitialLabelTextColor);
                    mUnreadCountTextView.setTextColor(mInitialUnreadCountTextColor);
                }
                // Restore the background of the view.
                setBackgroundDrawable(mBackground);
                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                return true;

            case DragEvent.ACTION_DROP:
                if (mDropHandler == null) {
                    return false;
                }

                mDropHandler.handleDrop(event, mFolder);
                return true;
        }
        return false;
    }
}
