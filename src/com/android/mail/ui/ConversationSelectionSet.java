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

import com.android.mail.ConversationInfo;
import com.android.mail.providers.Folder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple thread-safe wrapper over a set of conversations representing a
 * selection set (e.g. in a conversation list). This class dispatches changes
 * when the set goes empty, and when it becomes unempty. For simplicity, this
 * class <b>does not allow modifications</b> to the collection in observers when
 * responding to change events.
 */
public class ConversationSelectionSet implements Parcelable {
    private final HashMap<Long, ConversationInfo> mInternalMap =
            new HashMap<Long, ConversationInfo>();

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
     * Unregisters an observer for change events.
     *
     * @param observer the observer to unregister.
     */
    public synchronized void removeObserver(ConversationSetObserver observer) {
        mObservers.remove(observer);
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
    private synchronized ConversationInfo get(Long id) {
        return mInternalMap.get(id);
    }

    /** @see java.util.HashMap#put */
    private synchronized void put(Long id, ConversationInfo info) {
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

    /** @see java.util.HashMap#size */
    private synchronized int size() {
        return mInternalMap.size();
    }

    /** @see java.util.HashMap#values */
    private synchronized Collection<ConversationInfo> values() {
        return mInternalMap.values();
    }

    /** @see java.util.HashMap#containsKey */
    private synchronized boolean containsKey(Long key) {
        return mInternalMap.containsKey(key);
    }

    /** @see java.util.HashMap#clear */
    private synchronized void clear() {
        boolean initiallyNotEmpty = !mInternalMap.isEmpty();
        mInternalMap.clear();

        if (mInternalMap.isEmpty() && initiallyNotEmpty) {
            ArrayList<ConversationSetObserver> observersCopy = Lists.newArrayList(mObservers);
            dispatchOnChange(observersCopy);
            dispatchOnEmpty(observersCopy);
        }
    }

    /**
     * Determines the set of labels associated with the selected conversations.
     * If the set of labels differ between the conversations, it will return the
     * set by the first one in the iterator order.
     *
     * @return the set of labels associated with the selection set.
     */
    public synchronized Map<String, Folder> getFolders() {
        if (mInternalMap.isEmpty()) {
            return Collections.emptyMap();
        } else {
            return mInternalMap.values().iterator().next().getFolders();
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        ConversationInfo[] values = values().toArray(new ConversationInfo[size()]);
        dest.writeParcelableArray(values, flags);
    }

    public static final Parcelable.Creator<ConversationSelectionSet> CREATOR =
            new Parcelable.Creator<ConversationSelectionSet>() {

        @Override
        public ConversationSelectionSet createFromParcel(Parcel source) {
            ConversationSelectionSet result = new ConversationSelectionSet();
            Parcelable[] conversations = source.readParcelableArray(
                    ConversationInfo.class.getClassLoader());
            for (Parcelable parceled : conversations) {
                ConversationInfo conversation = (ConversationInfo) parceled;
                result.put(conversation.getConversationId(), conversation);
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
     public synchronized void toggle(long conversationId, long maxMessageId,
             Map<String, Folder> folders) {
         if (containsKey(conversationId)) {
             remove(conversationId);
         } else {
             put(conversationId, new ConversationInfo(conversationId, maxMessageId, folders));
         }
     }

     /**
      * Updates the label a given conversation in the set may have.
      */
     public synchronized void onLabelChanged(Folder folders, long conversationId, boolean added) {
         if (containsKey(conversationId)) {
             ConversationInfo info = get(conversationId);
             info.updateFolder(folders, added);
         }
     }
}
