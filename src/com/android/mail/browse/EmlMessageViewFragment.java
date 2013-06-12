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

package com.android.mail.browse;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.android.mail.providers.Account;
import com.android.mail.providers.Address;
import com.android.mail.ui.AbstractConversationWebViewClient;
import com.android.mail.ui.ContactLoaderCallbacks;
import com.android.mail.ui.SecureConversationViewController;
import com.android.mail.ui.SecureConversationViewControllerCallbacks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fragment that is used to view EML files. It is mostly stubs
 * that calls {@link SecureConversationViewController} to do most
 * of the rendering work.
 */
public class EmlMessageViewFragment extends Fragment
        implements SecureConversationViewControllerCallbacks,
        LoaderManager.LoaderCallbacks<ConversationMessage> {
    private static final String ARG_EML_FILE_URI = "eml_file_uri";
    private static final String BASE_URI = "x-thread://message/rfc822/";
    private static final int MESSAGE_LOADER = 0;
    private static final int CONTACT_LOADER = 1;

    private final Handler mHandler = new Handler();

    private EmlWebViewClient mWebViewClient;
    private SecureConversationViewController mViewController;
    private ContactLoaderCallbacks mContactLoaderCallbacks;

    private Uri mEmlFileUri;

    /**
     * Cache of email address strings to parsed Address objects.
     * <p>
     * Remember to synchronize on the map when reading or writing to this cache, because some
     * instances use it off the UI thread (e.g. from WebView).
     */
    protected final Map<String, Address> mAddressCache = Collections.synchronizedMap(
            new HashMap<String, Address>());

    private class EmlWebViewClient extends AbstractConversationWebViewClient {
        public EmlWebViewClient(Account account) {
            super(account);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            mViewController.dismissLoadingStatus();

            final Set<String> emailAddresses = Sets.newHashSet();
            final List<Address> cacheCopy;
            synchronized (mAddressCache) {
                cacheCopy = ImmutableList.copyOf(mAddressCache.values());
            }
            for (Address addr : cacheCopy) {
                emailAddresses.add(addr.getAddress());
            }
            final ContactLoaderCallbacks callbacks = getContactInfoSource();
            callbacks.setSenders(emailAddresses);
            getLoaderManager().restartLoader(CONTACT_LOADER, Bundle.EMPTY, callbacks);
        }
    };

    /**
     * Creates a new instance of {@link EmlMessageViewFragment},
     * initialized to display an eml file from the specified {@link Uri}.
     */
    public static EmlMessageViewFragment newInstance(Uri emlFileUri) {
        EmlMessageViewFragment f = new EmlMessageViewFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_EML_FILE_URI, emlFileUri);
        f.setArguments(args);
        return f;
    }

    /**
     * Constructor needs to be public to handle orientation changes and activity
     * lifecycle events.
     */
    public EmlMessageViewFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        Bundle args = getArguments();
        mEmlFileUri = args.getParcelable(ARG_EML_FILE_URI);

        mWebViewClient = new EmlWebViewClient(null);
        mViewController = new SecureConversationViewController(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return mViewController.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mWebViewClient.setActivity(getActivity());
        mViewController.onActivityCreated(savedInstanceState);
    }

    // Start SecureConversationViewControllerCallbacks

    @Override
    public Handler getHandler() {
        return mHandler;
    }

    @Override
    public AbstractConversationWebViewClient getWebViewClient() {
        return mWebViewClient;
    }

    @Override
    public Fragment getFragment() {
        return this;
    }

    @Override
    public void setupConversationHeaderView(ConversationViewHeader headerView) {
        // DO NOTHING
    }

    @Override
    public boolean isViewVisibleToUser() {
        return true;
    }

    @Override
    public ContactLoaderCallbacks getContactInfoSource() {
        if (mContactLoaderCallbacks == null) {
            mContactLoaderCallbacks = new ContactLoaderCallbacks(getActivity());
        }
        return mContactLoaderCallbacks;
    }

    @Override
    public ConversationAccountController getConversationAccountController() {
        return null;
    }

    @Override
    public Map<String, Address> getAddressCache() {
        return mAddressCache;
    }

    @Override
    public void setupMessageHeaderVeiledMatcher(MessageHeaderView messageHeaderView) {
        // DO NOTHING
    }

    @Override
    public void startMessageLoader() {
        getLoaderManager().initLoader(MESSAGE_LOADER, null, this);
    }

    @Override
    public String getBaseUri() {
        return BASE_URI;
    }

    @Override
    public boolean isViewOnlyMode() {
        return true;
    }

    // End SecureConversationViewControllerCallbacks

    // Start LoaderCallbacks

    @Override
    public Loader<ConversationMessage> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case MESSAGE_LOADER:
                return new EmlMessageLoader(getActivity(), mEmlFileUri);
            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<ConversationMessage> loader, ConversationMessage data) {
        mViewController.setSubject(data.subject);
        mViewController.renderMessage(data);
    }

    @Override
    public void onLoaderReset(Loader<ConversationMessage> loader) {
        // Do nothing
    }

    // End LoaderCallbacks
}
