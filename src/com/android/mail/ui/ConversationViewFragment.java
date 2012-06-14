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
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.android.mail.R;
import com.android.mail.browse.ConversationContainer;
import com.android.mail.browse.ConversationOverlayItem;
import com.android.mail.browse.ConversationViewAdapter;
import com.android.mail.browse.ConversationViewAdapter.MessageFooterItem;
import com.android.mail.browse.ConversationViewAdapter.MessageHeaderItem;
import com.android.mail.browse.ConversationViewAdapter.SuperCollapsedBlockItem;
import com.android.mail.browse.ConversationViewHeader;
import com.android.mail.browse.ConversationWebView;
import com.android.mail.browse.MessageCursor;
import com.android.mail.browse.MessageFooterView;
import com.android.mail.browse.MessageHeaderView.MessageHeaderViewCallbacks;
import com.android.mail.browse.SuperCollapsedBlock;
import com.android.mail.providers.Account;
import com.android.mail.providers.Address;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.ListParams;
import com.android.mail.providers.Message;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;


/**
 * The conversation view UI component.
 */
public final class ConversationViewFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        ConversationViewHeader.ConversationViewHeaderCallbacks,
        MessageHeaderViewCallbacks,
        SuperCollapsedBlock.OnClickListener {

    private static final String LOG_TAG = new LogUtils().getLogTag();
    public static final String LAYOUT_TAG = "ConvLayout";

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

    /**
     * Folder is used to help determine valid menu actions for this conversation.
     */
    private Folder mFolder;

    private final Map<String, Address> mAddressCache = Maps.newHashMap();

    /**
     * Temporary string containing the message bodies of the messages within a super-collapsed
     * block, for one-time use during block expansion. We cannot easily pass the body HTML
     * into JS without problematic escaping, so hold onto it momentarily and signal JS to fetch it
     * using {@link MailJsBridge}.
     */
    private String mTempBodiesHtml;

    private boolean mUserVisible;

    private int  mMaxAutoLoadMessages;

    private boolean mDeferredConversationLoad;

    private static final String ARG_ACCOUNT = "account";
    public static final String ARG_CONVERSATION = "conversation";
    private static final String ARG_FOLDER = "folder";

    private static final boolean DEBUG_DUMP_CONVERSATION_HTML = false;

    /**
     * Constructor needs to be public to handle orientation changes and activity lifecycle events.
     */
    public ConversationViewFragment() {
        super();
    }

    /**
     * Creates a new instance of {@link ConversationViewFragment}, initialized
     * to display a conversation with other parameters inherited/copied from an existing bundle,
     * typically one created using {@link #makeBasicArgs}.
     */
    public static ConversationViewFragment newInstance(Bundle existingArgs,
            Conversation conversation) {
        ConversationViewFragment f = new ConversationViewFragment();
        Bundle args = new Bundle(existingArgs);
        args.putParcelable(ARG_CONVERSATION, conversation);
        f.setArguments(args);
        return f;
    }

    public static Bundle makeBasicArgs(Account account, Folder folder) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_ACCOUNT, account);
        args.putParcelable(ARG_FOLDER, folder);
        return args;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        LogUtils.d(LOG_TAG, "IN CVF.onActivityCreated, this=%s subj=%s", this,
                mConversation.subject);
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
        mTemplates = new HtmlConversationTemplates(mContext);

        mAdapter = new ConversationViewAdapter(mActivity.getActivityContext(), mAccount,
                getLoaderManager(), this, this, this, mAddressCache);
        mConversationContainer.setOverlayAdapter(mAdapter);

        mDensity = getResources().getDisplayMetrics().density;

        mMaxAutoLoadMessages = getResources().getInteger(R.integer.max_auto_load_messages);

        showConversation();
    }

    @Override
    public void onCreate(Bundle savedState) {
        LogUtils.d(LOG_TAG, "onCreate in ConversationViewFragment (this=%s)", this);
        super.onCreate(savedState);

        Bundle args = getArguments();
        mAccount = args.getParcelable(ARG_ACCOUNT);
        mConversation = args.getParcelable(ARG_CONVERSATION);
        mFolder = args.getParcelable(ARG_FOLDER);
        mBaseUri = "x-thread://" + mAccount.name + "/" + mConversation.id;

        // Not really, we just want to get a crack to store a reference to the change_folder item
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.conversation_view, container, false);
        mConversationContainer = (ConversationContainer) rootView
                .findViewById(R.id.conversation_container);
        mWebView = (ConversationWebView) mConversationContainer.findViewById(R.id.webview);

        mWebView.addJavascriptInterface(mJsBridge, "mail");
        mWebView.setWebViewClient(mWebViewClient);
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                LogUtils.d(LOG_TAG, "JS: %s (%s:%d)", consoleMessage.message(),
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

        final float fontScale = getResources().getConfiguration().fontScale;
        final int textZoomPercent = getResources().getInteger(
                R.integer.conversation_text_zoom_percent);

        // scale down the default size a bit on small-screen devices
        // the goal is an effective default font size of 14dp
        int textZoom = settings.getTextZoom() * textZoomPercent / 100;
        // and then apply any system font scaling
        textZoom = (int) (textZoom * fontScale);
        settings.setTextZoom(textZoom);

        mViewsCreated = true;

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mConversationContainer.setOverlayAdapter(null);
        mAdapter = null;
        mViewsCreated = false;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        mChangeFoldersMenuItem = menu.findItem(R.id.change_folder);
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
        Utils.setMenuItemVisibility(menu, R.id.archive,
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
     * {@link #setUserVisibleHint(boolean)} only works on API >= 15, so implement our own for
     * reliability on older platforms.
     */
    public void setExtraUserVisibleHint(boolean isVisibleToUser) {
        LogUtils.v(LOG_TAG, "in CVF.setHint, val=%s (%s)", isVisibleToUser, this);

        if (mUserVisible != isVisibleToUser) {
            mUserVisible = isVisibleToUser;

            if (isVisibleToUser && mViewsCreated) {

                if (mCursor == null && mDeferredConversationLoad) {
                    // load
                    LogUtils.v(LOG_TAG, "Fragment is now user-visible, showing conversation: %s",
                            mConversation.uri);
                    showConversation();
                    mDeferredConversationLoad = false;
                } else {
                    onConversationSeen();
                }

            }
        }
    }

    /**
     * Handles a request to show a new conversation list, either from a search query or for viewing
     * a folder. This will initiate a data load, and hence must be called on the UI thread.
     */
    private void showConversation() {
        if (!mUserVisible && mConversation.numMessages > mMaxAutoLoadMessages) {
            LogUtils.v(LOG_TAG, "Fragment not user-visible, not showing conversation: %s",
                    mConversation.uri);
            mDeferredConversationLoad = true;
            return;
        }
        LogUtils.v(LOG_TAG,
                "Fragment is short or user-visible, immediately rendering conversation: %s",
                mConversation.uri);
        getLoaderManager().initLoader(MESSAGE_LOADER_ID, Bundle.EMPTY, this);
    }

    public Conversation getConversation() {
        return mConversation;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new MessageLoader(mContext, mConversation.messageListUri,
                mActivity.getListHandler());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        MessageCursor messageCursor = (MessageCursor) data;

        // ignore truly duplicate results
        // this can happen when restoring after rotation
        if (mCursor == messageCursor) {
            return;
        }

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

        // TODO: if this is not user-visible, delay render until user-visible fragment is done.
        // This is needed in addition to the showConversation() delay to speed up rotation and
        // restoration.

        renderConversation(messageCursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mCursor = null;
        // TODO: null out all Message.mMessageCursor references
    }

    private void renderConversation(MessageCursor messageCursor) {
        final String convHtml = renderMessageBodies(messageCursor);

        if (DEBUG_DUMP_CONVERSATION_HTML) {
            java.io.FileWriter fw = null;
            try {
                fw = new java.io.FileWriter("/sdcard/conv" + mConversation.id
                        + ".html");
                fw.write(convHtml);
            } catch (java.io.IOException e) {
                e.printStackTrace();
            } finally {
                if (fw != null) {
                    try {
                        fw.close();
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        mWebView.loadDataWithBaseURL(mBaseUri, convHtml, "text/html", "utf-8", null);
        mCursor = messageCursor;
    }

    /**
     * Populate the adapter with overlay views (message headers, super-collapsed blocks, a
     * conversation header), and return an HTML document with spacer divs inserted for all overlays.
     *
     */
    private String renderMessageBodies(MessageCursor messageCursor) {
        int pos = -1;

        LogUtils.d(LOG_TAG, "IN renderMessageBodies, fragment=%s subj=%s", this,
                mConversation.subject);
        boolean allowNetworkImages = false;

        // TODO: re-use any existing adapter item state (expanded, details expanded, show pics)
        final Settings settings = mActivity.getSettings();
        if (settings != null) {
            mAdapter.setDefaultReplyAll(settings.replyBehavior ==
                    UIProvider.DefaultReplyBehavior.REPLY_ALL);
        }
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

        int collapsedStart = -1;
        Message prevCollapsedMsg = null;
        boolean prevSafeForImages = false;

        while (messageCursor.moveToPosition(++pos)) {
            final Message msg = messageCursor.getMessage();

            // TODO: save/restore 'show pics' state
            final boolean safeForImages = msg.alwaysShowImages /* || savedStateSaysSafe */;
            allowNetworkImages |= safeForImages;

            final boolean expanded = !msg.read || msg.starred || messageCursor.isLast();

            if (!expanded) {
                // contribute to a super-collapsed block that will be emitted just before the next
                // expanded header
                if (collapsedStart < 0) {
                    collapsedStart = pos;
                }
                prevCollapsedMsg = msg;
                prevSafeForImages = safeForImages;
                continue;
            }

            // resolve any deferred decisions on previous collapsed items
            if (collapsedStart >= 0) {
                if (pos - collapsedStart == 1) {
                    // special-case for a single collapsed message: no need to super-collapse it
                    renderMessage(prevCollapsedMsg, false /* expanded */,
                            prevSafeForImages);
                } else {
                    renderSuperCollapsedBlock(collapsedStart, pos - 1);
                }
                prevCollapsedMsg = null;
                collapsedStart = -1;
            }

            renderMessage(msg, expanded, safeForImages);
        }

        // Re-enable attachment loaders
        MessageFooterView.enableAttachmentLoaders(true);

        mWebView.getSettings().setBlockNetworkImage(!allowNetworkImages);

        return mTemplates.endConversation(mBaseUri, 320);
    }

    private void renderSuperCollapsedBlock(int start, int end) {
        final int blockPos = mAdapter.addSuperCollapsedBlock(start, end);
        final int blockDp = measureOverlayHeight(blockPos);
        mTemplates.appendSuperCollapsedHtml(start, blockDp);
    }

    private void renderMessage(Message msg, boolean expanded, boolean safeForImages) {
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

    private String renderCollapsedHeaders(MessageCursor cursor,
            SuperCollapsedBlockItem blockToReplace) {
        final List<ConversationOverlayItem> replacements = Lists.newArrayList();

        mTemplates.reset();

        for (int i = blockToReplace.getStart(), end = blockToReplace.getEnd(); i <= end; i++) {
            cursor.moveToPosition(i);
            final Message msg = cursor.getMessage();
            final MessageHeaderItem header = mAdapter.newMessageHeaderItem(msg,
                    false /* expanded */);
            final MessageFooterItem footer = mAdapter.newMessageFooterItem(header);

            final int headerDp = measureOverlayHeight(header);
            final int footerDp = measureOverlayHeight(footer);

            mTemplates.appendMessageHtml(msg, false /* expanded */, msg.alwaysShowImages, 1.0f,
                    headerDp, footerDp);
            replacements.add(header);
            replacements.add(footer);
        }

        mAdapter.replaceSuperCollapsedBlock(blockToReplace, replacements);

        return mTemplates.emit();
    }

    private int measureOverlayHeight(int position) {
        return measureOverlayHeight(mAdapter.getItem(position));
    }

    /**
     * Measure the height of an adapter view by rendering and adapter item into a temporary
     * host view, and asking the view to immediately measure itself. This method will reuse
     * a previous adapter view from {@link ConversationContainer}'s scrap views if one was generated
     * earlier.
     * <p>
     * After measuring the height, this method also saves the height in the
     * {@link ConversationOverlayItem} for later use in overlay positioning.
     *
     * @param convItem adapter item with data to render and measure
     * @return height in dp of the rendered view
     */
    private int measureOverlayHeight(ConversationOverlayItem convItem) {
        final int type = convItem.getType();

        final View convertView = mConversationContainer.getScrapView(type);
        final View hostView = mAdapter.getView(convItem, convertView, mConversationContainer);
        if (convertView == null) {
            mConversationContainer.addScrapView(type, hostView);
        }

        final int heightPx = mConversationContainer.measureOverlay(hostView);
        convItem.setHeight(heightPx);
        convItem.markMeasurementValid();

        return (int) (heightPx / mDensity);
    }

    private void onConversationSeen() {
        // Ignore unsafe calls made after a fragment is detached from an activity
        final ControllableActivity activity = (ControllableActivity) getActivity();
        if (activity == null) {
            LogUtils.w(LOG_TAG, "ignoring onConversationSeen for conv=%s", mConversation.id);
            return;
        }

        // mark as read upon open
        if (!mConversation.read) {
            activity.getListHandler().sendConversationRead(
                    AbstractActivityController.TAG_CONVERSATION_LIST, mConversation, true,
                    false /*local*/);
            mConversation.read = true;
        }

        activity.onConversationSeen(mConversation);

        final SubjectDisplayChanger sdc = activity.getSubjectDisplayChanger();
        if (sdc != null) {
            sdc.setSubject(mConversation.subject);
        }
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
        final SubjectDisplayChanger sdc = mActivity.getSubjectDisplayChanger();
        if (sdc == null) {
            return subject;
        }
        return sdc.getUnshownSubject(subject);
    }
    // END conversation header callbacks

    // START message header callbacks
    @Override
    public void setMessageSpacerHeight(MessageHeaderItem item, int newSpacerHeightPx) {
        mConversationContainer.invalidateSpacerGeometry();

        // update message HTML spacer height
        LogUtils.i(LAYOUT_TAG, "setting HTML spacer h=%dpx", newSpacerHeightPx);
        final int heightDp = (int) (newSpacerHeightPx / mDensity);
        mWebView.loadUrl(String.format("javascript:setMessageHeaderSpacerHeight('%s', %d);",
                mTemplates.getMessageDomId(item.message), heightDp));
    }

    @Override
    public void setMessageExpanded(MessageHeaderItem item, int newSpacerHeightPx) {
        mConversationContainer.invalidateSpacerGeometry();

        // show/hide the HTML message body and update the spacer height
        LogUtils.i(LAYOUT_TAG, "setting HTML spacer expanded=%s h=%dpx", item.isExpanded(),
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

    @Override
    public void onSuperCollapsedClick(SuperCollapsedBlockItem item) {
        if (mCursor == null || !mViewsCreated) {
            return;
        }

        mTempBodiesHtml = renderCollapsedHeaders(mCursor, item);
        mWebView.loadUrl("javascript:replaceSuperCollapsedBlock(" + item.getStart() + ")");
    }

    private static class MessageLoader extends CursorLoader {
        private boolean mDeliveredFirstResults = false;
        private final ConversationListCallbacks mListController;

        public MessageLoader(Context c, Uri uri, ConversationListCallbacks listController) {
            super(c, uri, UIProvider.MESSAGE_PROJECTION, null, null, null);
            mListController = listController;
        }

        @Override
        public Cursor loadInBackground() {
            return new MessageCursor(super.loadInBackground(), mListController);
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
            LogUtils.i(LOG_TAG, "IN CVF.onPageFinished, url=%s fragment=%s", url,
                    ConversationViewFragment.this);

            super.onPageFinished(view, url);

            // TODO: save off individual message unread state (here, or in onLoadFinished?) so
            // 'mark unread' restores the original unread state for each individual message

            if (mUserVisible) {
                onConversationSeen();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            final Activity activity = getActivity();
            if (!mViewsCreated || activity == null) {
                return false;
            }

            boolean result = false;
            final Uri uri = Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, activity.getPackageName());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

            // FIXME: give provider a chance to customize url intents?
            // Utils.addGoogleUriAccountIntentExtras(mContext, uri, mAccount, intent);

            try {
                activity.startActivity(intent);
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
            try {
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
            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.onWebContentGeometryChange");
            }
        }

        @SuppressWarnings("unused")
        public String getTempMessageBodies() {
            try {
                if (!mViewsCreated) {
                    return "";
                }

                final String s = mTempBodiesHtml;
                mTempBodiesHtml = null;
                return s;
            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.getTempMessageBodies");
                return "";
            }
        }

    }

}
