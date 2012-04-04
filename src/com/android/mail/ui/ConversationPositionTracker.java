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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * An iterator over a conversation list that keeps track of the position of a conversation, and
 * updates the position accordingly when the underlying list data changes and the conversation
 * is in a different position.
 */
public class ConversationPositionTracker {
    /**
     * Observer for changes in {@link ConversationPositionTracker}.
     */
    public interface Observer {
        /**
         * Method that is called when the conversation positions change.
         * @param selectedConversationPos
         * @param smoothScroll
         */
        // TODO(viki) why are we passing the selected conversation positions back?
        void onPositionChanged(ConversationPositionTracker selectedConversationPos,
                boolean smoothScroll);
    }

    /** The ID of the current conversation being viewed */
    private long mConversationId;
    /** Cursor into the conversations */
    private ConversationCursor mCursor;
    /** Observers interested in change in position */
    private final List<Observer> mObservers = Lists.newArrayList();
    /** The position of the current conversation in the conversation list */
    private int mPosition;
    /** Is the current position a valid position? */
    private boolean mIsPositionValid;

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
    public ConversationPositionTracker() {
        initialize(-1, -1);
    }

    /**
     * Constructs an iterator over the underlying conversation list data backed by the specified
     * loader.
     */
    public ConversationPositionTracker(long initialConversationId, int initialPosition) {
        initialize(initialConversationId, initialPosition);
    }

    /**
     * Clears the current selected position.
     */
    public void clearPosition() {
        initialize(-1, -1);
        notifyPositionChanged(false);
    }

    /**
     * @return the current conversation position.
     */
    public Conversation getConversation() {
        if (mCursor == null || !mIsPositionValid) {
            return null;
        }
        mCursor.moveToPosition(mPosition);
        return new Conversation(mCursor);
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
        if (!hasNewer()) {
            return null;
        }

        mCursor.moveToPosition(mPosition - 1);
        return new Conversation(mCursor);
    }

    /**
     * @return the {@link Conversation} of the next newer conversation not in the selection set. If
     * no such conversation exists, this method returns null.
     */
    public Conversation getNewer(ConversationSelectionSet batchConversations) {
        if (!mIsPositionValid || !isDataLoaded()) {
            return null;
        }

        int pos = mPosition - 1;
        while (pos >= 0) {
            mCursor.moveToPosition(pos);
            final Conversation conversation = new Conversation(mCursor);
            final long id = conversation.id;
            if (!batchConversations.containsKey(id)) {
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
        if (!hasOlder()) {
            return null;
        }
        mCursor.moveToPosition(mPosition + 1);
        return new Conversation(mCursor);
    }

    /**
     * @return the {@link Conversation} of the next older conversation not in the selection set.
     */
    public Conversation getOlder(ConversationSelectionSet batchConversations) {
        if (!mIsPositionValid || !isDataLoaded()) {
            return null;
        }
        int pos = mPosition + 1;
        while (pos < mCursor.getCount()) {
            mCursor.moveToPosition(pos);
            final Conversation conversation = new Conversation(mCursor);
            final long id = conversation.id;
            if (!batchConversations.containsKey(id)) {
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
        return mPosition;
    }

    /**
     * @return whether or not there is a newer conversation in the list.
     */
    @VisibleForTesting
    boolean hasNewer() {
        return mIsPositionValid && isDataLoaded() && mCursor.moveToPosition(mPosition - 1);
    }

    /**
     * @return whether or not there is an older conversation in the list.
     */
    @VisibleForTesting
    boolean hasOlder() {
        return mIsPositionValid && isDataLoaded() && mCursor.moveToPosition(mPosition + 1);
    }

    /**
     *  Initializes the tracker with initial conversation id and initial position. This invalidates
     *  the positions in the tracker. We need a valid cursor before we can bless the position as
     *  valid. This requires a call to
     *  {@link #updateAdapterAndCursor(AnimatedAdapter, ConversationCursor)}.
     */
    public void initialize(long initialConversationId, int initialPosition) {
        mConversationId = initialConversationId;
        mPosition = initialPosition;
        // Unless we have any cursor, this is an invalid position.
        mIsPositionValid = false;
    }

    /** @return whether or not we have a valid cursor to check the position of. */
    private boolean isDataLoaded() {
        return mCursor != null && !mCursor.isClosed();
    }

    /**
     * @return whether or not the position is still a valid position in the conversation list.
     */
    public boolean isValid() {
        return mIsPositionValid && (mPosition >= 0);
    }

    /**
     * Moves to a specific position in the list.
     * @return true if the move was successful and false otherwise.
     */
    public boolean moveToPosition(int position, boolean smoothScroll) {
        if (!isDataLoaded() || !mCursor.moveToPosition(position)) {
            return false;
        }
        mPosition = position;
        mConversationId = getConversationId(mCursor);
        notifyPositionChanged(smoothScroll);
        return true;
    }

    /**
     * Let the observers know that the underlying list data has changed.
     * @param smoothScroll
     */
    private void notifyPositionChanged(boolean smoothScroll) {
        for (Observer observer : Lists.newArrayList(mObservers)) {
            observer.onPositionChanged(this, smoothScroll);
        }
    }

    private boolean onListDataChanged(AnimatedAdapter listAdapter, boolean positionIsValidBefore) {
        // Update the internal state for where the current conversation is in
        // the list.
        int conversationPosition = 0;
        while (listAdapter.getItem(conversationPosition) != null) {
            if (listAdapter.getItemId(conversationPosition) == mConversationId) {
                final boolean changed = mPosition != conversationPosition;
                mPosition = conversationPosition;
                mIsPositionValid = true;

                if (changed || !positionIsValidBefore) {
                    // Pre-emptively try to load the next cursor position so
                    // that the cursor window can be filled. The odd behavior of
                    // the ConversationCursor requires us to do this to ensure
                    // the adjacent conversation information is loaded for calls
                    // to hasNext.
                    notifyPositionChanged(!positionIsValidBefore);
                }
                return true;
            }
            conversationPosition++;
        }

        // If the conversation is no longer found in the list, try to save the same position if
        // it is still a valid position. Otherwise, go back to a valid position until we can find
        // a valid one.
        final int listSize = listAdapter.getCount();
        if (mPosition >= listSize) {
            if (listSize == 0) {
                mIsPositionValid = false;
            } else {
                mPosition = listAdapter.getCount() - 1;
            }
        }

        // Did not keep the same conversation, but could still be a valid conversation.
        if (mIsPositionValid) {
            mConversationId = listAdapter.getItemId(mPosition);
            notifyPositionChanged(true);
        }
        return false;
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
    public boolean updateAdapterAndCursor(AnimatedAdapter listAdapter, ConversationCursor cursor) {
        mCursor = cursor;
        // If we don't have a valid position, exit early.
        if (mPosition < 0) {
            return false;
        }

        if (!isDataLoaded()) {
            mIsPositionValid = false;
            return false;
        }

        // We only need to scroll the list if originally the position is not
        // valid.
        final boolean positionIsValidBefore = mIsPositionValid;
        if (listAdapter != null) {
            return onListDataChanged(listAdapter, positionIsValidBefore);
        } else {
            return onListDataChanged(mCursor, positionIsValidBefore);
        }

    }

    private boolean onListDataChanged(ConversationCursor cursor, boolean positionIsValidBefore) {
        // Update the internal state for where the current conversation is in
        // the list.
        int conversationPosition = 0;
        while (mCursor.moveToPosition(conversationPosition)) {
            if (getConversationId(mCursor) == mConversationId) {

                mPosition = conversationPosition;
                mIsPositionValid = true;

                // Pre-emptively try to load the next cursor position so that the cursor window
                // can be filled. The odd behavior of the ConversationCursor requires us to do this
                // to ensure the adjacent conversation information is loaded for calls to hasNext.
                mCursor.moveToPosition(conversationPosition + 1);

                notifyPositionChanged(!positionIsValidBefore);
                return true;
            }
            conversationPosition++;
        }
        // If the conversation is no longer found in the list, try to save the same position if
        // it is still a valid position. Otherwise, go back to a valid position until we can find
        // a valid one.
        final int listSize = mCursor.getCount();
        if (mPosition >= listSize) {
            if (listSize == 0) {
                mIsPositionValid = false;
            } else {
                mPosition = mCursor.getCount() - 1;
            }
        }

        // Did not keep the same conversation, but could still be a valid conversation.
        if (mIsPositionValid) {
            mCursor.moveToPosition(mPosition);
            mConversationId = getConversationId(mCursor);
            notifyPositionChanged(true);
        }
        return false;
    }

    /**
     * Registers an observer to be notified when the selected conversation position changed.
     */
    public void registerObserver(Observer observer) {
        mObservers.add(observer);
    }

    /**
     * Unregisters an observer.
     */
    public void unregisterObserver(Observer observer) {
        mObservers.remove(observer);
    }
}