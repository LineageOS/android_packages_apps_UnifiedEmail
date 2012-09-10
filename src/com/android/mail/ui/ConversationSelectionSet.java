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
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.mail.browse.ConversationItemView;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.providers.Conversation;
import com.android.mail.utils.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

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

    private final HashMap<Long, ConversationItemView> mInternalViewMap =
            new HashMap<Long, ConversationItemView>();
    private final BiMap<String, Long> mConversationUriToIdMap = HashBiMap.create();

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
        mInternalViewMap.clear();
        mInternalMap.clear();
        mConversationUriToIdMap.clear();

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
        return containsKey(conversation.id);
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

    private synchronized void put(Long id, Conversation info) {
        final boolean initiallyEmpty = mInternalMap.isEmpty();
        mInternalMap.put(id, info);
        // Fill out the view map with null. The sizes will match, but
        // we won't have any views available yet to store.
        mInternalViewMap.put(id, null);
        mConversationUriToIdMap.put(info.uri.toString(), id);

        final ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
        dispatchOnChange(observersCopy);
        if (initiallyEmpty) {
            dispatchOnBecomeUnempty(observersCopy);
        }
    }

    /** @see java.util.HashMap#put */
    private synchronized void put(Long id, ConversationItemView info) {
        boolean initiallyEmpty = mInternalMap.isEmpty();
        mInternalViewMap.put(id, info);
        mInternalMap.put(id, info.mHeader.conversation);
        mConversationUriToIdMap.put(info.mHeader.conversation.uri.toString(), id);

        final ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
        dispatchOnChange(observersCopy);
        if (initiallyEmpty) {
            dispatchOnBecomeUnempty(observersCopy);
        }
    }

    /** @see java.util.HashMap#remove */
    private synchronized void remove(Long id) {
        removeAll(Collections.singleton(id));
    }

    private synchronized void removeAll(Collection<Long> ids) {
        final boolean initiallyNotEmpty = !mInternalMap.isEmpty();

        final BiMap<Long, String> inverseMap = mConversationUriToIdMap.inverse();

        for (Long id : ids) {
            mInternalViewMap.remove(id);
            mInternalMap.remove(id);
            inverseMap.remove(id);
        }

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
     * selected. If you are certain that you are deselecting a conversation (you have verified
     * that {@link #contains(Conversation)} or {@link #containsKey(Long)} are true), then you
     * may pass a null {@link ConversationItemView}.
     * @param conversation
     */
    public void toggle(ConversationItemView view, Conversation conversation) {
        long conversationId = conversation.id;
        if (containsKey(conversationId)) {
            // We must not do anything with view here.
            remove(conversationId);
        } else {
            put(conversationId, view);
        }
    }

    /** @see java.util.HashMap#values */
    public synchronized Collection<Conversation> values() {
        return mInternalMap.values();
    }

    /** @see java.util.HashMap#keySet() */
    public synchronized Set<Long> keySet() {
        return mInternalMap.keySet();
    }

    /**
     * Puts all conversations given in the input argument into the selection set. If there are
     * any listeners they are notified once after adding <em>all</em> conversations to the selection
     * set.
     * @see java.util.HashMap#putAll(java.util.Map)
     */
    public void putAll(ConversationSelectionSet other) {
        if (other == null) {
            return;
        }

        final boolean initiallyEmpty = mInternalMap.isEmpty();
        mInternalMap.putAll(other.mInternalMap);

        ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
        dispatchOnChange(observersCopy);
        if (initiallyEmpty) {
            dispatchOnBecomeUnempty(observersCopy);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Conversation[] values = values().toArray(new Conversation[size()]);
        dest.writeParcelableArray(values, flags);
    }

    public Collection<ConversationItemView> views() {
        return mInternalViewMap.values();
    }

    /**
     * @param deletedRows an arraylist of conversation IDs which have been deleted.
     */
    public void delete(ArrayList<Integer> deletedRows) {
        for (long id : deletedRows) {
            remove(id);
        }
    }

    /**
     * Iterates through a cursor of conversations and ensures that the current set is present
     * within the result set denoted by the cursor. Any conversations not foun in the result set
     * is removed from the collection.
     */
    public synchronized void validateAgainstCursor(ConversationCursor cursor) {
        if (isEmpty()) {
            return;
        }

        if (cursor == null) {
            clear();
            return;
        }

        // We don't want iterating over this cusor to trigger a network request
        final boolean networkWasEnabled = Utils.disableConversationCursorNetworkAccess(cursor);

        // Get the current position of the cursor, so it can be reset
        final int currentPosition = cursor.getPosition();
        if (currentPosition != -1) {
            // Validate batch selection across all conversations, not just the most
            // recently loaded set.  See bug 2405138 (Batch selection cleared when
            // additional conversations are loaded on demand).
            cursor.moveToPosition(-1);
        }

        // The query has run, but we have been in the list
        // Make sure that the list of selected conversations
        // contains only items that are in the result set
        final Set<Long> batchConversations = new HashSet<Long>(keySet());

        // First ask the ConversationCursor for the list of conversations that have been deleted
        final Set<String> deletedConversations = cursor.getDeletedItems();
        // For each of the uris in the deleted set, add the conversation id to the
        // itemsToRemoveFromBatch set.
        final Set<Long> itemsToRemoveFromBatch = Sets.newHashSet();
        for (String conversationUri : deletedConversations) {
            final Long conversationId = mConversationUriToIdMap.get(conversationUri);
            if (conversationId != null) {
                itemsToRemoveFromBatch.add(conversationId);
            }
        }

        // Now remove the deleted conversations from the conversation cursor from the set of batch
        // conversations.  This can prevent the need to iterate over the cursor when the
        // ConversationCursor indicates that all of the items in the batch selection have been
        // deleted.
        batchConversations.removeAll(itemsToRemoveFromBatch);

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
        while (!batchConversations.isEmpty() && cursor.moveToNext()) {
            final long conversationId = Utils.getConversationId(cursor);
            if (batchConversations.remove(conversationId)) {
                itemsToRemoveFromBatch.add(conversationId);
            }
        }

        removeAll(itemsToRemoveFromBatch);

        cursor.moveToPosition(currentPosition);

        if (networkWasEnabled) {
            Utils.enableConversationCursorNetworkAccess(cursor);
        }
    }
}
