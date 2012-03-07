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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Adapter;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.browse.MessageHeaderView;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.ListParams;
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

    private final WebViewClient mWebViewClient = new ConversationWebViewClient();

    private MessageListAdapter mAdapter;

    private boolean mViewsCreated;

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

        mAdapter = new MessageListAdapter(mActivity.getActivityContext(),
                null /* cursor */, mAccount, getLoaderManager());
        mConversationContainer.setOverlayAdapter(mAdapter);

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
        mWebView.setWebViewClient(mWebViewClient);
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

        mViewsCreated = true;

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mViewsCreated = false;
        mActivity.attachConversationView(null);
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

        if (mAdapter.getCursor() == null) {
            renderConversation(messageCursor);
        } else {
            updateConversation(messageCursor);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    private void renderConversation(MessageCursor messageCursor) {
        mWebView.loadDataWithBaseURL(mBaseUri, renderMessageBodies(messageCursor), "text/html",
                "utf-8", null);
        mAdapter.swapCursor(messageCursor);
    }

    private void updateConversation(MessageCursor messageCursor) {
        // TODO: handle server-side conversation updates
        // for simple things like header data changes, just re-render the affected headers
        // if a new message is present, save off the pending cursor and show a notification to
        // re-render

        final MessageCursor oldCursor = (MessageCursor) mAdapter.getCursor();
        mAdapter.swapCursor(messageCursor);
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
        private boolean mDeliveredFirstResults = false;

        public MessageLoader(Context c, Uri uri) {
            super(c, uri, UIProvider.MESSAGE_PROJECTION, null, null, null);
        }

        @Override
        public Cursor loadInBackground() {
            return new MessageCursor(super.loadInBackground());

        }

        @Override
        public void deliverResult(Cursor result) {
            // We want to deliver these results, and then we want to make sure that any subsequent
            // queries do not hit the network
            super.deliverResult(result);

            if (!mDeliveredFirstResults) {
                mDeliveredFirstResults = true;
                Uri uri = getUri();

                // Create a ListParams that tells the provider to not hit the network
                final ListParams listParams =
                        new ListParams(ListParams.NO_LIMIT, false /* useNetwork */);

                // Build the new uri with this additional parameter
                uri = uri.buildUpon().appendQueryParameter(
                        UIProvider.LIST_PARAMS_QUERY_PARAMETER, listParams.serialize()).build();
                setUri(uri);
            }
        }
    }

    private static class MessageCursor extends CursorWrapper {

        private Map<Long, Message> mCache = Maps.newHashMap();

        public MessageCursor(Cursor inner) {
            super(inner);
        }

        public Message get() {
            final long id = getWrappedCursor().getLong(UIProvider.MESSAGE_ID_COLUMN);
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

    private static int[] parseInts(final String[] stringArray) {
        final int len = stringArray.length;
        final int[] ints = new int[len];
        for (int i = 0; i < len; i++) {
            ints[i] = Integer.parseInt(stringArray[i]);
        }
        return ints;
    }

    private class ConversationWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            // TODO: save off individual message unread state (here, or in onLoadFinished?) so
            // 'mark unread' restores the original unread state for each individual message

            // mark as read upon open
            if (!mConversation.read) {
                mConversation.markRead(mContext, true /* read */);
            }
        }

    }

    /**
     * NOTE: all public methods must be listed in the proguard flags so that they can be accessed
     * via reflection and not stripped.
     *
     */
    private class MailJsBridge {

        @SuppressWarnings("unused")
        public void onWebContentGeometryChange(final String[] headerBottomStrs,
                final String[] headerHeightStrs) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mViewsCreated) {
                        LogUtils.d(LOG_TAG, "ignoring webContentGeometryChange because views" +
                                " are gone, %s", ConversationViewFragment.this);
                        return;
                    }

                    mConversationContainer.onGeometryChange(parseInts(headerBottomStrs),
                            parseInts(headerHeightStrs));
                }
            });
        }

    }

}
