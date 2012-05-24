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

package com.android.mail.photo.adapters;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.view.View;
import android.view.ViewGroup;

/**
 * The base cursor adapter
 */
public class BaseCursorAdapter extends CursorAdapter {
    /**
     * Constructor
     *
     * @param context The context
     * @param cursor The cursor
     */
    public BaseCursorAdapter(Context context, Cursor cursor) {
        super(context, cursor, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int position, View convertView, ViewGroup viewGroup) {

        // Since there is a bug in the framework that causes AbstractCursor.obtainView()
        // to sometimes call this method with a position outside the bounds of
        // the adapter, perform a check to prevent the IllegalStateException.
        // See http://b/5147237
        if (position >= getCount()) {
            return convertView == null ? newView(mContext, getCursor(), viewGroup) : convertView;
        }

        return super.getView(position, convertView, viewGroup);
    }

    /**
     * Get the view from the specified position
     *
     * @param pos The position
     *
     * @return The view
     */
    public View getViewFromPos(int pos) {
        return null;
    }

    /**
     * Called when the activity pauses
     */
    public void onPause() {
    }

    /**
     * Called when the activity resumes
     */
    public void onResume() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        if (getCursor() == null) {
            return true;
        } else {
            return super.isEmpty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return null;
    }
}
