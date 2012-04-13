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

import android.database.Cursor;

import com.android.mail.browse.ConversationCursor;
import com.android.mail.providers.Conversation;
import com.android.mail.utils.LogUtils;

import com.google.common.annotations.VisibleForTesting;

/**
 * An iterator over a conversation list that keeps track of the position of a conversation, and
 * updates the position accordingly when the underlying list data changes and the conversation
 * is in a different position.
 */
public class ConversationPositionTracker {
    protected static final String LOG_TAG = new LogUtils().getLogTag();

    /** Cursor into the conversations */
    private ConversationCursor mCursor = null;
    /** Did we recalculate positions after updating the cursor? */
    private boolean mCursorDirty = false;
    /** The currently selected conversation */
    private Conversation mConversation;
    /** The selected set */
    private final ConversationSelectionSet mSelectedSet;

    /**
     * This utility method returns the conversation ID at the current cursor position.
     * @return the conversation id at the cursor.
     */
    private static long getConversationId(Cursor cursor) {
        final Conversation conversation = new Conversation(cursor);
        return conversation.id;
    }

    /**
     * Constructs a position tracker that doesn't point to any specific conversation.
     */
    public ConversationPositionTracker(ConversationSelectionSet selectedSet) {
        mSelectedSet = selectedSet;
    }

    /**
     * Clears the current selected position.
     */
    public void clearPosition() {
        initialize(null);
    }

    /** Move cursor to a specific position and return the conversation there */
    private Conversation conversationAtPosition(int position){
        mCursor.moveToPosition(position);
        final Conversation conv = new Conversation(mCursor);
        conv.position = position;
        return conv;
    }

    /**
     * @return the total number of conversations in the list.
     */
    public int getCount() {
        if (isDataLoaded()) {
            return mCursor.getCount();
        } else {
            return 0;
        }
    }

    /**
     * @return the {@link Conversation} of the newer conversation by one position. If no such
     * conversation exists, this method returns null.
     */
    public Conversation getNewer() {
        calculatePosition();
        if (!hasNewer()) {
            return null;
        }
        return conversationAtPosition(mConversation.position - 1);
    }

    /**
     * @return the {@link Conversation} of the next newer conversation not in the selection set. If
     * no such conversation exists, this method returns null.
     */
    public Conversation getNewerUnselected() {
        calculatePosition();
        if (!isDataLoaded()) {
            return null;
        }

        int pos = mConversation.position - 1;
        while (pos >= 0) {
            final Conversation conversation = conversationAtPosition(pos);
            final long id = conversation.id;
            if (!mSelectedSet.containsKey(id)) {
                return conversation;
            }
            pos--;
        }
        return null;
    }

    /**
     * @return the {@link Conversation} of the older conversation by one spot. If no such
     * conversation exists, this method returns null.
     */
    public Conversation getOlder() {
        calculatePosition();
        if (!hasOlder()) {
            return null;
        }
        return conversationAtPosition(mConversation.position + 1);
    }

    /**
     * @return the {@link Conversation} of the next older conversation not in the selection set.
     */
    public Conversation getOlderUnselected() {
        calculatePosition();
        if (!isDataLoaded()) {
            return null;
        }
        int pos = mConversation.position + 1;
        while (pos < mCursor.getCount()) {
            final Conversation conversation = conversationAtPosition(pos);
            final long id = conversation.id;
            if (!mSelectedSet.containsKey(id)) {
                return conversation;
            }
            pos++;
        }
        return null;
    }

    /**
     * @return the current conversation position in the list.
     */
    public int getPosition() {
        calculatePosition();
        return mConversation.position;
    }

    /**
     * @return whether or not there is a newer conversation in the list.
     */
    @VisibleForTesting
    boolean hasNewer() {
        calculatePosition();
        return isDataLoaded() && mCursor.moveToPosition(mConversation.position - 1);
    }

    /**
     * @return whether or not there is an older conversation in the list.
     */
    @VisibleForTesting
    boolean hasOlder() {
        calculatePosition();
        return isDataLoaded() && mCursor.moveToPosition(mConversation.position + 1);
    }

    /**
     *  Initializes the tracker with initial conversation id and initial position. This invalidates
     *  the positions in the tracker. We need a valid cursor before we can bless the position as
     *  valid. This requires a call to
     *  {@link #updateCursor(ConversationCursor)}.
     */
    public void initialize(Conversation conversation) {
        final String d = (conversation == null) ? "NOOL" : conversation.toString();
        mConversation = conversation;
        mCursorDirty = true;
    }

    /** @return whether or not we have a valid cursor to check the position of. */
    private boolean isDataLoaded() {
        return mCursor != null && !mCursor.isClosed();
    }

    /**
     * Updates the underlying data when the conversation list changes. This class will try to find
     * the existing conversation and update the position if the conversation is found. If the
     * conversation that was pointed to by the existing position was not found, it will find the
     * next valid possible conversation, though if none is found, it may become invalid.
     *
     * @return Whether or not the same conversation was found after the update and this position
     *     tracker is in a valid state.
     */
    public void updateCursor(ConversationCursor cursor) {
        mCursor = cursor;
        // Now we should run applyCursor before proceeding.
        mCursorDirty = true;
    }

    /**
     * Recalculate the current position based on the cursor. This needs to be done once for
     * each (Conversation, Cursor) pair. We could do this on every change of conversation or
     * cursor, but that would be wasteful, since the recalculation of position is only required
     * when transitioning to the next conversation. Transitions don't happen frequently, but
     * changes in conversation and cursor do. So we defer this till it is actually needed.
     *
     * This method could change the current conversation if it cannot find the current conversation
     * in the cursor. When this happens, this method sets the current conversation to some safe
     * value and logs the reasons why it couldn't find the conversation.
     *
     * Calling this method repeatedly is safe: it returns early if it detects it has already been
     * called.
     */
    private void calculatePosition() {
        // Run this method once for a mConversation, mCursor pair.
        if (!mCursorDirty) {
            return;
        }
        mCursorDirty = false;

        // If we don't have a valid position, exit early.
        if (mConversation.position < 0) {
            return;
        }

        final int listSize = (mCursor == null) ? 0 : mCursor.getCount();
        if (!isDataLoaded() || listSize == 0) {
            return;
        }
        // Update the internal state for where the current conversation is in
        // the list.  Start from the beginning and find the current conversation in it.
        int newPosition = 0;
        while (mCursor.moveToPosition(newPosition)) {
            if (getConversationId(mCursor) == mConversation.id) {
                mConversation.position = newPosition;
                final boolean changed = (mConversation.position != newPosition);
                // Pre-emptively try to load the next cursor position so that the cursor window
                // can be filled. The odd behavior of the ConversationCursor requires us to do this
                // to ensure the adjacent conversation information is loaded for calls to hasNext.
                mCursor.moveToPosition(newPosition + 1);
                return;
            }
            newPosition++;
        }
        // If the conversation is no longer found in the list, try to save the same position if
        // it is still a valid position. Otherwise, go back to a valid position until we can find
        // a valid one.
        if (mConversation.position >= listSize) {
            // Go to the last position since our expected position is past this somewhere.
            newPosition = mCursor.getCount() - 1;
        }

        // Did not keep the same conversation, but could still be a valid conversation.
        if (isDataLoaded()){
            LogUtils.d(LOG_TAG, "ConversationPositionTracker: Could not find conversation %s" +
                    " in the cursor. Moving to position %d ", mConversation.toString(),
                    newPosition);
            mCursor.moveToPosition(newPosition);
            mConversation = new Conversation(mCursor);
        }
        return;
    }
}