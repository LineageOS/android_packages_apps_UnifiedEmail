/*
 * Copyright (C) 2013 Google Inc.
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

package com.android.mail.ads;

import android.os.Bundle;

import com.android.mail.browse.ConversationMessage;
import com.android.mail.browse.MessageCursor;
import com.android.mail.providers.Conversation;
import com.android.mail.ui.ConversationViewFragment;
import com.android.mail.ui.ConversationViewState;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

public final class AdViewFragment extends ConversationViewFragment {

    private static final String LOG_TAG = LogTag.getLogTag();

    /**
     * Constructor needs to be public to handle orientation changes and activity lifecycle events.
     */
    public AdViewFragment() {
        super();
    }

    /**
     * Creates a new instance of {@link AdViewFragment}, initialized
     * to display a conversation with other parameters inherited/copied from an existing bundle,
     * typically one created using {@link #makeBasicArgs}.
     */
    public static AdViewFragment newInstance(Bundle existingArgs,
            Conversation conversation) {
        final AdViewFragment f = new AdViewFragment();
        final Bundle args = new Bundle(existingArgs);
        args.putParcelable(ARG_CONVERSATION, conversation);
        f.setArguments(args);
        return f;
    }

    /**
     * Populate the adapter with overlay views (message headers, super-collapsed blocks, a
     * conversation header), and return an HTML document with spacer divs inserted for all overlays.
     *
     */
    @Override
    protected String renderMessageBodies(MessageCursor messageCursor,
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
        final int adHeaderPos = mAdapter.addAdHeader(mConversation);
        final int adHeaderPx = measureOverlayHeight(adHeaderPos);

        mTemplates.startConversation(mWebView.screenPxToWebPx(mSideMarginPx),
                mWebView.screenPxToWebPx(adHeaderPx));

        renderBorder(false /* contiguous */);

        if (messageCursor.moveToFirst()) {
            final ConversationMessage msg = messageCursor.getMessage();
            renderMessage(msg);
        }

        renderBorder(true /* contiguous */);

        mWebView.getSettings().setBlockNetworkImage(false);

        final boolean applyTransforms = shouldApplyTransforms();

        // If the conversation has specified a base uri, use it here, otherwise use mBaseUri
        return mTemplates.endConversation(mBaseUri, mConversation.getBaseUri(mBaseUri), 320,
                mWebView.getViewportWidth(), enableContentReadySignal, isOverviewMode(mAccount),
                applyTransforms, applyTransforms);
    }

    private void renderMessage(ConversationMessage msg) {
        mAdapter.addAdFooter();

        mTemplates.appendMessageHtml(msg, true /** expanded */, true /** showImages */,
               0 /** headerHeight */, 0 /** footerHeight */);
        timerMark("rendered message");
    }
}
