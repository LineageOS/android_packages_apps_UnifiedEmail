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

import com.google.common.collect.Maps;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.widget.Adapter;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.browse.MessageHeaderView;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;

import java.util.Map;

/**
 * The conversation view UI component.
 */
public final class ConversationViewFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String LOG_TAG = new LogUtils().getLogTag();

    private static final int MESSAGE_LOADER_ID = 0;

    private ControllableActivity mActivity;

    private Context mContext;

    private Conversation mConversation;

    private TextView mSubject;

    private ConversationContainer mConversationContainer;

    private Account mAccount;

    private ConversationWebView mWebView;

    private HtmlConversationTemplates mTemplates;

    private String mBaseUri;

    private final Handler mHandler = new Handler();

    private final MailJsBridge mJsBridge = new MailJsBridge();

    private static final String ARG_ACCOUNT = "account";
    private static final String ARG_CONVERSATION = "conversation";

    /**
     * Constructor needs to be public to handle orientation changes and activity lifecycle events.
     */
    public ConversationViewFragment() {
        super();
    }

    /**
     * Creates a new instance of {@link ConversationViewFragment}, initialized
     * to display conversation.
     */
    public static ConversationViewFragment newInstance(Account account,
            Conversation conversation) {
       ConversationViewFragment f = new ConversationViewFragment();
       Bundle args = new Bundle();
       args.putParcelable(ARG_ACCOUNT, account);
       args.putParcelable(ARG_CONVERSATION, conversation);
       f.setArguments(args);
       return f;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Strictly speaking, we get back an android.app.Activity from getActivity. However, the
        // only activity creating a ConversationListContext is a MailActivity which is of type
        // ControllableActivity, so this cast should be safe. If this cast fails, some other
        // activity is creating ConversationListFragments. This activity must be of type
        // ControllableActivity.
        final Activity activity = getActivity();
        if (! (activity instanceof ControllableActivity)){
            LogUtils.wtf(LOG_TAG, "ConversationViewFragment expects only a ControllableActivity to"
                    + "create it. Cannot proceed.");
        }
        mActivity = (ControllableActivity) activity;
        mContext = mActivity.getApplicationContext();
        if (mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }
        mActivity.attachConversationView(this);
        mTemplates = new HtmlConversationTemplates(mContext);
        // Show conversation and start loading messages.
        showConversation();
    }

    @Override
    public void onCreate(Bundle savedState) {
        LogUtils.v(LOG_TAG, "onCreate in FolderListFragment(this=%s)", this);
        super.onCreate(savedState);

        Bundle args = getArguments();
        mAccount = args.getParcelable(ARG_ACCOUNT);
        mConversation = args.getParcelable(ARG_CONVERSATION);
        mBaseUri = "x-thread://" + mAccount.name + "/" + mConversation.id;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.conversation_view, null);
        mSubject = (TextView) rootView.findViewById(R.id.subject);
        mConversationContainer = (ConversationContainer) rootView
                .findViewById(R.id.conversation_container);
        mWebView = (ConversationWebView) rootView.findViewById(R.id.webview);

        mWebView.addJavascriptInterface(mJsBridge, "mail");

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                LogUtils.i(LOG_TAG, "JS: %s (%s:%d)", consoleMessage.message(),
                        consoleMessage.sourceId(), consoleMessage.lineNumber());
                return true;
            }
        });

        WebSettings settings = mWebView.getSettings();

        settings.setBlockNetworkImage(true);

        settings.setJavaScriptEnabled(true);
        settings.setUseWideViewPort(true);

        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        // Clear the adapter.
        mConversationContainer.setOverlayAdapter(null);
        mActivity.attachConversationView(null);

        super.onDestroyView();
    }

    /**
     * Handles a request to show a new conversation list, either from a search query or for viewing
     * a folder. This will initiate a data load, and hence must be called on the UI thread.
     */
    private void showConversation() {
        mSubject.setText(mConversation.subject);
        getLoaderManager().initLoader(MESSAGE_LOADER_ID, Bundle.EMPTY, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new MessageLoader(mContext, mConversation.messageListUri);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        MessageCursor messageCursor = (MessageCursor) data;
        mWebView.loadDataWithBaseURL(mBaseUri, renderMessageBodies(messageCursor), "text/html",
                "utf-8", null);
        final Adapter messageListAdapter = new MessageListAdapter(
                mActivity.getActivityContext(), messageCursor, mAccount, getLoaderManager());
        mConversationContainer.setOverlayAdapter(messageListAdapter);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Do nothing.
    }

    private String renderMessageBodies(MessageCursor messageCursor) {
        int pos = -1;
        mTemplates.startConversation(0);
        while (messageCursor.moveToPosition(++pos)) {
            mTemplates.appendMessageHtml(messageCursor.get(), true, false, 1.0f, 96);
        }
        return mTemplates.endConversation(mBaseUri, 320);
    }

    public void onTouchEvent(MotionEvent event) {
        // TODO: (mindyp) when there is an undo bar, check for event !in undo bar
        // if its not in undo bar, dismiss the undo bar.
    }

    private static class MessageLoader extends CursorLoader {

        public MessageLoader(Context c, Uri uri) {
            super(c, uri, UIProvider.MESSAGE_PROJECTION, null, null, null);
        }

        @Override
        public Cursor loadInBackground() {
            return new MessageCursor(super.loadInBackground());

        }
    }

    private static class MessageCursor extends CursorWrapper {

        private Map<Long, Message> mCache = Maps.newHashMap();

        public MessageCursor(Cursor inner) {
            super(inner);
        }

        public Message get() {
            long id = getWrappedCursor().getLong(0);
            Message m = mCache.get(id);
            if (m == null) {
                m = new Message(this);
                mCache.put(id, m);
            }
            return m;
        }
    }

    private static class MessageListAdapter extends ResourceCursorAdapter {

        private final FormattedDateBuilder mDateBuilder;
        private final Account mAccount;
        private final LoaderManager mLoaderManager;

        public MessageListAdapter(Context context, Cursor messageCursor, Account account,
                LoaderManager loaderManager) {
            super(context, R.layout.conversation_message_header, messageCursor, 0);
            mDateBuilder = new FormattedDateBuilder(context);
            mAccount = account;
            mLoaderManager = loaderManager;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            Message m = ((MessageCursor) cursor).get();
            MessageHeaderView header = (MessageHeaderView) view;
            header.initialize(mDateBuilder, mAccount, mLoaderManager, true, false, false);
            header.bind(m);
        }
    }

    /**
     * NOTE: all public methods must be listed in the proguard flags so that they can be accessed
     * via reflection and not stripped.
     *
     */
    private class MailJsBridge {

        @SuppressWarnings("unused")
        public void onWebContentGeometryChange(final String[] headerBottomStrs) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    final int len = headerBottomStrs.length;
                    final int[] headerBottoms = new int[len];
                    for (int i = 0; i < len; i++) {
                        headerBottoms[i] = Integer.parseInt(headerBottomStrs[i]);
                    }
                    mConversationContainer.onGeometryChange(headerBottoms);
                }
            });
        }

    }

}
