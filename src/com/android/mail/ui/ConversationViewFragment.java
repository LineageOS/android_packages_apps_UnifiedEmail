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

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Browser;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.android.mail.R;
import com.android.mail.browse.ConversationContainer;
import com.android.mail.browse.ConversationViewAdapter;
import com.android.mail.browse.ConversationViewAdapter.ConversationItem;
import com.android.mail.browse.ConversationViewAdapter.MessageHeaderItem;
import com.android.mail.browse.ConversationViewHeader;
import com.android.mail.browse.ConversationWebView;
import com.android.mail.browse.MessageCursor;
import com.android.mail.browse.MessageFooterView;
import com.android.mail.browse.MessageHeaderView.MessageHeaderViewCallbacks;
import com.android.mail.providers.Account;
import com.android.mail.providers.Address;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.ListParams;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.Maps;

import java.util.Map;


/**
 * The conversation view UI component.
 */
public final class ConversationViewFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        ConversationViewHeader.ConversationViewHeaderCallbacks,
        MessageHeaderViewCallbacks {

    private static final String LOG_TAG = new LogUtils().getLogTag();

    private static final int MESSAGE_LOADER_ID = 0;

    private ControllableActivity mActivity;

    private Context mContext;

    private Conversation mConversation;

    private ConversationContainer mConversationContainer;

    private Account mAccount;

    private ConversationWebView mWebView;

    private HtmlConversationTemplates mTemplates;

    private String mBaseUri;

    private final Handler mHandler = new Handler();

    private final MailJsBridge mJsBridge = new MailJsBridge();

    private final WebViewClient mWebViewClient = new ConversationWebViewClient();

    private ConversationViewAdapter mAdapter;
    private MessageCursor mCursor;

    private boolean mViewsCreated;

    private MenuItem mChangeFoldersMenuItem;

    private float mDensity;

    private Folder mFolder;

    private final Map<String, Address> mAddressCache = Maps.newHashMap();

    private static final String ARG_ACCOUNT = "account";
    private static final String ARG_CONVERSATION = "conversation";
    private static final String ARG_FOLDER = "folder";

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
            Conversation conversation, Folder folder) {
       ConversationViewFragment f = new ConversationViewFragment();
       Bundle args = new Bundle();
       args.putParcelable(ARG_ACCOUNT, account);
       args.putParcelable(ARG_CONVERSATION, conversation);
       args.putParcelable(ARG_FOLDER, folder);
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
        if (!(activity instanceof ControllableActivity)) {
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

        mAdapter = new ConversationViewAdapter(mActivity.getActivityContext(), mAccount,
                getLoaderManager(), this, this, mAddressCache);
        mConversationContainer.setOverlayAdapter(mAdapter);

        mDensity = getResources().getDisplayMetrics().density;

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
        mFolder = args.getParcelable(ARG_FOLDER);
        mBaseUri = "x-thread://" + mAccount.name + "/" + mConversation.id;

        // not really, we just want to get a crack to store a reference to the change_folders item
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.conversation_view, null);
        mConversationContainer = (ConversationContainer) rootView
                .findViewById(R.id.conversation_container);
        mWebView = (ConversationWebView) mConversationContainer.findViewById(R.id.webview);

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

        final WebSettings settings = mWebView.getSettings();

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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        mChangeFoldersMenuItem = menu.findItem(R.id.change_folders);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        boolean showMarkImportant = !mConversation.isImportant();
        Utils.setMenuItemVisibility(
                menu,
                R.id.mark_important,
                showMarkImportant
                        && mAccount
                                .supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));
        Utils.setMenuItemVisibility(
                menu,
                R.id.mark_not_important,
                !showMarkImportant
                        && mAccount
                                .supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));
        // TODO(mindyp) show/ hide spam and mute based on conversation
        // properties to be added.
        Utils.setMenuItemVisibility(menu, R.id.y_button,
                mAccount.supportsCapability(AccountCapabilities.ARCHIVE) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.ARCHIVE));
        Utils.setMenuItemVisibility(menu, R.id.report_spam,
                mAccount.supportsCapability(AccountCapabilities.REPORT_SPAM) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.REPORT_SPAM)
                        && !mConversation.spam);
        Utils.setMenuItemVisibility(
                menu,
                R.id.mute,
                mAccount.supportsCapability(AccountCapabilities.MUTE) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.DESTRUCTIVE_MUTE)
                        && !mConversation.muted);
    }
    /**
     * Handles a request to show a new conversation list, either from a search query or for viewing
     * a folder. This will initiate a data load, and hence must be called on the UI thread.
     */
    private void showConversation() {
        getLoaderManager().initLoader(MESSAGE_LOADER_ID, Bundle.EMPTY, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new MessageLoader(mContext, mConversation.messageListUri);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        MessageCursor messageCursor = (MessageCursor) data;

        // TODO: handle Gmail loading states (like LOADING and ERROR)
        if (messageCursor.getCount() == 0) {
            if (mCursor != null) {
                // TODO: need to exit this view- conversation may have been deleted, or for
                // whatever reason is now invalid
            } else {
                // ignore zero-sized cursors during initial load
            }
            return;
        }

        renderConversation(messageCursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mCursor = null;
        // TODO: null out all Message.mMessageCursor references
    }

    private void renderConversation(MessageCursor messageCursor) {
        mWebView.loadDataWithBaseURL(mBaseUri, renderMessageBodies(messageCursor), "text/html",
                "utf-8", null);
        mCursor = messageCursor;
    }

    private void updateConversation(MessageCursor messageCursor) {
        // TODO: handle server-side conversation updates
        // for simple things like header data changes, just re-render the affected headers
        // if a new message is present, save off the pending cursor and show a notification to
        // re-render

        mCursor = messageCursor;
    }

    /**
     * Populate the adapter with overlay views (message headers, super-collapsed blocks, a
     * conversation header), and return an HTML document with spacer divs inserted for all overlays.
     *
     */
    private String renderMessageBodies(MessageCursor messageCursor) {
        int pos = -1;

        boolean allowNetworkImages = false;

        // TODO: re-use any existing adapter item state (expanded, details expanded, show pics)

        mAdapter.setDefaultReplyAll(mActivity.getSettings().replyBehavior ==
                UIProvider.DefaultReplyBehavior.REPLY_ALL);

        // Walk through the cursor and build up an overlay adapter as you go.
        // Each overlay has an entry in the adapter for easy scroll handling in the container.
        // Items are not necessarily 1:1 in cursor and adapter because of super-collapsed blocks.
        // When adding adapter items, also add their heights to help the container later determine
        // overlay dimensions.

        mAdapter.clear();

        // We don't need to kick off attachment loaders during this first measurement phase,
        // so disable them temporarily.
        MessageFooterView.enableAttachmentLoaders(false);

        // N.B. the units of height for spacers are actually dp and not px because WebView assumes
        // a pixel is an mdpi pixel, unless you set device-dpi.

        // add a single conversation header item
        final int convHeaderPos = mAdapter.addConversationHeader(mConversation);
        final int convHeaderDp = measureOverlayHeight(convHeaderPos);

        mTemplates.startConversation(convHeaderDp);

        while (messageCursor.moveToPosition(++pos)) {
            final Message msg = messageCursor.getMessage();
            // TODO: save/restore 'show pics' state
            final boolean safeForImages = msg.alwaysShowImages /* || savedStateSaysSafe */;
            allowNetworkImages |= safeForImages;

            final boolean expanded = !msg.read || msg.starred || messageCursor.isLast();

            final int headerPos = mAdapter.addMessageHeader(msg, expanded);
            final MessageHeaderItem headerItem = (MessageHeaderItem) mAdapter.getItem(headerPos);

            final int footerPos = mAdapter.addMessageFooter(headerItem);

            // Measure item header and footer heights to allocate spacers in HTML
            // But since the views themselves don't exist yet, render each item temporarily into
            // a host view for measurement.
            final int headerDp = measureOverlayHeight(headerPos);
            final int footerDp = measureOverlayHeight(footerPos);

            mTemplates.appendMessageHtml(msg, expanded, safeForImages, 1.0f, headerDp,
                    footerDp);
        }

        // Re-enable attachment loaders
        MessageFooterView.enableAttachmentLoaders(true);

        mWebView.getSettings().setBlockNetworkImage(!allowNetworkImages);

        return mTemplates.endConversation(mBaseUri, 320);
    }

    /**
     * Measure the height of an adapter view by rendering the data in the adapter into a temporary
     * host view, and asking the adapter item to immediately measure itself. This method will reuse
     * a previous adapter view from {@link ConversationContainer}'s scrap views if one was generated
     * earlier.
     * <p>
     * After measuring the height, this method also saves the height in the {@link ConversationItem}
     * for later use in overlay positioning.
     *
     * @param position index into the adapter
     * @return height in dp of the rendered view
     */
    private int measureOverlayHeight(int position) {
        final ConversationItem convItem = mAdapter.getItem(position);
        final int type = convItem.getType();

        final View convertView = mConversationContainer.getScrapView(type);
        final View hostView = mAdapter.getView(position, convertView, mConversationContainer);
        if (convertView == null) {
            mConversationContainer.addScrapView(type, hostView);
        }

        final int heightPx = mConversationContainer.measureOverlay(hostView);
        convItem.setHeight(heightPx);
        convItem.markMeasurementValid();

        return (int) (heightPx / mDensity);
    }

    // BEGIN conversation header callbacks
    @Override
    public void onFoldersClicked() {
        if (mChangeFoldersMenuItem == null) {
            LogUtils.e(LOG_TAG, "unable to open 'change folders' dialog for a conversation");
            return;
        }
        mActivity.onOptionsItemSelected(mChangeFoldersMenuItem);
    }

    @Override
    public void onConversationViewHeaderHeightChange(int newHeight) {
        // TODO: propagate the new height to the header's HTML spacer. This can happen when labels
        // are added/removed
    }

    @Override
    public String getSubjectRemainder(String subject) {
        // TODO: hook this up to action bar
        return subject;
    }
    // END conversation header callbacks

    // START message header callbacks
    @Override
    public void setMessageSpacerHeight(MessageHeaderItem item, int newSpacerHeightPx) {
        mConversationContainer.invalidateSpacerGeometry();

        // update message HTML spacer height
        LogUtils.i(LOG_TAG, "setting HTML spacer h=%dpx", newSpacerHeightPx);
        final int heightDp = (int) (newSpacerHeightPx / mDensity);
        mWebView.loadUrl(String.format("javascript:setMessageHeaderSpacerHeight('%s', %d);",
                mTemplates.getMessageDomId(item.message), heightDp));
    }

    @Override
    public void setMessageExpanded(MessageHeaderItem item, int newSpacerHeightPx) {
        mConversationContainer.invalidateSpacerGeometry();

        // show/hide the HTML message body and update the spacer height
        LogUtils.i(LOG_TAG, "setting HTML spacer expanded=%s h=%dpx", item.isExpanded(),
                newSpacerHeightPx);
        final int heightDp = (int) (newSpacerHeightPx / mDensity);
        mWebView.loadUrl(String.format("javascript:setMessageBodyVisible('%s', %s, %d);",
                mTemplates.getMessageDomId(item.message), item.isExpanded(), heightDp));
    }

    @Override
    public void showExternalResources(Message msg) {
        mWebView.getSettings().setBlockNetworkImage(false);
        mWebView.loadUrl("javascript:unblockImages('" + mTemplates.getMessageDomId(msg) + "');");
    }
    // END message header callbacks

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
                mConversation.read = true;
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            boolean result = false;
            final Uri uri = Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, getActivity().getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

            // FIXME: give provider a chance to customize url intents?
            // Utils.addGoogleUriAccountIntentExtras(mContext, uri, mAccount, intent);

            try {
                mActivity.getActivityContext().startActivity(intent);
                result = true;
            } catch (ActivityNotFoundException ex) {
                // If no application can handle the URL, assume that the
                // caller can handle it.
            }

            return result;
        }

    }

    /**
     * NOTE: all public methods must be listed in the proguard flags so that they can be accessed
     * via reflection and not stripped.
     *
     */
    private class MailJsBridge {

        @SuppressWarnings("unused")
        public void onWebContentGeometryChange(final String[] overlayBottomStrs) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!mViewsCreated) {
                        LogUtils.d(LOG_TAG, "ignoring webContentGeometryChange because views" +
                                " are gone, %s", ConversationViewFragment.this);
                        return;
                    }

                    mConversationContainer.onGeometryChange(parseInts(overlayBottomStrs));
                }
            });
        }

    }

}
