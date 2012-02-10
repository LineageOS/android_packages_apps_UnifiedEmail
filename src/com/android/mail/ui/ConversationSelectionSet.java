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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.mail.providers.Conversation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * A simple thread-safe wrapper over a set of conversations representing a
 * selection set (e.g. in a conversation list). This class dispatches changes
 * when the set goes empty, and when it becomes unempty. For simplicity, this
 * class <b>does not allow modifications</b> to the collection in observers when
 * responding to change events.
 */
public class ConversationSelectionSet implements Parcelable {
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

    private final HashMap<Long, Conversation> mInternalMap =
            new HashMap<Long, Conversation>();

    @VisibleForTesting
    final ArrayList<ConversationSetObserver> mObservers = new ArrayList<ConversationSetObserver>();

    /**
     * Registers an observer to listen for interesting changes on this set.
     *
     * @param observer the observer to register.
     */
    public synchronized void addObserver(ConversationSetObserver observer) {
        mObservers.add(observer);
    }

    /**
     * Clear the selected set entirely.
     */
    public synchronized void clear() {
        boolean initiallyNotEmpty = !mInternalMap.isEmpty();
        mInternalMap.clear();

        if (mInternalMap.isEmpty() && initiallyNotEmpty) {
            ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
            dispatchOnChange(observersCopy);
            dispatchOnEmpty(observersCopy);
        }
    }

    /**
     * Returns true if the given key exists in the conversation selection set. This assumes
     * the internal representation holds conversation.id values.
     * @param key the id of the conversation
     * @return true if the key exists in this selected set.
     */
    public synchronized boolean containsKey(Long key) {
        return mInternalMap.containsKey(key);
    }

    /**
     * Returns true if the given conversation is stored in the selection set.
     * @param conversation
     * @return true if the conversation exists in the selected set.
     */
    public synchronized boolean contains(Conversation conversation) {
        return mInternalMap.containsKey(conversation.id);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private synchronized void dispatchOnBecomeUnempty(
            ArrayList<ConversationSetObserver> observers) {
        for (ConversationSetObserver observer : observers) {
            observer.onSetPopulated(this);
        }
    }

    private synchronized void dispatchOnChange(ArrayList<ConversationSetObserver> observers) {
        // Copy observers so that they may unregister themselves as listeners on
        // event handling.
        for (ConversationSetObserver observer : observers) {
            observer.onSetChanged(this);
        }
    }

    private synchronized void dispatchOnEmpty(ArrayList<ConversationSetObserver> observers) {
        for (ConversationSetObserver observer : observers) {
            observer.onSetEmpty();
        }
    }

    /**
     * Is this conversation set empty?
     * @return true if the conversation selection set is empty. False otherwise.
     */
    public synchronized boolean isEmpty() {
        return mInternalMap.isEmpty();
    }

    /** @see java.util.HashMap#put */
    private synchronized void put(Long id, Conversation info) {
        boolean initiallyEmpty = mInternalMap.isEmpty();
        mInternalMap.put(id, info);

        ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
        dispatchOnChange(observersCopy);
        if (initiallyEmpty) {
            dispatchOnBecomeUnempty(observersCopy);
        }
    }

    /** @see java.util.HashMap#remove */
    private synchronized void remove(Long id) {
        boolean initiallyNotEmpty = !mInternalMap.isEmpty();
        mInternalMap.remove(id);

        ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
        dispatchOnChange(observersCopy);
        if (mInternalMap.isEmpty() && initiallyNotEmpty) {
            dispatchOnEmpty(observersCopy);
        }
    }

    /**
     * Unregisters an observer for change events.
     *
     * @param observer the observer to unregister.
     */
    public synchronized void removeObserver(ConversationSetObserver observer) {
        mObservers.remove(observer);
    }

    /**
     * Returns the number of conversations that are currently selected
     * @return the number of selected conversations.
     */
    public synchronized int size() {
        return mInternalMap.size();
    }

    /**
     * Toggles the existence of the given conversation in the selection set. If the conversation is
     * currently selected, it is deselected. If it doesn't exist in the selection set, then it is
     * selected.
     * @param conversation
     */
    public void toggle(Conversation conversation) {
        long conversationId = conversation.id;
        if (containsKey(conversationId)) {
            remove(conversationId);
        } else {
            put(conversationId, conversation);
        }
    }

    /** @see java.util.HashMap#values */
    public synchronized Collection<Conversation> values() {
        return mInternalMap.values();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Conversation[] values = values().toArray(new Conversation[size()]);
        dest.writeParcelableArray(values, flags);
    }
}
