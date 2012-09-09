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

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.Animator.AnimatorListener;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Browser;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.android.mail.ContactInfo;
import com.android.mail.ContactInfoSource;
import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.SenderInfoLoader;
import com.android.mail.browse.ConversationContainer;
import com.android.mail.browse.ConversationOverlayItem;
import com.android.mail.browse.ConversationViewAdapter;
import com.android.mail.browse.ConversationViewAdapter.ConversationAccountController;
import com.android.mail.browse.ConversationViewAdapter.MessageFooterItem;
import com.android.mail.browse.ConversationViewAdapter.MessageHeaderItem;
import com.android.mail.browse.ConversationViewAdapter.SuperCollapsedBlockItem;
import com.android.mail.browse.ConversationViewHeader;
import com.android.mail.browse.ConversationWebView;
import com.android.mail.browse.MessageCursor;
import com.android.mail.browse.MessageCursor.ConversationController;
import com.android.mail.browse.MessageCursor.ConversationMessage;
import com.android.mail.browse.MessageHeaderView;
import com.android.mail.browse.MessageHeaderView.MessageHeaderViewCallbacks;
import com.android.mail.browse.SuperCollapsedBlock;
import com.android.mail.browse.WebViewContextMenu;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.Address;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.ListParams;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.ViewProxyExtras;
import com.android.mail.ui.ConversationViewState.ExpansionState;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * The conversation view UI component.
 */
public final class ConversationViewFragment extends Fragment implements
        ConversationViewHeader.ConversationViewHeaderCallbacks,
        MessageHeaderViewCallbacks,
        SuperCollapsedBlock.OnClickListener,
        ConversationController,
        ConversationAccountController {

    private static final String LOG_TAG = LogTag.getLogTag();
    public static final String LAYOUT_TAG = "ConvLayout";

    private static final int MESSAGE_LOADER_ID = 0;
    private static final int CONTACT_LOADER_ID = 1;

    /** Do not auto load data when create this {@link ConversationView}. */
    public static final int NO_AUTO_LOAD = 0;
    /** Auto load data but do not show any animation. */
    public static final int AUTO_LOAD_BACKGROUND = 1;
    /** Auto load data and show animation. */
    public static final int AUTO_LOAD_VISIBLE = 2;

    private ControllableActivity mActivity;

    private Context mContext;

    private Conversation mConversation;

    private ConversationContainer mConversationContainer;

    private Account mAccount;

    private ConversationWebView mWebView;

    private View mNewMessageBar;

    private View mBackgroundView;

    private View mInfoView;

    private TextView mSendersView;

    private TextView mSubjectView;

    private View mProgressView;

    private HtmlConversationTemplates mTemplates;

    private String mBaseUri;

    private final Handler mHandler = new Handler();

    private final MailJsBridge mJsBridge = new MailJsBridge();

    private final WebViewClient mWebViewClient = new ConversationWebViewClient();

    private ConversationViewAdapter mAdapter;
    private MessageCursor mCursor;

    private boolean mViewsCreated;

    private MenuItem mChangeFoldersMenuItem;
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

    /**
     * Handles a deferred 'mark read' operation, necessary when the conversation view has finished
     * loading before the conversation cursor. Normally null unless this situation occurs.
     * When finally able to 'mark read', this observer will also be unregistered and cleaned up.
     */
    private MarkReadObserver mMarkReadObserver;

    /**
     * Parcelable state of the conversation view. Can safely be used without null checking any time
     * after {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     */
    private ConversationViewState mViewState;

    private final MessageLoaderCallbacks mMessageLoaderCallbacks = new MessageLoaderCallbacks();
    private final ContactLoaderCallbacks mContactLoaderCallbacks = new ContactLoaderCallbacks();

    private final AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount) {
            mAccount = newAccount;

            // settings may have been updated; refresh views that are known to depend on settings
            mConversationContainer.getSnapHeader().onAccountChanged();
            mAdapter.notifyDataSetChanged();
        }
    };
    private boolean mEnableContentReadySignal;

    private static final String ARG_ACCOUNT = "account";
    public static final String ARG_CONVERSATION = "conversation";
    private static final String ARG_FOLDER = "folder";
    private static final String BUNDLE_VIEW_STATE = "viewstate";
    private static int sSubjectColor = Integer.MIN_VALUE;
    private static int sSnippetColor = Integer.MIN_VALUE;

    private static final boolean DEBUG_DUMP_CONVERSATION_HTML = false;
    private static final boolean DISABLE_OFFSCREEN_LOADING = false;
    protected static final String AUTO_LOAD_KEY = "auto-load";

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
        mAccount = mAccountObserver.initialize(mActivity.getAccountController());

        final FormattedDateBuilder dateBuilder = new FormattedDateBuilder(mContext);

        mAdapter = new ConversationViewAdapter(mActivity.getActivityContext(), this,
                getLoaderManager(), this, mContactLoaderCallbacks, this, this, mAddressCache,
                dateBuilder);
        mConversationContainer.setOverlayAdapter(mAdapter);

        // set up snap header (the adapter usually does this with the other ones)
        final MessageHeaderView snapHeader = mConversationContainer.getSnapHeader();
        snapHeader.initialize(dateBuilder, this, mAddressCache);
        snapHeader.setCallbacks(this);
        snapHeader.setContactInfoSource(mContactLoaderCallbacks);

        mMaxAutoLoadMessages = getResources().getInteger(R.integer.max_auto_load_messages);

        mWebView.setOnCreateContextMenuListener(new WebViewContextMenu(activity));

        showConversation();

        if (mConversation.conversationBaseUri != null &&
                !TextUtils.isEmpty(mConversation.conversationCookie)) {
            // Set the cookie for this base url
            new SetCookieTask(mConversation.conversationBaseUri.toString(),
                    mConversation.conversationCookie).execute();
        }
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        final Bundle args = getArguments();
        mAccount = args.getParcelable(ARG_ACCOUNT);
        mConversation = args.getParcelable(ARG_CONVERSATION);
        mFolder = args.getParcelable(ARG_FOLDER);
        // Since the uri specified in the conversation base uri may not be unique, we specify a
        // base uri that us guaranteed to be unique for this conversation.
        mBaseUri = "x-thread://" + mAccount.name + "/" + mConversation.id;
        LogUtils.d(LOG_TAG, "onCreate in ConversationViewFragment (this=%s)", this);

        // Not really, we just want to get a crack to store a reference to the change_folder item
        setHasOptionsMenu(true);
    }

    private CharSequence createSubjectSnippet(CharSequence subject, CharSequence snippet) {
        SpannableStringBuilder subjectText = new SpannableStringBuilder(mContext.getString(
                R.string.subject_and_snippet, subject, snippet));
        ensureSubjectSnippetColors();
        int snippetStart = 0;
        int fontColor = sSubjectColor;
        if (subject != null) {
            subjectText.setSpan(new ForegroundColorSpan(fontColor), 0, subject.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            snippetStart = subject.length() + 1;
        }
        if (snippet != null) {
            fontColor = sSnippetColor;
            subjectText.setSpan(new ForegroundColorSpan(fontColor), snippetStart, subjectText
                    .length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return subjectText;
    }

    private void ensureSubjectSnippetColors() {
        if (sSubjectColor == Integer.MIN_VALUE) {
            Resources res = mContext.getResources();
            sSubjectColor = res.getColor(R.color.subject_text_color_read);
            sSnippetColor = res.getColor(R.color.snippet_text_color_read);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {

        if (savedInstanceState != null) {
            mViewState = savedInstanceState.getParcelable(BUNDLE_VIEW_STATE);
        } else {
            mViewState = new ConversationViewState();
        }

        View rootView = inflater.inflate(R.layout.conversation_view, container, false);
        mConversationContainer = (ConversationContainer) rootView
                .findViewById(R.id.conversation_container);

        mNewMessageBar = mConversationContainer.findViewById(R.id.new_message_notification_bar);
        mNewMessageBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onNewMessageBarClick();
            }
        });

        mBackgroundView = rootView.findViewById(R.id.background_view);
        mInfoView = rootView.findViewById(R.id.info_view);
        mSendersView = (TextView) rootView.findViewById(R.id.senders_view);
        mSubjectView = (TextView) rootView.findViewById(R.id.info_subject_view);
        mProgressView = rootView.findViewById(R.id.loading_progress);

        mWebView = (ConversationWebView) mConversationContainer.findViewById(R.id.webview);

        mWebView.addJavascriptInterface(mJsBridge, "mail");
        // On JB or newer, we use the 'webkitAnimationStart' DOM event to signal load complete
        // Below JB, try to speed up initial render by having the webview do supplemental draws to
        // custom a software canvas.
        mEnableContentReadySignal = Utils.isRunningJellybeanOrLater();
        mWebView.setWebViewClient(mWebViewClient);
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                LogUtils.i(LOG_TAG, "JS: %s (%s:%d)", consoleMessage.message(),
                        consoleMessage.sourceId(), consoleMessage.lineNumber());
                return true;
            }
        });
        mWebView.setContentSizeChangeListener(new ConversationWebView.ContentSizeChangeListener() {
            @Override
            public void onHeightChange(int h) {
                // When WebKit says the DOM height has changed, re-measure bodies and re-position
                // their headers.
                // This is separate from the typical JavaScript DOM change listeners because
                // cases like NARROW_COLUMNS text reflow do not trigger DOM events.
                mWebView.loadUrl("javascript:measurePositions();");
            }
        });

        final WebSettings settings = mWebView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        final float fontScale = getResources().getConfiguration().fontScale;
        final int desiredFontSizePx = getResources()
                .getInteger(R.integer.conversation_desired_font_size_px);
        final int unstyledFontSizePx = getResources()
                .getInteger(R.integer.conversation_unstyled_font_size_px);

        int textZoom = settings.getTextZoom();
        // apply a correction to the default body text style to get regular text to the size we want
        textZoom = textZoom * desiredFontSizePx / unstyledFontSizePx;
        // then apply any system font scaling
        textZoom = (int) (textZoom * fontScale);
        settings.setTextZoom(textZoom);

        mViewsCreated = true;

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Hacky workaround for http://b/6946182
        Utils.fixSubTreeLayoutIfOrphaned(getView(), "ConversationViewFragment");
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mViewState != null) {
            outState.putParcelable(BUNDLE_VIEW_STATE, mViewState);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mConversationContainer.setOverlayAdapter(null);
        mAdapter = null;
        if (mMarkReadObserver != null) {
            mActivity.getConversationUpdater().unregisterConversationListObserver(
                    mMarkReadObserver);
            mMarkReadObserver = null;
        }
        mViewsCreated = false;
        mAccountObserver.unregisterAndDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        mChangeFoldersMenuItem = menu.findItem(R.id.change_folder);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final boolean showMarkImportant = !mConversation.isImportant();
        Utils.setMenuItemVisibility(menu, R.id.mark_important, showMarkImportant
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));
        Utils.setMenuItemVisibility(menu, R.id.mark_not_important, !showMarkImportant
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));
        final boolean showDelete = mFolder != null &&
                mFolder.supportsCapability(UIProvider.FolderCapabilities.DELETE);
        Utils.setMenuItemVisibility(menu, R.id.delete, showDelete);
        // We only want to show the discard drafts menu item if we are not showing the delete menu
        // item, and the current folder is a draft folder and the account supports discarding
        // drafts for a conversation
        final boolean showDiscardDrafts = !showDelete && mFolder != null && mFolder.isDraft() &&
                mAccount.supportsCapability(AccountCapabilities.DISCARD_CONVERSATION_DRAFTS);
        Utils.setMenuItemVisibility(menu, R.id.discard_drafts, showDiscardDrafts);
        final boolean archiveVisible = mAccount.supportsCapability(AccountCapabilities.ARCHIVE)
                && mFolder != null && mFolder.supportsCapability(FolderCapabilities.ARCHIVE)
                && !mFolder.isTrash();
        Utils.setMenuItemVisibility(menu, R.id.archive, archiveVisible);
        Utils.setMenuItemVisibility(menu, R.id.remove_folder, !archiveVisible && mFolder != null
                && mFolder.supportsCapability(FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES)
                && !mFolder.isProviderFolder());
        final MenuItem removeFolder = menu.findItem(R.id.remove_folder);
        if (removeFolder != null) {
            removeFolder.setTitle(getString(R.string.remove_folder, mFolder.name));
        }
        Utils.setMenuItemVisibility(menu, R.id.report_spam,
                mAccount.supportsCapability(AccountCapabilities.REPORT_SPAM) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.REPORT_SPAM)
                        && !mConversation.spam);
        Utils.setMenuItemVisibility(menu, R.id.mark_not_spam,
                mAccount.supportsCapability(AccountCapabilities.REPORT_SPAM) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.MARK_NOT_SPAM)
                        && mConversation.spam);
        Utils.setMenuItemVisibility(menu, R.id.report_phishing,
                mAccount.supportsCapability(AccountCapabilities.REPORT_PHISHING) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.REPORT_PHISHING)
                        && !mConversation.phishing);
        Utils.setMenuItemVisibility(menu, R.id.mute,
                        mAccount.supportsCapability(AccountCapabilities.MUTE) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.DESTRUCTIVE_MUTE)
                        && !mConversation.muted);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean handled = false;

        switch (item.getItemId()) {
            case R.id.inside_conversation_unread:
                markUnread();
                handled = true;
                break;
        }

        return handled;
    }

    @Override
    public ConversationUpdater getListController() {
        final ControllableActivity activity = (ControllableActivity) getActivity();
        return activity != null ? activity.getConversationUpdater() : null;
    }

    @Override
    public MessageCursor getMessageCursor() {
        return mCursor;
    }

    private void markUnread() {
        // Ignore unsafe calls made after a fragment is detached from an activity
        final ControllableActivity activity = (ControllableActivity) getActivity();
        if (activity == null) {
            LogUtils.w(LOG_TAG, "ignoring markUnread for conv=%s", mConversation.id);
            return;
        }

        if (mViewState == null) {
            LogUtils.i(LOG_TAG, "ignoring markUnread for conv with no view state (%d)",
                    mConversation.id);
            return;
        }
        activity.getConversationUpdater().markConversationMessagesUnread(mConversation,
                mViewState.getUnreadMessageUris(), mViewState.getConversationInfo());
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
     * Handles a request to show a new conversation list, either from a search
     * query or for viewing a folder. This will initiate a data load, and hence
     * must be called on the UI thread.
     */
    private void showConversation() {
        final boolean disableOffscreenLoading = DISABLE_OFFSCREEN_LOADING
                || (mConversation.isRemote
                        || mConversation.getNumMessages() > mMaxAutoLoadMessages);
        if (!mUserVisible && disableOffscreenLoading) {
            LogUtils.v(LOG_TAG, "Fragment not user-visible, not showing conversation: %s",
                    mConversation.uri);
            mDeferredConversationLoad = true;
            return;
        }
        LogUtils.v(LOG_TAG,
                "Fragment is short or user-visible, immediately rendering conversation: %s",
                mConversation.uri);
        mWebView.setVisibility(View.VISIBLE);
        getLoaderManager().initLoader(MESSAGE_LOADER_ID, Bundle.EMPTY, mMessageLoaderCallbacks);
        if (mUserVisible) {
            final SubjectDisplayChanger sdc = mActivity.getSubjectDisplayChanger();
            if (sdc != null) {
                sdc.setSubject(mConversation.subject);
            }
        }
        // TODO(mindyp): don't show loading status for a previously rendered
        // conversation. Ielieve this is better done by making sure don't show loading status
        // until XX ms have passed without loading completed.
        showLoadingStatus();
    }

    public Conversation getConversation() {
        return mConversation;
    }

    private void renderConversation(MessageCursor messageCursor) {
        final String convHtml = renderMessageBodies(messageCursor, mEnableContentReadySignal);

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
    private String renderMessageBodies(MessageCursor messageCursor,
            boolean enableContentReadySignal) {
        int pos = -1;

        LogUtils.d(LOG_TAG, "IN renderMessageBodies, fragment=%s", this);
        boolean allowNetworkImages = false;

        // TODO: re-use any existing adapter item state (expanded, details expanded, show pics)

        // Walk through the cursor and build up an overlay adapter as you go.
        // Each overlay has an entry in the adapter for easy scroll handling in the container.
        // Items are not necessarily 1:1 in cursor and adapter because of super-collapsed blocks.
        // When adding adapter items, also add their heights to help the container later determine
        // overlay dimensions.

        // When re-rendering, prevent ConversationContainer from laying out overlays until after
        // the new spacers are positioned by WebView.
        mConversationContainer.invalidateSpacerGeometry();

        mAdapter.clear();

        // re-evaluate the message parts of the view state, since the messages may have changed
        // since the previous render
        final ConversationViewState prevState = mViewState;
        mViewState = new ConversationViewState(prevState);

        // N.B. the units of height for spacers are actually dp and not px because WebView assumes
        // a pixel is an mdpi pixel, unless you set device-dpi.

        // add a single conversation header item
        final int convHeaderPos = mAdapter.addConversationHeader(mConversation);
        final int convHeaderPx = measureOverlayHeight(convHeaderPos);

        final int sideMarginPx = getResources().getDimensionPixelOffset(
                R.dimen.conversation_view_margin_side) + getResources().getDimensionPixelOffset(
                R.dimen.conversation_message_content_margin_side);

        mTemplates.startConversation(mWebView.screenPxToWebPx(sideMarginPx),
                mWebView.screenPxToWebPx(convHeaderPx));

        int collapsedStart = -1;
        ConversationMessage prevCollapsedMsg = null;
        boolean prevSafeForImages = false;

        while (messageCursor.moveToPosition(++pos)) {
            final ConversationMessage msg = messageCursor.getMessage();

            // TODO: save/restore 'show pics' state
            final boolean safeForImages = msg.alwaysShowImages /* || savedStateSaysSafe */;
            allowNetworkImages |= safeForImages;

            final Integer savedExpanded = prevState.getExpansionState(msg);
            final int expandedState;
            if (savedExpanded != null) {
                if (ExpansionState.isSuperCollapsed(savedExpanded) && messageCursor.isLast()) {
                    // override saved state when this is now the new last message
                    // this happens to the second-to-last message when you discard a draft
                    expandedState = ExpansionState.EXPANDED;
                } else {
                    expandedState = savedExpanded;
                }
            } else {
                // new messages that are not expanded default to being eligible for super-collapse
                expandedState = (!msg.read || msg.starred || messageCursor.isLast()) ?
                        ExpansionState.EXPANDED : ExpansionState.SUPER_COLLAPSED;
            }
            mViewState.setExpansionState(msg, expandedState);

            // save off "read" state from the cursor
            // later, the view may not match the cursor (e.g. conversation marked read on open)
            // however, if a previous state indicated this message was unread, trust that instead
            // so "mark unread" marks all originally unread messages
            mViewState.setReadState(msg, msg.read && !prevState.isUnread(msg));

            // We only want to consider this for inclusion in the super collapsed block if
            // 1) The we don't have previous state about this message  (The first time that the
            //    user opens a conversation)
            // 2) The previously saved state for this message indicates that this message is
            //    in the super collapsed block.
            if (ExpansionState.isSuperCollapsed(expandedState)) {
                // contribute to a super-collapsed block that will be emitted just before the
                // next expanded header
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

            renderMessage(msg, ExpansionState.isExpanded(expandedState), safeForImages);
        }

        mWebView.getSettings().setBlockNetworkImage(!allowNetworkImages);

        // If the conversation has specified a base uri, use it here, use mBaseUri
        final String conversationBaseUri = mConversation.conversationBaseUri != null ?
                mConversation.conversationBaseUri.toString() : mBaseUri;
        return mTemplates.endConversation(mBaseUri, conversationBaseUri, 320,
                mWebView.getViewportWidth(), enableContentReadySignal);
    }

    private void renderSuperCollapsedBlock(int start, int end) {
        final int blockPos = mAdapter.addSuperCollapsedBlock(start, end);
        final int blockPx = measureOverlayHeight(blockPos);
        mTemplates.appendSuperCollapsedHtml(start, mWebView.screenPxToWebPx(blockPx));
    }

    private void renderMessage(ConversationMessage msg, boolean expanded,
            boolean safeForImages) {
        final int headerPos = mAdapter.addMessageHeader(msg, expanded);
        final MessageHeaderItem headerItem = (MessageHeaderItem) mAdapter.getItem(headerPos);

        final int footerPos = mAdapter.addMessageFooter(headerItem);

        // Measure item header and footer heights to allocate spacers in HTML
        // But since the views themselves don't exist yet, render each item temporarily into
        // a host view for measurement.
        final int headerPx = measureOverlayHeight(headerPos);
        final int footerPx = measureOverlayHeight(footerPos);

        mTemplates.appendMessageHtml(msg, expanded, safeForImages,
                mWebView.screenPxToWebPx(headerPx), mWebView.screenPxToWebPx(footerPx));
    }

    private String renderCollapsedHeaders(MessageCursor cursor,
            SuperCollapsedBlockItem blockToReplace) {
        final List<ConversationOverlayItem> replacements = Lists.newArrayList();

        mTemplates.reset();

        for (int i = blockToReplace.getStart(), end = blockToReplace.getEnd(); i <= end; i++) {
            cursor.moveToPosition(i);
            final ConversationMessage msg = cursor.getMessage();
            final MessageHeaderItem header = mAdapter.newMessageHeaderItem(msg,
                    false /* expanded */);
            final MessageFooterItem footer = mAdapter.newMessageFooterItem(header);

            final int headerPx = measureOverlayHeight(header);
            final int footerPx = measureOverlayHeight(footer);

            mTemplates.appendMessageHtml(msg, false /* expanded */, msg.alwaysShowImages,
                    mWebView.screenPxToWebPx(headerPx), mWebView.screenPxToWebPx(footerPx));
            replacements.add(header);
            replacements.add(footer);

            mViewState.setExpansionState(msg, ExpansionState.COLLAPSED);
        }

        mAdapter.replaceSuperCollapsedBlock(blockToReplace, replacements);

        return mTemplates.emit();
    }

    private int measureOverlayHeight(int position) {
        return measureOverlayHeight(mAdapter.getItem(position));
    }

    /**
     * Measure the height of an adapter view by rendering an adapter item into a temporary
     * host view, and asking the view to immediately measure itself. This method will reuse
     * a previous adapter view from {@link ConversationContainer}'s scrap views if one was generated
     * earlier.
     * <p>
     * After measuring the height, this method also saves the height in the
     * {@link ConversationOverlayItem} for later use in overlay positioning.
     *
     * @param convItem adapter item with data to render and measure
     * @return height of the rendered view in screen px
     */
    private int measureOverlayHeight(ConversationOverlayItem convItem) {
        final int type = convItem.getType();

        final View convertView = mConversationContainer.getScrapView(type);
        final View hostView = mAdapter.getView(convItem, convertView, mConversationContainer,
                true /* measureOnly */);
        if (convertView == null) {
            mConversationContainer.addScrapView(type, hostView);
        }

        final int heightPx = mConversationContainer.measureOverlay(hostView);
        convItem.setHeight(heightPx);
        convItem.markMeasurementValid();

        return heightPx;
    }

    private void onConversationSeen() {
        // Ignore unsafe calls made after a fragment is detached from an activity
        final ControllableActivity activity = (ControllableActivity) getActivity();
        if (activity == null) {
            LogUtils.w(LOG_TAG, "ignoring onConversationSeen for conv=%s", mConversation.id);
            return;
        }

        mViewState.setInfoForConversation(mConversation);

        // mark viewed/read if not previously marked viewed by this conversation view,
        // or if unread messages still exist in the message list cursor
        // we don't want to keep marking viewed on rotation or restore
        // but we do want future re-renders to mark read (e.g. "New message from X" case)
        if (!mConversation.isViewed() || (mCursor != null && !mCursor.isConversationRead())) {
            final ConversationUpdater listController = activity.getConversationUpdater();
            // The conversation cursor may not have finished loading by now (when launched via
            // notification), so watch for when it finishes and mark it read then.
            if (listController.getConversationListCursor() == null) {
                LogUtils.i(LOG_TAG, "deferring conv mark read on open for id=%d",
                        mConversation.id);
                mMarkReadObserver = new MarkReadObserver(listController);
                listController.registerConversationListObserver(mMarkReadObserver);
            } else {
                markReadOnSeen(listController);
            }
        }

        activity.getListHandler().onConversationSeen(mConversation);
    }

    private void markReadOnSeen(ConversationUpdater listController) {
        // Mark the conversation viewed and read.
        listController.markConversationsRead(Arrays.asList(mConversation), true /* read */,
                true /* viewed */);

        // and update the Message objects in the cursor so the next time a cursor update happens
        // with these messages marked read, we know to ignore it
        if (mCursor != null) {
            mCursor.markMessagesRead();
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
        final int h = mWebView.screenPxToWebPx(newSpacerHeightPx);
        LogUtils.i(LAYOUT_TAG, "setting HTML spacer h=%dwebPx (%dscreenPx)", h,
                newSpacerHeightPx);
        mWebView.loadUrl(String.format("javascript:setMessageHeaderSpacerHeight('%s', %d);",
                mTemplates.getMessageDomId(item.message), h));
    }

    @Override
    public void setMessageExpanded(MessageHeaderItem item, int newSpacerHeightPx) {
        mConversationContainer.invalidateSpacerGeometry();

        // show/hide the HTML message body and update the spacer height
        final int h = mWebView.screenPxToWebPx(newSpacerHeightPx);
        LogUtils.i(LAYOUT_TAG, "setting HTML spacer expanded=%s h=%dwebPx (%dscreenPx)",
                item.isExpanded(), h, newSpacerHeightPx);
        mWebView.loadUrl(String.format("javascript:setMessageBodyVisible('%s', %s, %d);",
                mTemplates.getMessageDomId(item.message), item.isExpanded(), h));

        mViewState.setExpansionState(item.message,
                item.isExpanded() ? ExpansionState.EXPANDED : ExpansionState.COLLAPSED);
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

    private void showNewMessageNotification(NewMessagesInfo info) {
        final TextView descriptionView = (TextView) mNewMessageBar.findViewById(
                R.id.new_message_description);
        descriptionView.setText(info.getNotificationText());
        mNewMessageBar.setVisibility(View.VISIBLE);
    }

    private void onNewMessageBarClick() {
        mNewMessageBar.setVisibility(View.GONE);

        renderConversation(mCursor); // mCursor is already up-to-date per onLoadFinished()
    }

    private static class MessageLoader extends CursorLoader {
        private boolean mDeliveredFirstResults = false;
        private final Conversation mConversation;
        private final ConversationController mController;

        public MessageLoader(Context c, Conversation conv, ConversationController controller) {
            super(c, conv.messageListUri, UIProvider.MESSAGE_PROJECTION, null, null, null);
            mConversation = conv;
            mController = controller;
        }

        @Override
        public Cursor loadInBackground() {
            return new MessageCursor(super.loadInBackground(), mConversation, mController);
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

    @Override
    public String toString() {
        // log extra info at DEBUG level or finer
        final String s = super.toString();
        if (!LogUtils.isLoggable(LOG_TAG, LogUtils.DEBUG) || mConversation == null) {
            return s;
        }
        return "(" + s + " subj=" + mConversation.subject + ")";
    }

    private Address getAddress(String rawFrom) {
        Address addr = mAddressCache.get(rawFrom);
        if (addr == null) {
            addr = Address.getEmailAddress(rawFrom);
            mAddressCache.put(rawFrom, addr);
        }
        return addr;
    }

    @Override
    public Account getAccount() {
        return mAccount;
    }

    private class ConversationWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            // Ignore unsafe calls made after a fragment is detached from an activity
            final ControllableActivity activity = (ControllableActivity) getActivity();
            if (activity == null || !mViewsCreated) {
                LogUtils.i(LOG_TAG, "ignoring CVF.onPageFinished, url=%s fragment=%s", url,
                        ConversationViewFragment.this);
                return;
            }

            LogUtils.i(LOG_TAG, "IN CVF.onPageFinished, url=%s fragment=%s act=%s", url,
                    ConversationViewFragment.this, getActivity());

            super.onPageFinished(view, url);

            // TODO: save off individual message unread state (here, or in onLoadFinished?) so
            // 'mark unread' restores the original unread state for each individual message

            if (mUserVisible) {
                onConversationSeen();
            }
            if (!mEnableContentReadySignal) {
                notifyConversationLoaded(mConversation);
                dismissLoadingStatus();
            }
            final Set<String> emailAddresses = Sets.newHashSet();
            for (Address addr : mAddressCache.values()) {
                emailAddresses.add(addr.getAddress());
            }
            mContactLoaderCallbacks.setSenders(emailAddresses);
            getLoaderManager().restartLoader(CONTACT_LOADER_ID, Bundle.EMPTY,
                    mContactLoaderCallbacks);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            final Activity activity = getActivity();
            if (!mViewsCreated || activity == null) {
                return false;
            }

            boolean result = false;
            final Intent intent;
            Uri uri = Uri.parse(url);
            if (!Utils.isEmpty(mAccount.viewIntentProxyUri)) {
                intent = new Intent(Intent.ACTION_VIEW, mAccount.viewIntentProxyUri);
                intent.putExtra(ViewProxyExtras.EXTRA_ORIGINAL_URI, uri);
                intent.putExtra(ViewProxyExtras.EXTRA_ACCOUNT, mAccount);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.putExtra(Browser.EXTRA_APPLICATION_ID, activity.getPackageName());
            }

            try {
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
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
     * Notifies the {@link ConversationViewable.ConversationCallbacks} that the conversation has
     * been loaded.
     */
    public void notifyConversationLoaded(Conversation c) {
        // Do nothing.
    }

    /**
     * Notifies the {@link ConversationViewable.ConversationCallbacks} that the conversation has
     * failed to load.
     */
    protected void notifyConversationLoadError(Conversation c) {
        mActivity.onConversationLoadError();
    }

    private void showLoadingStatus() {
        mBackgroundView.setVisibility(View.VISIBLE);
        String senders = mConversation.getSenders(mContext);
        if (!TextUtils.isEmpty(senders) && mConversation.subject != null) {
            mInfoView.setVisibility(View.VISIBLE);
            mSendersView.setText(senders);
            mSubjectView.setText(createSubjectSnippet(mConversation.subject,
                    mConversation.getSnippet()));
        } else {
            mProgressView.setVisibility(View.VISIBLE);
        }
    }

    private void dismissLoadingStatus() {
        // Fade out the info view.
        if (mBackgroundView.getVisibility() == View.VISIBLE) {
            Animator animator = AnimatorInflater.loadAnimator(mContext, R.anim.fade_out);
            animator.setTarget(mBackgroundView);
            animator.addListener(new AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (mProgressView.getVisibility() != View.VISIBLE) {
                        mProgressView.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mBackgroundView.setVisibility(View.GONE);
                    mInfoView.setVisibility(View.GONE);
                    mProgressView.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    // Do nothing.
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                    // Do nothing.
                }
            });
            animator.start();
        } else {
            mBackgroundView.setVisibility(View.GONE);
            mInfoView.setVisibility(View.GONE);
            mProgressView.setVisibility(View.GONE);
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

        private void showConversation(Conversation conv) {
            notifyConversationLoaded(conv);
            dismissLoadingStatus();
        }

        @SuppressWarnings("unused")
        public void onContentReady() {
            final Conversation conv = mConversation;
            try {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        LogUtils.d(LOG_TAG, "ANIMATION STARTED, ready to draw. t=%s",
                                SystemClock.uptimeMillis());
                        showConversation(conv);
                    }
                });
            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.onContentReady");
                // Still try to show the conversation.
                showConversation(conv);
            }
        }
    }

    private class NewMessagesInfo {
        int count;
        String senderAddress;

        /**
         * Return the display text for the new message notification overlay. It will be formatted
         * appropriately for a single new message vs. multiple new messages.
         *
         * @return display text
         */
        public String getNotificationText() {
            final Object param;
            if (count > 1) {
                param = count;
            } else {
                final Address addr = getAddress(senderAddress);
                param = TextUtils.isEmpty(addr.getName()) ? addr.getAddress() : addr.getName();
            }
            return getResources().getQuantityString(R.plurals.new_incoming_messages, count, param);
        }
    }

    private class MessageLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new MessageLoader(mContext, mConversation, ConversationViewFragment.this);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            MessageCursor messageCursor = (MessageCursor) data;

            // ignore truly duplicate results
            // this can happen when restoring after rotation
            if (mCursor == messageCursor) {
                return;
            }

            if (LogUtils.isLoggable(LOG_TAG, LogUtils.DEBUG)) {
                LogUtils.d(LOG_TAG, "LOADED CONVERSATION= %s", messageCursor.getDebugDump());
            }

            // TODO: handle ERROR status

            // When the last cursor had message(s), and the new version has no messages,
            // we need to exit conversation view.
            if (messageCursor.getCount() == 0 && mCursor != null) {

                if (mUserVisible) {
                    // need to exit this view- conversation may have been deleted, or for
                    // whatever reason is now invalid (e.g. discard single draft)
                    //
                    // N.B. this may involve a fragment transaction, which FragmentManager will
                    // refuse to execute directly within onLoadFinished. Make sure the controller
                    // knows.
                    LogUtils.i(LOG_TAG, "CVF: visible conv has no messages, exiting conv mode");
                    mActivity.getListHandler().onConversationSelected(null,
                            true /* inLoaderCallbacks */);
                } else {
                    // we expect that the pager adapter will remove this conversation fragment
                    // on its own due to a separate conversation cursor update
                    // (we might get here if the message list update fires first. nothing to do
                    // because we expect to be torn down soon.)
                    LogUtils.i(LOG_TAG, "CVF: offscreen conv has no messages, ignoring update"
                            + " in anticipation of conv cursor update. c=%s", mConversation.uri);
                }

                return;
            }

            // ignore cursors that are still loading results
            if (!messageCursor.isLoaded()) {
                return;
            }

            /*
             * what kind of changes affect the MessageCursor?
             * 1. new message(s)
             * 2. read/unread state change
             * 3. deleted message, either regular or draft
             * 4. updated message, either from self or from others, updated in content or state
             * or sender
             * 5. star/unstar of message (technically similar to #1)
             * 6. other label change
             *
             * Use MessageCursor.hashCode() to sort out interesting vs. no-op cursor updates.
             */

            if (mCursor == null) {
                LogUtils.i(LOG_TAG, "CONV RENDER: existing cursor is null, rendering from scratch");
            } else {
                final NewMessagesInfo info = getNewIncomingMessagesInfo(messageCursor);

                if (info.count > 0 || messageCursor.hashCode() == mCursor.hashCode()) {

                    if (info.count > 0) {
                        // don't immediately render new incoming messages from other senders
                        // (to avoid a new message from losing the user's focus)
                        LogUtils.i(LOG_TAG, "CONV RENDER: conversation updated"
                                + ", holding cursor for new incoming message");
                        showNewMessageNotification(info);
                    } else {
                        LogUtils.i(LOG_TAG, "CONV RENDER: uninteresting update"
                                + ", ignoring this conversation update");
                    }

                    // update mCursor reference because the old one is about to be closed by
                    // CursorLoader
                    mCursor = messageCursor;
                    return;
                }

                // cursors are different, and not due to an incoming message. fall through and
                // render.
                LogUtils.i(LOG_TAG, "CONV RENDER: conversation updated"
                        + ", but not due to incoming message. rendering.");
            }

            renderConversation(messageCursor);

            // TODO: if this is not user-visible, delay render until user-visible fragment is done.
            // This is needed in addition to the showConversation() delay to speed up rotation and
            // restoration.
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            mCursor = null;
        }

        private NewMessagesInfo getNewIncomingMessagesInfo(MessageCursor newCursor) {
            final NewMessagesInfo info = new NewMessagesInfo();

            int pos = -1;
            while (newCursor.moveToPosition(++pos)) {
                final Message m = newCursor.getMessage();
                if (!mViewState.contains(m)) {
                    LogUtils.i(LOG_TAG, "conversation diff: found new msg: %s", m.uri);

                    final Address from = getAddress(m.from);
                    // distinguish ours from theirs
                    // new messages from the account owner should not trigger a notification
                    if (mAccount.ownsFromAddress(from.getAddress())) {
                        LogUtils.i(LOG_TAG, "found message from self: %s", m.uri);
                        continue;
                    }

                    info.count++;
                    info.senderAddress = m.from;
                }
            }
            return info;
        }

    }

    /**
     * Inner class to to asynchronously load contact data for all senders in the conversation,
     * and notify observers when the data is ready.
     *
     */
    private class ContactLoaderCallbacks implements ContactInfoSource,
            LoaderManager.LoaderCallbacks<ImmutableMap<String, ContactInfo>> {

        private Set<String> mSenders;
        private ImmutableMap<String, ContactInfo> mContactInfoMap;
        private DataSetObservable mObservable = new DataSetObservable();

        public void setSenders(Set<String> emailAddresses) {
            mSenders = emailAddresses;
        }

        @Override
        public Loader<ImmutableMap<String, ContactInfo>> onCreateLoader(int id, Bundle args) {
            return new SenderInfoLoader(mContext, mSenders);
        }

        @Override
        public void onLoadFinished(Loader<ImmutableMap<String, ContactInfo>> loader,
                ImmutableMap<String, ContactInfo> data) {
            mContactInfoMap = data;
            mObservable.notifyChanged();
        }

        @Override
        public void onLoaderReset(Loader<ImmutableMap<String, ContactInfo>> loader) {
        }

        @Override
        public ContactInfo getContactInfo(String email) {
            if (mContactInfoMap == null) {
                return null;
            }
            return mContactInfoMap.get(email);
        }

        @Override
        public void registerObserver(DataSetObserver observer) {
            mObservable.registerObserver(observer);
        }

        @Override
        public void unregisterObserver(DataSetObserver observer) {
            mObservable.unregisterObserver(observer);
        }

    }

    private class MarkReadObserver extends DataSetObserver {
        private final ConversationUpdater mListController;

        private MarkReadObserver(ConversationUpdater listController) {
            mListController = listController;
        }

        @Override
        public void onChanged() {
            if (mListController.getConversationListCursor() == null) {
                // nothing yet, keep watching
                return;
            }
            // done loading, safe to mark read now
            mListController.unregisterConversationListObserver(this);
            mMarkReadObserver = null;
            LogUtils.i(LOG_TAG, "running deferred conv mark read on open, id=%d", mConversation.id);
            markReadOnSeen(mListController);
        }
    }

    private class SetCookieTask extends AsyncTask<Void, Void, Void> {
        final String mUri;
        final String mCookie;

        SetCookieTask(String uri, String cookie) {
            mUri = uri;
            mCookie = cookie;
        }

        @Override
        public Void doInBackground(Void... args) {
            final CookieSyncManager csm =
                CookieSyncManager.createInstance(mContext);
            CookieManager.getInstance().setCookie(mUri, mCookie);
            csm.sync();
            return null;
        }
    }

    public void onConversationUpdated(Conversation conv) {
        final ConversationViewHeader headerView = (ConversationViewHeader) mConversationContainer
                .findViewById(R.id.conversation_header);
        mConversation = conv;
        headerView.onConversationUpdated(conv);
    }
}
