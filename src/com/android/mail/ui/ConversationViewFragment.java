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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
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
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.browse.ConversationContainer;
import com.android.mail.browse.ConversationOverlayItem;
import com.android.mail.browse.ConversationViewAdapter;
import com.android.mail.browse.ConversationViewAdapter.ConversationAccountController;
import com.android.mail.browse.ConversationViewAdapter.MessageFooterItem;
import com.android.mail.browse.ConversationViewAdapter.MessageHeaderItem;
import com.android.mail.browse.ConversationViewAdapter.SuperCollapsedBlockItem;
import com.android.mail.browse.ConversationViewHeader;
import com.android.mail.browse.ConversationWebView;
import com.android.mail.browse.ConversationWebView.ContentSizeChangeListener;
import com.android.mail.browse.MessageCursor;
import com.android.mail.browse.MessageCursor.ConversationController;
import com.android.mail.browse.MessageCursor.ConversationMessage;
import com.android.mail.browse.MessageHeaderView;
import com.android.mail.browse.MessageHeaderView.MessageHeaderViewCallbacks;
import com.android.mail.browse.SuperCollapsedBlock;
import com.android.mail.browse.WebViewContextMenu;
import com.android.mail.providers.Account;
import com.android.mail.providers.Address;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider.ViewProxyExtras;
import com.android.mail.ui.ConversationViewState.ExpansionState;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.List;
import java.util.Set;


/**
 * The conversation view UI component.
 */
public final class ConversationViewFragment extends AbstractConversationViewFragment implements
        ConversationViewHeader.ConversationViewHeaderCallbacks,
        MessageHeaderViewCallbacks,
        SuperCollapsedBlock.OnClickListener,
        ConversationController,
        ConversationAccountController {

    private static final String LOG_TAG = LogTag.getLogTag();
    public static final String LAYOUT_TAG = "ConvLayout";

    /** Do not auto load data when create this {@link ConversationView}. */
    public static final int NO_AUTO_LOAD = 0;
    /** Auto load data but do not show any animation. */
    public static final int AUTO_LOAD_BACKGROUND = 1;
    /** Auto load data and show animation. */
    public static final int AUTO_LOAD_VISIBLE = 2;

    private ConversationContainer mConversationContainer;

    private ConversationWebView mWebView;

    private View mNewMessageBar;

    private View mBackgroundView;

    private View mInfoView;

    private TextView mSendersView;

    private TextView mSubjectView;

    private View mProgressView;

    private HtmlConversationTemplates mTemplates;

    private final Handler mHandler = new Handler();

    private final MailJsBridge mJsBridge = new MailJsBridge();

    private final WebViewClient mWebViewClient = new ConversationWebViewClient();

    private ConversationViewAdapter mAdapter;

    private boolean mViewsCreated;

    /**
     * Temporary string containing the message bodies of the messages within a super-collapsed
     * block, for one-time use during block expansion. We cannot easily pass the body HTML
     * into JS without problematic escaping, so hold onto it momentarily and signal JS to fetch it
     * using {@link MailJsBridge}.
     */
    private String mTempBodiesHtml;

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

    private boolean mEnableContentReadySignal;
    private Runnable mDelayedShow = new Runnable() {
        @Override
        public void run() {
            mBackgroundView.setVisibility(View.VISIBLE);
            String senders = mConversation.getSenders(getContext());
            if (!TextUtils.isEmpty(senders) && mConversation.subject != null) {
                mInfoView.setVisibility(View.VISIBLE);
                mSendersView.setText(senders);
                mSubjectView.setText(createSubjectSnippet(mConversation.subject,
                        mConversation.getSnippet()));
            } else {
                mProgressView.setVisibility(View.VISIBLE);
            }
        }
    };

    private ContentSizeChangeListener mWebViewSizeChangeListener;

    private static final String BUNDLE_VIEW_STATE = "viewstate";
    private static int sSubjectColor = Integer.MIN_VALUE;
    private static int sSnippetColor = Integer.MIN_VALUE;
    private static long sMinDelay = -1;

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

    @Override
    public void onAccountChanged() {
        // settings may have been updated; refresh views that are known to
        // depend on settings
        mConversationContainer.getSnapHeader().onAccountChanged();
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        LogUtils.d(LOG_TAG, "IN CVF.onActivityCreated, this=%s subj=%s", this,
                mConversation.subject);
        super.onActivityCreated(savedInstanceState);
        Context context = getContext();
        mTemplates = new HtmlConversationTemplates(context);

        final FormattedDateBuilder dateBuilder = new FormattedDateBuilder(context);

        mAdapter = new ConversationViewAdapter(mActivity, this,
                getLoaderManager(), this, getContactInfoSource(), this,
                this, mAddressCache, dateBuilder);
        mConversationContainer.setOverlayAdapter(mAdapter);

        // set up snap header (the adapter usually does this with the other ones)
        final MessageHeaderView snapHeader = mConversationContainer.getSnapHeader();
        snapHeader.initialize(dateBuilder, this, mAddressCache);
        snapHeader.setCallbacks(this);
        snapHeader.setContactInfoSource(getContactInfoSource());

        mMaxAutoLoadMessages = getResources().getInteger(R.integer.max_auto_load_messages);

        mWebView.setOnCreateContextMenuListener(new WebViewContextMenu(getActivity()));

        showConversation();

        if (mConversation.conversationBaseUri != null &&
                !TextUtils.isEmpty(mConversation.conversationCookie)) {
            // Set the cookie for this base url
            new SetCookieTask(mConversation.conversationBaseUri.toString(),
                    mConversation.conversationCookie).execute();
        }
    }

    private CharSequence createSubjectSnippet(CharSequence subject, CharSequence snippet) {
        if (TextUtils.isEmpty(subject) && TextUtils.isEmpty(snippet)) {
            return "";
        }
        if (subject == null) {
            subject = "";
        }
        if (snippet == null) {
            snippet = "";
        }
        SpannableStringBuilder subjectText = new SpannableStringBuilder(getContext().getString(
                R.string.subject_and_snippet, subject, snippet));
        ensureSubjectSnippetColors();
        int snippetStart = 0;
        int fontColor = sSubjectColor;
        subjectText.setSpan(new ForegroundColorSpan(fontColor), 0, subject.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        snippetStart = subject.length() + 1;
        fontColor = sSnippetColor;
        subjectText.setSpan(new ForegroundColorSpan(fontColor), snippetStart, subjectText.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return subjectText;
    }

    private void ensureSubjectSnippetColors() {
        if (sSubjectColor == Integer.MIN_VALUE) {
            Resources res = getContext().getResources();
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
        // TODO(mindyp):
        //PAGE READINESS SIGNAL FOR JELLYBEAN AND NEWER
        // Notify the app on 'webkitAnimationStart' of a simple dummy element with a simple no-op
        // animation that immediately runs on page load. The app uses this as a signal that the
        // content is loaded and ready to draw, since WebView delays firing this event until the
        // layers are composited and everything is ready to draw.
        // This signal does not seem to be reliable, so just use the old method for now.
        mEnableContentReadySignal = false;
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
    }

    @Override
    protected void markUnread() {
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

    @Override
    public void onUserVisibleHintChanged() {
        if (mUserVisible && mViewsCreated) {
            Cursor cursor = getMessageCursor();
            if (cursor == null && mDeferredConversationLoad) {
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
        getLoaderManager().initLoader(MESSAGE_LOADER, Bundle.EMPTY, getMessageLoaderCallbacks());
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

        // In devices with non-integral density multiplier, screen pixels translate to non-integral
        // web pixels. Keep track of the error that occurs when we cast all heights to int
        float error = 0f;
        for (int i = blockToReplace.getStart(), end = blockToReplace.getEnd(); i <= end; i++) {
            cursor.moveToPosition(i);
            final ConversationMessage msg = cursor.getMessage();
            final MessageHeaderItem header = mAdapter.newMessageHeaderItem(msg,
                    false /* expanded */);
            final MessageFooterItem footer = mAdapter.newMessageFooterItem(header);

            final int headerPx = measureOverlayHeight(header);
            final int footerPx = measureOverlayHeight(footer);
            error += mWebView.screenPxToWebPxError(headerPx)
                    + mWebView.screenPxToWebPxError(footerPx);

            // When the error becomes greater than 1 pixel, make the next header 1 pixel taller
            int correction = 0;
            if (error >= 1) {
                correction = 1;
                error -= 1;
            }

            mTemplates.appendMessageHtml(msg, false /* expanded */, msg.alwaysShowImages,
                    mWebView.screenPxToWebPx(headerPx) + correction,
                    mWebView.screenPxToWebPx(footerPx));
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
        MessageCursor cursor = getMessageCursor();
        if (!mConversation.isViewed() || (cursor != null && !cursor.isConversationRead())) {
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
        MessageCursor cursor = getMessageCursor();
        if (cursor != null) {
            cursor.markMessagesRead();
        }
    }

    @Override
    public void onConversationViewHeaderHeightChange(int newHeight) {
        // TODO: propagate the new height to the header's HTML spacer. This can happen when labels
        // are added/removed
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
        MessageCursor cursor = getMessageCursor();
        if (cursor == null || !mViewsCreated) {
            return;
        }

        mTempBodiesHtml = renderCollapsedHeaders(cursor, item);
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

        renderConversation(getMessageCursor()); // mCursor is already up-to-date
                                                // per onLoadFinished()
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
            // We are not able to use the loader manager unless this fragment is added to the
            // activity
            if (isAdded()) {
                final Set<String> emailAddresses = Sets.newHashSet();
                for (Address addr : mAddressCache.values()) {
                    emailAddresses.add(addr.getAddress());
                }
                ContactLoaderCallbacks callbacks = getContactInfoSource();
                getContactInfoSource().setSenders(emailAddresses);
                getLoaderManager().restartLoader(CONTACT_LOADER, Bundle.EMPTY, callbacks);
            }
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
        if (mWebViewSizeChangeListener == null) {
            mWebViewSizeChangeListener = new ConversationWebView.ContentSizeChangeListener() {
                @Override
                public void onHeightChange(int h) {
                    // When WebKit says the DOM height has changed, re-measure
                    // bodies and re-position their headers.
                    // This is separate from the typical JavaScript DOM change
                    // listeners because cases like NARROW_COLUMNS text reflow do not trigger DOM
                    // events.
                    mWebView.loadUrl("javascript:measurePositions();");
                }
            };
        }
        mWebView.setContentSizeChangeListener(mWebViewSizeChangeListener);
    }

    /**
     * Notifies the {@link ConversationViewable.ConversationCallbacks} that the conversation has
     * failed to load.
     */
    protected void notifyConversationLoadError(Conversation c) {
        mActivity.onConversationLoadError();
    }

    private void showLoadingStatus() {
        if (sMinDelay == -1) {
            sMinDelay = getContext().getResources()
                    .getInteger(R.integer.conversationview_show_loading_delay);
        }
        // In case there were any other instances around, get rid of them.
        mHandler.removeCallbacks(mDelayedShow);
        mHandler.postDelayed(mDelayedShow, sMinDelay);
    }

    private void dismissLoadingStatus() {
        if (mBackgroundView.getVisibility() != View.VISIBLE) {
            // The runnable hasn't run yet, so just remove it.
            mHandler.removeCallbacks(mDelayedShow);
            return;
        }
        // Fade out the info view.
        if (mBackgroundView.getVisibility() == View.VISIBLE) {
            Animator animator = AnimatorInflater.loadAnimator(getContext(), R.anim.fade_out);
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
        @JavascriptInterface
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
        @JavascriptInterface
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
        @JavascriptInterface
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

    @Override
    public void onMessageCursorLoadFinished(Loader<Cursor> loader, Cursor data, boolean wasNull,
            boolean changed) {
        MessageCursor messageCursor = (MessageCursor) data;
        /*
         * what kind of changes affect the MessageCursor? 1. new message(s) 2.
         * read/unread state change 3. deleted message, either regular or draft
         * 4. updated message, either from self or from others, updated in
         * content or state or sender 5. star/unstar of message (technically
         * similar to #1) 6. other label change Use MessageCursor.hashCode() to
         * sort out interesting vs. no-op cursor updates.
         */
        if (!wasNull) {
            final NewMessagesInfo info = getNewIncomingMessagesInfo(messageCursor);

            if (info.count > 0 || !changed) {

                if (info.count > 0) {
                    // don't immediately render new incoming messages from other
                    // senders
                    // (to avoid a new message from losing the user's focus)
                    LogUtils.i(LOG_TAG, "CONV RENDER: conversation updated"
                            + ", holding cursor for new incoming message");
                    showNewMessageNotification(info);
                } else {
                    LogUtils.i(LOG_TAG, "CONV RENDER: uninteresting update"
                            + ", ignoring this conversation update");
                }

                // update mCursor reference because the old one is about to be
                // closed by CursorLoader
                return;
            }
        }

        // cursors are different, and not due to an incoming message. fall
        // through and render.
        LogUtils.i(LOG_TAG, "CONV RENDER: conversation updated"
                + ", but not due to incoming message. rendering.");

        // TODO: if this is not user-visible, delay render until user-visible
        // fragment is done. This is needed in addition to the
        // showConversation() delay to speed up rotation and restoration.
        renderConversation(messageCursor);
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
                // new messages from the account owner should not trigger a
                // notification
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
                CookieSyncManager.createInstance(getContext());
            CookieManager.getInstance().setCookie(mUri, mCookie);
            csm.sync();
            return null;
        }
    }

    public void onConversationUpdated(Conversation conv) {
        final ConversationViewHeader headerView = (ConversationViewHeader) mConversationContainer
                .findViewById(R.id.conversation_header);
        if (headerView != null) {
            headerView.onConversationUpdated(conv);
        }
    }
}
