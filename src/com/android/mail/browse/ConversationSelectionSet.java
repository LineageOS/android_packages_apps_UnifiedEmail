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

import com.android.mail.providers.Conversation;
import com.android.mail.providers.UIProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;


/**
 * A simple thread-safe wrapper over a set of conversations representing a selection set (e.g.
 * in a conversation list). This class dispatches changes when the set goes empty, and when it
 * becomes unempty.
 *
 * For simplicity, this class <b>does not allow modifications</b> to the collection in observers
 * when responding to change events.
 *
 * In the Unified codebase, this is called ui.ConversationSelectionSet
 */
public class ConversationSelectionSet implements Parcelable {
    private final HashMap<Long, Conversation> mInternalMap =
            new HashMap<Long, Conversation>();

    /**
     * Whether or not change notifications should be suppressed. This is used for batch updates.
     */
    private boolean mLockedForChanges = false;

    @VisibleForTesting
    final ArrayList<ConversationSetObserver> mObservers = new ArrayList<ConversationSetObserver>();

    /**
     * Observers which can register on changes on this set.
     * Observers are required to ensure that no modification is done to the set when handling
     * events.
     *
     * In the Unified codebase, this is a separate interface in ui.ConversationSetObserver
     */
    public interface ConversationSetObserver {
        /**
         * Handle when the selection set becomes empty. The observer should not make any
         * modifications to the set while handling this event.
         */
        void onSetEmpty(ConversationSelectionSet set);

        /**
         * Handle when the selection set becomes unempty. The observer should not make any
         * modifications to the set while handling this event.
         */
        void onSetBecomeUnempty(ConversationSelectionSet set);

        /**
         * Handle when the selection set gets an element added or removed. The observer should not
         * make any modifications to the set while handling this event.
         *
         * The set makes no guarantees that this method will get dispatched for each addition or
         * removal for batch operations such as {@link #clear}, but will guarantee that it will
         * get called at least once.
         */
        void onSetChanged(ConversationSelectionSet set);
    }

    /**
     * Registers an observer to listen for interesting changes on this set.
     * @param observer the observer to register.
     */
    public synchronized void addObserver(ConversationSetObserver observer) {
        mObservers.add(observer);
    }

    /**
     * Unregisters an observer for change events.
     * @param observer the observer to unregister.
     */
    public synchronized void removeObserver(ConversationSetObserver observer) {
        mObservers.remove(observer);
    }

    private synchronized void dispatchOnChange(ArrayList<ConversationSetObserver> observers) {
        // Copy observers so that they may unregister themselves as listeners on event handling.
        for (ConversationSetObserver observer : observers) {
            observer.onSetChanged(this);
        }
    }

    private synchronized void dispatchOnEmpty(ArrayList<ConversationSetObserver> observers) {
        for (ConversationSetObserver observer : observers) {
            observer.onSetEmpty(this);
        }
    }

    private synchronized void dispatchOnBecomeUnempty(
            ArrayList<ConversationSetObserver> observers) {
        for (ConversationSetObserver observer : observers) {
            observer.onSetBecomeUnempty(this);
        }
    }

    /** @see java.util.HashMap#get */
    public synchronized Conversation get(Long id) {
        return mInternalMap.get(id);
    }

    /** @see java.util.HashMap#put */
    public synchronized void put(Long id, Conversation info) {
        boolean initiallyEmpty =  mInternalMap.isEmpty();
        mInternalMap.put(id, info);

        if (mLockedForChanges) {
            return;
        }

        ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
        dispatchOnChange(observersCopy);
        if (initiallyEmpty) {
            dispatchOnBecomeUnempty(observersCopy);
        }
    }

    /** @see java.util.HashMap#remove */
    public synchronized void remove(Long id) {
        boolean initiallyNotEmpty = !mInternalMap.isEmpty();
        mInternalMap.remove(id);

        if (mLockedForChanges) {
            return;
        }

        ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
        dispatchOnChange(observersCopy);
        if (mInternalMap.isEmpty() && initiallyNotEmpty) {
            dispatchOnEmpty(observersCopy);
        }
    }

    /** @see java.util.HashMap#isEmpty */
    public synchronized boolean isEmpty() {
        return mInternalMap.isEmpty();
    }

    /** @see java.util.HashMap#size */
    public synchronized int size() {
        return mInternalMap.size();
    }

    /** @see java.util.HashMap#keySet */
    public synchronized Collection<Long> keySet() {
        return mInternalMap.keySet();
    }

    /** @see java.util.HashMap#values */
    public synchronized Collection<Conversation> values() {
        return mInternalMap.values();
    }

    /** @see java.util.HashMap#containsKey */
    public synchronized boolean containsKey(Long key) {
        return mInternalMap.containsKey(key);
    }

    /** @see java.util.HashMap#clear */
    public synchronized void clear() {
        boolean initiallyNotEmpty = !mInternalMap.isEmpty();
        mInternalMap.clear();

        if (mLockedForChanges) {
            return;
        }

        if (mInternalMap.isEmpty() && initiallyNotEmpty) {
            ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
            dispatchOnChange(observersCopy);
            dispatchOnEmpty(observersCopy);
        }
    }

    /**
     * Iterates through a cursor of conversations and ensures that the current set is present
     * within the result set denoted by the cursor. Any conversations not foun in the result set
     * is removed from the collection.
     */
    public synchronized void validateAgainstCursor(Cursor cursor) {
        if (isEmpty()) {
            return;
        }

        if (cursor == null) {
            clear();
            return;
        }

        // Get the current position of the cursor, so it can be reset
        int currentPosition = cursor.getPosition();
        if (currentPosition != -1) {
            // Validate batch selection across all conversations, not just the most
            // recently loaded set.  See bug 2405138 (Batch selection cleared when
            // additional conversations are loaded on demand).
            cursor.moveToPosition(-1);
        }

        // The query has run, but we have been in the list
        // Make sure that the list of selected conversations
        // contains only items that are in the result set
        ArrayList<Long> selectedConversationsToToggle = new ArrayList<Long>(keySet());

        // Go through the list of what we think is selected,
        // if any of the conversations are not present,
        // untoggle them from the list
        //
        // Only continue going through the list while we are unsure
        // if a conversation is selected.  If we don't stop when
        // there are no more items in the selectedConversationsToToggle
        // collection, this will force the whole collection list to be loaded
        // If we believe that there is at least one conversation selected,
        // we need to keep looking to make sure that the conversation is still
        // present
        while (!selectedConversationsToToggle.isEmpty() && cursor.moveToNext()) {
            selectedConversationsToToggle.remove(cursor.getLong(UIProvider.CONVERSATION_ID_COLUMN));
        }

        boolean changed = false;

        // Now toggle the conversations that are incorrectly still selected
        mLockedForChanges = true;
        for (long conversationId : selectedConversationsToToggle) {
            // deselectConversation doesn't update the view, which is OK here, as
            // the view, if visible, will be removed
            remove(conversationId);
            changed = true;
        }
        mLockedForChanges = false;

        ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
        if (changed) {
            dispatchOnChange(observersCopy);
        }

        if (isEmpty()) {
            dispatchOnEmpty(observersCopy);
        }

        cursor.moveToPosition(currentPosition);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Conversation[] values = values().toArray(new Conversation[size()]);
        dest.writeParcelableArray(values, flags);
    }

    public static final Parcelable.Creator<ConversationSelectionSet> CREATOR =
            new Parcelable.Creator<ConversationSelectionSet>() {

        @Override
        public ConversationSelectionSet createFromParcel(Parcel source) {
            ConversationSelectionSet result = new ConversationSelectionSet();
            Parcelable[] conversations = source.readParcelableArray(
                    Conversation.class.getClassLoader());
            for (Parcelable parceled : conversations) {
                Conversation conversation = (Conversation) parceled;
                result.put(conversation.id, conversation);
            }
            return result;
        }

        @Override
        public ConversationSelectionSet[] newArray(int size) {
            return new ConversationSelectionSet[size];
        }
    };

    /**
     * Toggles the given conversation in the selection set.
     */
    public synchronized void toggle(Conversation conversation) {
        long conversationId = conversation.id;
        if (containsKey(conversationId)) {
            remove(conversationId);
        } else {
            put(conversationId, conversation);
        }
    }
}
