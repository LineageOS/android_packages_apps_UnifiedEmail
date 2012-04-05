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

import android.app.LoaderManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;

import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.browse.ConversationViewHeader.ConversationViewHeaderCallbacks;
import com.android.mail.browse.MessageHeaderView.MessageHeaderViewCallbacks;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * A specialized adapter that contains overlay views to draw on top of the underlying conversation
 * WebView. Each independently drawn overlay view gets its own item in this adapter, and indices
 * in this adapter do not necessarily line up with cursor indices. For example, an expanded
 * message may have a header and footer, and since they are not drawn coupled together, they each
 * get an adapter item.
 * <p>
 * Each item in this adapter is a {@link ConversationItem} to expose enough information to
 * {@link ConversationContainer} so that it can position overlays properly.
 *
 */
public class ConversationViewAdapter extends BaseAdapter {

    private Context mContext;
    private final FormattedDateBuilder mDateBuilder;
    private final Account mAccount;
    private final LoaderManager mLoaderManager;
    private final MessageHeaderViewCallbacks mMessageCallbacks;
    private ConversationViewHeaderCallbacks mConversationCallbacks;
    private final LayoutInflater mInflater;
    private boolean mDefaultReplyAll;

    private final List<ConversationItem> mItems;

    public static final int VIEW_TYPE_CONVERSATION_HEADER = 0;
    public static final int VIEW_TYPE_MESSAGE_HEADER = 1;
    public static final int VIEW_TYPE_MESSAGE_FOOTER = 2;
    public static final int VIEW_TYPE_COUNT = 3;

    public static final String LOG_TAG = new LogUtils().getLogTag();

    public static abstract class ConversationItem {
        private int mHeight;  // in px
        private boolean mNeedsMeasure;

        /**
         * @see Adapter#getItemViewType(int)
         */
        public abstract int getType();
        /**
         * Inflate and perform one-time initialization on a view for later binding.
         */
        public abstract View createView(Context context, LayoutInflater inflater,
                ViewGroup parent);
        /**
         * @see CursorAdapter#bindView(View, Context, android.database.Cursor)
         */
        public abstract void bindView(View v);
        /**
         * Returns true if this overlay view is meant to be positioned right on top of the overlay
         * below. This special positioning allows {@link ConversationContainer} to stack overlays
         * together even when zoomed into a conversation, when the overlay spacers spread farther
         * apart.
         */
        public abstract boolean isContiguous();

        /**
         * This method's behavior is critical and requires some 'splainin.
         * <p>
         * Subclasses that return a zero-size height to the {@link ConversationContainer} will
         * cause the scrolling/recycling logic there to remove any matching view from the container.
         * The item should switch to returning a non-zero height when its view should re-appear.
         * <p>
         * It's imperative that this method stay in sync with the current height of the HTML spacer
         * that matches this overlay.
         */
        public int getHeight() {
            return mHeight;
        }

        public void setHeight(int h) {
            LogUtils.i(LOG_TAG, "IN setHeight=%dpx of overlay item: %s", h, this);
            if (mHeight != h) {
                mHeight = h;
                mNeedsMeasure = true;
            }
        }

        public boolean isMeasurementValid() {
            return !mNeedsMeasure;
        }

        public void markMeasurementValid() {
            mNeedsMeasure = false;
        }

        public void invalidateMeasurement() {
            mNeedsMeasure = true;
        }
    }

    public class ConversationHeaderItem extends ConversationItem {
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
            headerView.setCallbacks(mConversationCallbacks);

            headerView.setSubject(mConversation.subject, false /* notify */);
            if (mAccount.supportsCapability(
                    UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV)) {
                headerView.setFolders(mConversation, false /* notify */);
            }

            return headerView;
        }

        @Override
        public void bindView(View v) {
            // There is only one conversation header, so the work is done once in createView.
        }

        @Override
        public boolean isContiguous() {
            return true;
        }

    }

    public class MessageHeaderItem extends ConversationItem {
        public final Message message;
        private boolean mExpanded;
        public boolean detailsExpanded;

        private MessageHeaderItem(Message message, boolean expanded) {
            this.message = message;
            mExpanded = expanded;

            detailsExpanded = false;
        }

        @Override
        public int getType() {
            return VIEW_TYPE_MESSAGE_HEADER;
        }

        @Override
        public View createView(Context context, LayoutInflater inflater, ViewGroup parent) {
            final MessageHeaderView v = (MessageHeaderView) inflater.inflate(
                    R.layout.conversation_message_header, parent, false);
            v.initialize(mDateBuilder, mAccount);
            v.setCallbacks(mMessageCallbacks);
            return v;
        }

        @Override
        public void bindView(View v) {
            final MessageHeaderView header = (MessageHeaderView) v;
            header.bind(this, mDefaultReplyAll);
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
    }

    public class MessageFooterItem extends ConversationItem {
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
            v.initialize(mLoaderManager);
            return v;
        }

        @Override
        public void bindView(View v) {
            final MessageFooterView attachmentsView = (MessageFooterView) v;
            attachmentsView.bind(headerItem);
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

    public ConversationViewAdapter(Context context, Account account, LoaderManager loaderManager,
            MessageHeaderViewCallbacks messageCallbacks,
            ConversationViewHeaderCallbacks convCallbacks) {
        mContext = context;
        mDateBuilder = new FormattedDateBuilder(context);
        mAccount = account;
        mLoaderManager = loaderManager;
        mMessageCallbacks = messageCallbacks;
        mConversationCallbacks = convCallbacks;
        mInflater = LayoutInflater.from(context);

        mItems = Lists.newArrayList();
    }

    public void setDefaultReplyAll(boolean defaultReplyAll) {
        mDefaultReplyAll = defaultReplyAll;
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
    public ConversationItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position; // TODO: ensure this works well enough
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View v;
        final ConversationItem item = getItem(position);

        if (convertView == null) {
            v = item.createView(mContext, mInflater, parent);
        } else {
            v = convertView;
        }
        item.bindView(v);

        return v;
    }

    public int addItem(ConversationItem item) {
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

    public int addMessageHeader(Message msg, boolean expanded) {
        return addItem(new MessageHeaderItem(msg, expanded));
    }

    public int addMessageFooter(MessageHeaderItem headerItem) {
        return addItem(new MessageFooterItem(headerItem));
    }

}
