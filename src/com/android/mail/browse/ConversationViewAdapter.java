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

import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.android.mail.ContactInfoSource;
import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.browse.ConversationViewHeader.ConversationViewHeaderCallbacks;
import com.android.mail.browse.MessageCursor.ConversationMessage;
import com.android.mail.browse.MessageHeaderView.MessageHeaderViewCallbacks;
import com.android.mail.browse.SuperCollapsedBlock.OnClickListener;
import com.android.mail.providers.Account;
import com.android.mail.providers.Address;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.ControllableActivity;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A specialized adapter that contains overlay views to draw on top of the underlying conversation
 * WebView. Each independently drawn overlay view gets its own item in this adapter, and indices
 * in this adapter do not necessarily line up with cursor indices. For example, an expanded
 * message may have a header and footer, and since they are not drawn coupled together, they each
 * get an adapter item.
 * <p>
 * Each item in this adapter is a {@link ConversationOverlayItem} to expose enough information
 * to {@link ConversationContainer} so that it can position overlays properly.
 *
 */
public class ConversationViewAdapter extends BaseAdapter {

    private Context mContext;
    private final FormattedDateBuilder mDateBuilder;
    private final ConversationAccountController mAccountController;
    private final LoaderManager mLoaderManager;
    private final FragmentManager mFragmentManager;
    private final MessageHeaderViewCallbacks mMessageCallbacks;
    private final ContactInfoSource mContactInfoSource;
    private ConversationViewHeaderCallbacks mConversationCallbacks;
    private OnClickListener mSuperCollapsedListener;
    private Map<String, Address> mAddressCache;
    private final LayoutInflater mInflater;

    private final List<ConversationOverlayItem> mItems;

    public static final int VIEW_TYPE_CONVERSATION_HEADER = 0;
    public static final int VIEW_TYPE_MESSAGE_HEADER = 1;
    public static final int VIEW_TYPE_MESSAGE_FOOTER = 2;
    public static final int VIEW_TYPE_SUPER_COLLAPSED_BLOCK = 3;
    public static final int VIEW_TYPE_COUNT = 4;

    public interface ConversationAccountController {
        Account getAccount();
    }

    public class ConversationHeaderItem extends ConversationOverlayItem {
        public final Conversation mConversation;

        private ConversationHeaderItem(Conversation conv) {
            mConversation = conv;
        }

        @Override
        public int getType() {
            return VIEW_TYPE_CONVERSATION_HEADER;
        }

        @Override
        public View createView(Context context, LayoutInflater inflater, ViewGroup parent) {
            final ConversationViewHeader headerView = (ConversationViewHeader) inflater.inflate(
                    R.layout.conversation_view_header, parent, false);
            headerView.setCallbacks(mConversationCallbacks, mAccountController);
            headerView.bind(this);
            headerView.setSubject(mConversation.subject);
            if (mAccountController.getAccount().supportsCapability(
                    UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV)) {
                headerView.setFolders(mConversation);
            }

            return headerView;
        }

        @Override
        public void bindView(View v, boolean measureOnly) {
            ConversationViewHeader header = (ConversationViewHeader) v;
            header.bind(this);
        }

        @Override
        public boolean isContiguous() {
            return true;
        }

    }

    public class MessageHeaderItem extends ConversationOverlayItem {
        private ConversationMessage mMessage;

        // view state variables
        private boolean mExpanded;
        public boolean detailsExpanded;

        // cached values to speed up re-rendering during view recycling
        public CharSequence timestampShort;
        public CharSequence timestampLong;
        public CharSequence recipientSummaryText;

        MessageHeaderItem(ConversationMessage message, boolean expanded) {
            mMessage = message;
            mExpanded = expanded;

            detailsExpanded = false;
        }

        public ConversationMessage getMessage() {
            return mMessage;
        }

        @Override
        public int getType() {
            return VIEW_TYPE_MESSAGE_HEADER;
        }

        @Override
        public View createView(Context context, LayoutInflater inflater, ViewGroup parent) {
            final MessageHeaderView v = (MessageHeaderView) inflater.inflate(
                    R.layout.conversation_message_header, parent, false);
            v.initialize(mDateBuilder, mAccountController, mAddressCache);
            v.setCallbacks(mMessageCallbacks);
            v.setContactInfoSource(mContactInfoSource);
            return v;
        }

        @Override
        public void bindView(View v, boolean measureOnly) {
            final MessageHeaderView header = (MessageHeaderView) v;
            header.bind(this, measureOnly);
        }

        @Override
        public boolean isContiguous() {
            return !isExpanded();
        }

        public boolean isExpanded() {
            return mExpanded;
        }

        public void setExpanded(boolean expanded) {
            if (mExpanded != expanded) {
                mExpanded = expanded;
            }
        }

        @Override
        public boolean canBecomeSnapHeader() {
            return isExpanded();
        }

        @Override
        public boolean canPushSnapHeader() {
            return true;
        }

        @Override
        public boolean belongsToMessage(ConversationMessage message) {
            return Objects.equal(mMessage, message);
        }

        @Override
        public void setMessage(ConversationMessage message) {
            mMessage = message;
        }

    }

    public class MessageFooterItem extends ConversationOverlayItem {
        /**
         * A footer can only exist if there is a matching header. Requiring a header allows a
         * footer to stay in sync with the expanded state of the header.
         */
        private final MessageHeaderItem headerItem;

        private MessageFooterItem(MessageHeaderItem item) {
            headerItem = item;
        }

        @Override
        public int getType() {
            return VIEW_TYPE_MESSAGE_FOOTER;
        }

        @Override
        public View createView(Context context, LayoutInflater inflater, ViewGroup parent) {
            final MessageFooterView v = (MessageFooterView) inflater.inflate(
                    R.layout.conversation_message_footer, parent, false);
            v.initialize(mLoaderManager, mFragmentManager);
            return v;
        }

        @Override
        public void bindView(View v, boolean measureOnly) {
            final MessageFooterView attachmentsView = (MessageFooterView) v;
            attachmentsView.bind(headerItem, measureOnly);
        }

        @Override
        public boolean isContiguous() {
            return true;
        }

        @Override
        public int getHeight() {
            // a footer may change height while its view does not exist because it is offscreen
            // (but the header is onscreen and thus collapsible)
            if (!headerItem.isExpanded()) {
                return 0;
            }
            return super.getHeight();
        }
    }

    public class SuperCollapsedBlockItem extends ConversationOverlayItem {

        private final int mStart;
        private int mEnd;

        private SuperCollapsedBlockItem(int start, int end) {
            mStart = start;
            mEnd = end;
        }

        @Override
        public int getType() {
            return VIEW_TYPE_SUPER_COLLAPSED_BLOCK;
        }

        @Override
        public View createView(Context context, LayoutInflater inflater, ViewGroup parent) {
            final SuperCollapsedBlock scb = (SuperCollapsedBlock) inflater.inflate(
                    R.layout.super_collapsed_block, parent, false);
            scb.initialize(mSuperCollapsedListener);
            return scb;
        }

        @Override
        public void bindView(View v, boolean measureOnly) {
            final SuperCollapsedBlock scb = (SuperCollapsedBlock) v;
            scb.bind(this);
        }

        @Override
        public boolean isContiguous() {
            return true;
        }

        public int getStart() {
            return mStart;
        }

        public int getEnd() {
            return mEnd;
        }

        @Override
        public boolean canPushSnapHeader() {
            return true;
        }
    }

    public ConversationViewAdapter(ControllableActivity controllableActivity,
            ConversationAccountController accountController,
            LoaderManager loaderManager,
            MessageHeaderViewCallbacks messageCallbacks,
            ContactInfoSource contactInfoSource,
            ConversationViewHeaderCallbacks convCallbacks,
            SuperCollapsedBlock.OnClickListener scbListener, Map<String, Address> addressCache,
            FormattedDateBuilder dateBuilder) {
        mContext = controllableActivity.getActivityContext();
        mDateBuilder = dateBuilder;
        mAccountController = accountController;
        mLoaderManager = loaderManager;
        mFragmentManager = controllableActivity.getFragmentManager();
        mMessageCallbacks = messageCallbacks;
        mContactInfoSource = contactInfoSource;
        mConversationCallbacks = convCallbacks;
        mSuperCollapsedListener = scbListener;
        mAddressCache = addressCache;
        mInflater = LayoutInflater.from(mContext);

        mItems = Lists.newArrayList();
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position).getType();
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public ConversationOverlayItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position; // TODO: ensure this works well enough
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getView(getItem(position), convertView, parent, false /* measureOnly */);
    }

    public View getView(ConversationOverlayItem item, View convertView, ViewGroup parent,
            boolean measureOnly) {
        final View v;

        if (convertView == null) {
            v = item.createView(mContext, mInflater, parent);
        } else {
            v = convertView;
        }
        item.bindView(v, measureOnly);

        return v;
    }

    public int addItem(ConversationOverlayItem item) {
        final int pos = mItems.size();
        mItems.add(item);
        notifyDataSetChanged();
        return pos;
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public int addConversationHeader(Conversation conv) {
        return addItem(new ConversationHeaderItem(conv));
    }

    public int addMessageHeader(ConversationMessage msg, boolean expanded) {
        return addItem(new MessageHeaderItem(msg, expanded));
    }

    public int addMessageFooter(MessageHeaderItem headerItem) {
        return addItem(new MessageFooterItem(headerItem));
    }

    public MessageHeaderItem newMessageHeaderItem(ConversationMessage message, boolean expanded) {
        return new MessageHeaderItem(message, expanded);
    }

    public MessageFooterItem newMessageFooterItem(MessageHeaderItem headerItem) {
        return new MessageFooterItem(headerItem);
    }

    public int addSuperCollapsedBlock(int start, int end) {
        return addItem(new SuperCollapsedBlockItem(start, end));
    }

    public void replaceSuperCollapsedBlock(SuperCollapsedBlockItem blockToRemove,
            Collection<ConversationOverlayItem> replacements) {
        final int pos = mItems.indexOf(blockToRemove);
        if (pos == -1) {
            return;
        }

        mItems.remove(pos);
        mItems.addAll(pos, replacements);
        notifyDataSetChanged();
    }

    public void updateItemsForMessage(ConversationMessage message) {
        for (ConversationOverlayItem item : mItems) {
            if (item.belongsToMessage(message)) {
                item.setMessage(message);
            }
        }
        notifyDataSetChanged();
    }

}
