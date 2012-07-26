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

package com.android.mail.ui;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.text.Html;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.google.common.collect.ImmutableList;

public class LeaveBehindItem extends AnimatingItemView implements OnClickListener,
    SwipeableItemView {

    private ToastBarOperation mUndoOp;
    private Account mAccount;
    private AnimatedAdapter mAdapter;
    private ConversationCursor mConversationCursor;

    public LeaveBehindItem(Context context) {
        this(context, null);
    }

    public LeaveBehindItem(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public LeaveBehindItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.undo_button:
                if (mAccount.undoUri != null) {
                    // NOTE: We might want undo to return the messages affected,
                    // in which case
                    // the resulting cursor might be interesting...
                    // TODO: Use UIProvider.SEQUENCE_QUERY_PARAMETER to indicate
                    // the set of commands to undo
                    mAdapter.clearLeaveBehind(getConversationId());
                    mAdapter.setUndo(true);
                    mConversationCursor.undo(getContext(), mAccount.undoUri);
                }
                break;
        }
    }

    public void bindOperations(int position, Account account, AnimatedAdapter adapter,
            ToastBarOperation undoOp, Conversation target) {
        mUndoOp = undoOp;
        mAccount = account;
        mAdapter = adapter;
        mConversationCursor = (ConversationCursor) adapter.getCursor();
        setData(target);
        ((TextView) findViewById(R.id.undo_descriptionview)).setText(Html.fromHtml(mUndoOp
                .getSingularDescription(getContext())));
        ((RelativeLayout) findViewById(R.id.undo_button)).setOnClickListener(this);
    }

    public void commit() {
        Conversation conv = getData();
        mConversationCursor.delete(getContext(), ImmutableList.of(conv));
        mAdapter.clearLeaveBehind(conv.id);
        mAdapter.notifyDataSetChanged();
    }

    public long getConversationId() {
        return getData().id;
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void cancelTap() {
        // Do nothing.
    }

    public LeaveBehindData getLeaveBehindData() {
        return new LeaveBehindData(getData(), mUndoOp);
    }

    public class LeaveBehindData implements Parcelable {
        ToastBarOperation op;
        Conversation data;

        public LeaveBehindData(Conversation conv, ToastBarOperation undoOp) {;
            op = undoOp;
            data = conv;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel arg, int flags) {
            arg.writeParcelable(op, 0);
            arg.writeParcelable(data, 0);
        }

        private LeaveBehindData(Parcel arg) {
            this((Conversation) arg.readParcelable(null),
                    (ToastBarOperation) arg.readParcelable(null));
        }

        public final Creator<LeaveBehindData> CREATOR = new Creator<LeaveBehindData>() {

            @Override
            public LeaveBehindData createFromParcel(Parcel source) {
                return new LeaveBehindData(source);
            }

            @Override
            public LeaveBehindData[] newArray(int size) {
                return new LeaveBehindData[size];
            }

        };
    }
}
