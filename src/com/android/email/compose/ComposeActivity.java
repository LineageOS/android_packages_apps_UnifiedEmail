/**
 * Copyright (c) 2011, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.email.compose;

import android.accounts.Account;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.TimingLogger;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.common.Rfc822Validator;
import com.android.email.compose.QuotedTextView.RespondInlineListener;
import com.android.email.providers.Address;
import com.android.email.providers.UIProvider;
import com.android.email.providers.Attachment;
import com.android.email.providers.protos.mock.MockAttachment;
import com.android.email.R;
import com.android.email.utils.AccountUtils;
import com.android.email.utils.MimeType;
import com.android.email.utils.Utils;
import com.android.ex.chips.RecipientEditTextView;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ComposeActivity extends Activity implements OnClickListener, OnNavigationListener,
        RespondInlineListener, OnItemSelectedListener {
    // Identifiers for which type of composition this is
    static final int COMPOSE = -1;  // also used for editing a draft
    static final int REPLY = 0;
    static final int REPLY_ALL = 1;
    static final int FORWARD = 2;

    // HTML tags used to quote reply content
    // The following style must be in-sync with
    // pinto.app.MessageUtil.QUOTE_STYLE and
    // java/com/google/caribou/ui/pinto/modules/app/messageutil.js
    // BEG_QUOTE_BIDI is also available there when we support BIDI
    private static final String BLOCKQUOTE_BEGIN = "<blockquote class=\"quote\" style=\""
            + "margin:0 0 0 .8ex;" + "border-left:1px #ccc solid;" + "padding-left:1ex\">";
    private static final String BLOCKQUOTE_END = "</blockquote>";
    // HTML tags used to quote replies & forwards
    /* package for testing */static final String QUOTE_BEGIN = "<div class=\"quote\">";
    private static final String QUOTE_END = "</div>";
    // Separates the attribution headers (Subject, To, etc) from the body in
    // quoted text.
    /* package for testing */  static final String HEADER_SEPARATOR = "<br type='attribution'>";
    private static final int HEADER_SEPARATOR_LENGTH = HEADER_SEPARATOR.length();

    // Integer extra holding one of the above compose action
    private static final String EXTRA_ACTION = "action";

    /**
     * Notifies the {@code Activity} that the caller is an Email
     * {@code Activity}, so that the back behavior may be modified accordingly.
     *
     * @see #onAppUpPressed
     */
    private static final String EXTRA_FROM_EMAIL_TASK = "fromemail";

    //  If this is a reply/forward then this extra will hold the original message uri
    private static final String EXTRA_IN_REFERENCE_TO_MESSAGE_URI = "in-reference-to-uri";
    private static final String END_TOKEN = ", ";

    private RecipientEditTextView mTo;
    private RecipientEditTextView mCc;
    private RecipientEditTextView mBcc;
    private Button mCcBccButton;
    private CcBccView mCcBccView;
    private AttachmentsView mAttachmentsView;
    private String mAccount;
    private Rfc822Validator mRecipientValidator;
    private Uri mRefMessageUri;
    private TextView mSubject;

    private ActionBar mActionBar;
    private ComposeModeAdapter mComposeModeAdapter;
    private int mComposeMode = -1;
    private boolean mForward;
    private String mRecipient;
    private boolean mAttachmentsChanged;
    private QuotedTextView mQuotedTextView;
    private TextView mBodyText;
    private View mFromStatic;
    private View mFromSpinner;
    private Spinner mFrom;
    private List<String[]> mReplyFromAccounts;
    private boolean mAccountSpinnerReady;
    private String[] mCurrentReplyFromAccount;
    private boolean mMessageIsForwardOrReply;
    private List<String> mAccounts;

    /**
     * Can be called from a non-UI thread.
     */
    public static void editDraft(Context context, String account, long mLocalMessageId) {
    }

    /**
     * Can be called from a non-UI thread.
     */
    public static void compose(Context launcher, String account) {
        launch(launcher, account, null, COMPOSE);
    }

    /**
     * Can be called from a non-UI thread.
     */
    public static void reply(Context launcher, String account, String uri) {
        launch(launcher, account, uri, REPLY);
    }

    /**
     * Can be called from a non-UI thread.
     */
    public static void replyAll(Context launcher, String account, String uri) {
        launch(launcher, account, uri, REPLY_ALL);
    }

    /**
     * Can be called from a non-UI thread.
     */
    public static void forward(Context launcher, String account, String uri) {
        launch(launcher, account, uri, FORWARD);
    }

    private static void launch(Context launcher, String account, String uri, int action) {
        Intent intent = new Intent(launcher, ComposeActivity.class);
        intent.putExtra(EXTRA_FROM_EMAIL_TASK, true);
        intent.putExtra(EXTRA_ACTION, action);
        intent.putExtra(Utils.EXTRA_ACCOUNT, account);
        intent.putExtra(EXTRA_IN_REFERENCE_TO_MESSAGE_URI, uri);
        launcher.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mAccount = intent.getStringExtra(Utils.EXTRA_ACCOUNT);
        setContentView(R.layout.compose);
        findViews();
        int action = intent.getIntExtra(EXTRA_ACTION, COMPOSE);
        if (action == REPLY || action == REPLY_ALL || action == FORWARD) {
            mRefMessageUri = Uri.parse(intent.getStringExtra(EXTRA_IN_REFERENCE_TO_MESSAGE_URI));
            initFromRefMessage(action, mAccount);
        } else {
            setQuotedTextVisibility(false);
        }
        initActionBar(action);
        asyncInitFromSpinner();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update the from spinner as other accounts
        // may now be available.
        asyncInitFromSpinner();
    }

    private void asyncInitFromSpinner() {
        Account[] result = AccountUtils.getSyncingAccounts(this, null, null, null);
        mAccounts = AccountUtils
                .mergeAccountLists(mAccounts, result, true /* prioritizeAccountList */);
        createReplyFromCache();
        initFromSpinner();
    }

    /**
     * Create a cache of all accounts a user could send mail from
     */
    private void createReplyFromCache() {
        // Check for replyFroms.
        List<String> accounts = null;
        mReplyFromAccounts = new ArrayList<String[]>();

        if (mMessageIsForwardOrReply) {
            accounts = Collections.singletonList(mAccount);
        } else {
            accounts = mAccounts;
        }
        for (String account : accounts) {
            // First add the account. First position is account, second
            // is display of account, 3rd position is the REAL account this
            // is being sent from / synced to.
            mReplyFromAccounts.add(new String[] {
                    account, account, account, "false"
            });
        }
    }

    private void initFromSpinner() {
        // If there are not yet any accounts in the cached synced accounts
        // because this is the first time Gmail was opened, and it was opened directly
        // to the compose activity,don't bother populating the reply from spinner yet.
        if (mReplyFromAccounts == null || mReplyFromAccounts.size() == 0) {
            mAccountSpinnerReady = false;
            return;
        }
        FromAddressSpinnerAdapter adapter = new FromAddressSpinnerAdapter(this);
        int currentAccountIndex = 0;
        String replyFromAccount = mAccount;

        boolean checkRealAccount = mRecipient == null || mAccount.equals(mRecipient);

        currentAccountIndex = addAccountsToAdapter(adapter, checkRealAccount, replyFromAccount);

        mFrom.setAdapter(adapter);
        mFrom.setSelection(currentAccountIndex, false);
        mFrom.setOnItemSelectedListener(this);
        mCurrentReplyFromAccount = mReplyFromAccounts.get(currentAccountIndex);

        hideOrShowFromSpinner();
        mAccountSpinnerReady = true;
        adapter.setSpinner(mFrom);
    }

    private void hideOrShowFromSpinner() {
        // Determine whether the from account spinner or the static
        // from text should be show
        // When the spinner is shown, the static from text
        // is hidden
        showFromSpinner(mFrom.getCount() > 1);
    }

    private int addAccountsToAdapter(FromAddressSpinnerAdapter adapter, boolean checkRealAccount,
            String replyFromAccount) {
        int currentIndex = 0;
        int currentAccountIndex = 0;
        // Get the position of the current account
        for (String[] account : mReplyFromAccounts) {
            // Add the account to the Adapter
            // The reason that we are not adding the Account array, but adding
            // the names of each account, is because Account returns a string
            // that we don't want to display on toString()
            adapter.add(account);
            // Compare to the account address, not the real account being
            // sent from.
            if (checkRealAccount) {
                // Need to check the real account and the account address
                // so that we can send from the correct address on the
                // correct account when the same address may exist across
                // multiple accounts.
                if (account[FromAddressSpinnerAdapter.REAL_ACCOUNT].equals(mAccount)
                        && account[FromAddressSpinnerAdapter.ACCOUNT_ADDRESS]
                                .equals(replyFromAccount)) {
                    currentAccountIndex = currentIndex;
                }
            } else {
                // Just need to check the account address.
                if (replyFromAccount.equals(
                        account[FromAddressSpinnerAdapter.ACCOUNT_ADDRESS])) {
                    currentAccountIndex = currentIndex;
                }
            }

            currentIndex++;
        }
        return currentAccountIndex;
    }

    private void findViews() {
        mCcBccButton = (Button) findViewById(R.id.add_cc);
        if (mCcBccButton != null) {
            mCcBccButton.setOnClickListener(this);
        }
        mCcBccView = (CcBccView) findViewById(R.id.cc_bcc_wrapper);
        mAttachmentsView = (AttachmentsView)findViewById(R.id.attachments);
        mTo = setupRecipients(R.id.to);
        mCc = setupRecipients(R.id.cc);
        mBcc = setupRecipients(R.id.bcc);
        mSubject = (TextView) findViewById(R.id.subject);
        mQuotedTextView = (QuotedTextView) findViewById(R.id.quoted_text_view);
        mQuotedTextView.setRespondInlineListener(this);
        mBodyText = (TextView) findViewById(R.id.body);
        mFromStatic = findViewById(R.id.static_from_content);
        mFromSpinner = findViewById(R.id.spinner_from_content);
        mFrom = (Spinner) findViewById(R.id.from_picker);
    }

    /**
     * Show the static from text view or the spinner
     * @param showSpinner Whether the spinner should be shown
     */
    private void showFromSpinner(boolean showSpinner) {
        // show/hide the static text
        mFromStatic.setVisibility(
                showSpinner ? View.GONE : View.VISIBLE);

        // show/hide the spinner
        mFromSpinner.setVisibility(
                showSpinner ? View.VISIBLE : View.GONE);
    }

    private void setQuotedTextVisibility(boolean show) {
        mQuotedTextView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void initActionBar(int action) {
        mComposeMode = action;
        mActionBar = getActionBar();
        if (action == ComposeActivity.COMPOSE) {
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            mActionBar.setTitle(R.string.compose);
        } else {
            mActionBar.setTitle(null);
            if (mComposeModeAdapter == null) {
                mComposeModeAdapter = new ComposeModeAdapter(this);
            }
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            mActionBar.setListNavigationCallbacks(mComposeModeAdapter, this);
            switch (action) {
                case ComposeActivity.REPLY:
                    mActionBar.setSelectedNavigationItem(0);
                    break;
                case ComposeActivity.REPLY_ALL:
                    mActionBar.setSelectedNavigationItem(1);
                    break;
                case ComposeActivity.FORWARD:
                    mActionBar.setSelectedNavigationItem(2);
                    break;
            }
        }
    }

    private void initFromRefMessage(int action, String recipientAddress) {
        ContentResolver resolver = getContentResolver();
        Cursor refMessage = resolver.query(mRefMessageUri, UIProvider.MESSAGE_PROJECTION, null,
                null, null);
        if (refMessage != null) {
            try {
                refMessage.moveToFirst();
                setSubject(refMessage, action);
                // Setup recipients
                if (action == FORWARD) {
                    mForward = true;
                }
                setQuotedTextVisibility(true);
                initRecipientsFromRefMessageCursor(recipientAddress, refMessage, action);
                initBodyFromRefMessage(refMessage, action);
                if (action == ComposeActivity.FORWARD || mAttachmentsChanged) {
                    updateAttachments(action, refMessage);
                } else {
                    // Clear the attachments.
                    removeAllAttachments();
                }
                updateHideOrShowCcBcc();
            } finally {
                refMessage.close();
            }
        }
    }

    private void initBodyFromRefMessage(Cursor refMessage, int action) {
        boolean forward = action == FORWARD;
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        Date date = new Date(refMessage.getLong(UIProvider.MESSAGE_DATE_RECEIVED_MS_COLUMN));
        StringBuffer quotedText = new StringBuffer();

        if (action == ComposeActivity.REPLY || action == ComposeActivity.REPLY_ALL) {
            quotedText.append(QUOTE_BEGIN);
            quotedText
                    .append(String.format(
                            getString(R.string.reply_attribution),
                            dateFormat.format(date),
                            Utils.cleanUpString(
                                    refMessage.getString(UIProvider.MESSAGE_FROM_COLUMN), true)));
            quotedText.append(HEADER_SEPARATOR);
            quotedText.append(BLOCKQUOTE_BEGIN);
            quotedText.append(refMessage.getString(UIProvider.MESSAGE_BODY_HTML));
            quotedText.append(BLOCKQUOTE_END);
            quotedText.append(QUOTE_END);
        } else if (action == ComposeActivity.FORWARD) {
            quotedText.append(QUOTE_BEGIN);
            quotedText
                    .append(String.format(getString(R.string.forward_attribution), Utils
                            .cleanUpString(refMessage.getString(UIProvider.MESSAGE_FROM_COLUMN),
                                    true /* remove empty quotes */), dateFormat.format(date), Utils
                            .cleanUpString(refMessage.getString(UIProvider.MESSAGE_SUBJECT_COLUMN),
                                    false /* don't remove empty quotes */), Utils.cleanUpString(
                            refMessage.getString(UIProvider.MESSAGE_TO_COLUMN), true)));
            String ccAddresses = refMessage.getString(UIProvider.MESSAGE_CC_COLUMN);
            quotedText.append(String.format(getString(R.string.cc_attribution),
                    Utils.cleanUpString(ccAddresses, true /* remove empty quotes */)));
        }
        quotedText.append(HEADER_SEPARATOR);
        quotedText.append(refMessage.getString(UIProvider.MESSAGE_BODY_HTML));
        quotedText.append(QUOTE_END);
        setQuotedText(quotedText.toString(), !forward);
    }

    /**
     * Fill the quoted text WebView. There is no point in having a "Show quoted
     * text" checkbox in a forwarded message so make sure mForward is
     * initialized properly before calling this method so we can hide it.
     */
    public void setQuotedText(CharSequence text, boolean allow) {
        // There is no way to retrieve this string from the WebView once it's
        // been loaded, so we need to store it here.
        mQuotedTextView.setQuotedText(text);
        mQuotedTextView.allowQuotedText(allow);
        // If there is quoted text, we always allow respond inline, since this
        // may be a forward.
        mQuotedTextView.allowRespondInline(true);
    }

    private void updateHideOrShowCcBcc() {
        // TODO
    }

    public void removeAllAttachments() {
        mAttachmentsView.removeAllViews();
    }

    private void updateAttachments(int action, Cursor refMessage) {
        // TODO: when we hook up attachments, make this work properly.
    }

    private void initRecipientsFromRefMessageCursor(String recipientAddress, Cursor refMessage,
            int action) {
        // Don't populate the address if this is a forward.
        if (action == ComposeActivity.FORWARD) {
            return;
        }
        initReplyRecipients(mAccount, refMessage, action);
    }

    private void initReplyRecipients(String account, Cursor refMessage, int action) {
        // This is the email address of the current user, i.e. the one composing
        // the reply.
        final String accountEmail = Utils.getEmailFromAddressString(account);
        String fromAddress = refMessage.getString(UIProvider.MESSAGE_FROM_COLUMN);
        String[] sentToAddresses = Utils.splitCommaSeparatedString(refMessage
                .getString(UIProvider.MESSAGE_TO_COLUMN));
        String[] replytoAddresses = Utils.splitCommaSeparatedString(refMessage
                .getString(UIProvider.MESSAGE_REPLY_TO_COLUMN));
        final Collection<String> toAddresses = initToRecipients(account, accountEmail, fromAddress,
                replytoAddresses, sentToAddresses);
        addToAddresses(toAddresses);

        // If this is a reply, the Cc list is empty. If this is a reply-all, the
        // Cc list is the union of the To and Cc recipients of the original
        // message, excluding the current user's email address and any addresses
        // already
        // on the To list.
        if (action == ComposeActivity.REPLY_ALL) {
            final Set<String> ccAddresses = Sets.newHashSet();
            addRecipients(accountEmail, ccAddresses, sentToAddresses);
            addRecipients(accountEmail, ccAddresses, Utils.splitCommaSeparatedString(refMessage
                    .getString(UIProvider.MESSAGE_CC_COLUMN)));
            addCcAddresses(ccAddresses, toAddresses);
        }
    }

    private void addToAddresses(Collection<String> addresses) {
        addAddressesToList(addresses, mTo);
    }

    private void addCcAddresses(Collection<String> addresses, Collection<String> toAddresses) {
        addCcAddressesToList(tokenizeAddressList(addresses), tokenizeAddressList(toAddresses),
                mCc);
    }

    private void addCcAddresses(Collection<String> addresses) {
        addAddressesToList(tokenizeAddressList(addresses), mCc);
    }

    @VisibleForTesting
    protected void addCcAddressesToList(List<Rfc822Token[]> addresses,
            List<Rfc822Token[]> compareToList, RecipientEditTextView list) {
        String address;

        HashSet<String> compareTo = convertToHashSet(compareToList);
        for (Rfc822Token[] tokens : addresses) {
            for (int i = 0; i < tokens.length; i++) {
                address = tokens[i].toString();
                // Check if this is a duplicate:
                if (!compareTo.contains(tokens[i].getAddress())) {
                    // Get the address here
                    list.append(address + END_TOKEN);
                }
            }
        }
    }

    private void addAddressesToList(List<Rfc822Token[]> addresses, RecipientEditTextView list) {
        String address;
        for (Rfc822Token[] tokens : addresses) {
            for (int i = 0; i < tokens.length; i++) {
                address = tokens[i].toString();
                list.append(address + END_TOKEN);
            }
        }
    }

    private HashSet<String> convertToHashSet(List<Rfc822Token[]> list) {
        HashSet<String> hash = new HashSet<String>();
        for (Rfc822Token[] tokens : list) {
            for (int i = 0; i < tokens.length; i++) {
                hash.add(tokens[i].getAddress());
            }
        }
        return hash;
    }

    private void addBccAddresses(Collection<String> addresses) {
        addAddressesToList(addresses, mBcc);
    }

    protected List<Rfc822Token[]> tokenizeAddressList(Collection<String> addresses) {
        @VisibleForTesting
        List<Rfc822Token[]> tokenized = new ArrayList<Rfc822Token[]>();

        for (String address: addresses) {
            tokenized.add(Rfc822Tokenizer.tokenize(address));
        }
        return tokenized;
    }

    @VisibleForTesting
    void addAddressesToList(Collection<String> addresses, RecipientEditTextView list) {
        for (String address : addresses) {
            addAddressToList(address, list);
        }
    }

    private void addAddressToList(String address, RecipientEditTextView list) {
        if (address == null || list == null)
            return;

        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(address);

        for (int i = 0; i < tokens.length; i++) {
            list.append(tokens[i] + END_TOKEN);
        }
    }

    @VisibleForTesting
    protected Collection<String> initToRecipients(String account, String accountEmail,
            String senderAddress, String[] replyToAddresses, String[] inToAddresses) {
        // The To recipient is the reply-to address specified in the original
        // message, unless it is:
        // the current user OR a custom from of the current user, in which case
        // it's the To recipient list of the original message.
        // OR missing, in which case use the sender of the original message
        Set<String> toAddresses = Sets.newHashSet();
        if (Utils.getEmailFromAddressString(senderAddress).equalsIgnoreCase(account)) {
            // The sender address is this account, so reply acts like reply all.
            toAddresses.addAll(Arrays.asList(inToAddresses));
        } else if (replyToAddresses != null && replyToAddresses.length != 0) {
            toAddresses.addAll(Arrays.asList(replyToAddresses));
        } else {
            // Check to see if the sender address is one of the user's custom
            // from addresses.
            if (senderAddress != null
                    && !accountEmail.equalsIgnoreCase(Utils
                            .getEmailFromAddressString(senderAddress))) {
                // Replying to the sender of the original message is the most
                // common case.
                toAddresses.add(senderAddress);
            } else {
                // This happens if the user replies to a message they originally
                // wrote. In this case, "reply" really means "re-send," so we
                // target the original recipients. This works as expected even
                // if the user sent the original message to themselves.
                toAddresses.addAll(Arrays.asList(inToAddresses));
            }
        }
        return toAddresses;
    }

    private static void addRecipients(String account, Set<String> recipients, String[] addresses) {
        for (String email : addresses) {
            // Do not add this account, or any of the custom froms, to the list
            // of recipients.
            final String recipientAddress = Utils.getEmailFromAddressString(email);
            if (!account.equalsIgnoreCase(recipientAddress)) {
                recipients.add(email.replace("\"\"", ""));
            }
        }
    }

    private void setSubject(Cursor refMessage, int action) {
        String subject = refMessage.getString(UIProvider.MESSAGE_SUBJECT_COLUMN);
        String prefix;
        String correctedSubject = null;
        if (action == ComposeActivity.COMPOSE) {
            prefix = "";
        } else if (action == ComposeActivity.FORWARD) {
            prefix = getString(R.string.forward_subject_label);
        } else {
            prefix = getString(R.string.reply_subject_label);
        }

        // Don't duplicate the prefix
        if (subject.toLowerCase().startsWith(prefix.toLowerCase())) {
            correctedSubject = subject;
        } else {
            correctedSubject = String
                    .format(getString(R.string.formatted_subject), prefix, subject);
        }
        mSubject.setText(correctedSubject);
    }

    private RecipientEditTextView setupRecipients(int id) {
        RecipientEditTextView view = (RecipientEditTextView) findViewById(id);
        view.setAdapter(new RecipientAdapter(this, mAccount));
        view.setTokenizer(new Rfc822Tokenizer());
        if (mRecipientValidator == null) {
            int offset = mAccount.indexOf("@") + 1;
            String account = mAccount;
            if (offset > -1) {
                account = account.substring(mAccount.indexOf("@") + 1);
            }
            mRecipientValidator = new Rfc822Validator(account);
        }
        view.setValidator(mRecipientValidator);
        return view;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.add_cc:
            case R.id.add_bcc:
                // Verify that cc/ bcc aren't showing.
                // Animate in cc/bcc.
                mCcBccView.show();
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.compose_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        boolean handled = false;
        switch (id) {
            case R.id.add_attachment:
                MockAttachment attachment = new MockAttachment();
                attachment.partId = "0";
                attachment.name = "testattachment.png";
                attachment.contentType = MimeType.inferMimeType(attachment.name, null);
                attachment.originExtras = "";
                mAttachmentsView.addAttachment(attachment);
                break;
            case R.id.add_cc:
            case R.id.add_bcc:
                mCcBccView.show();
                handled = true;
                break;
        }
        return !handled ? super.onOptionsItemSelected(item) : handled;
    }

    @Override
    public boolean onNavigationItemSelected(int position, long itemId) {
        if (position == ComposeActivity.REPLY) {
            mComposeMode = ComposeActivity.REPLY;
        } else if (position == ComposeActivity.REPLY_ALL) {
            mComposeMode = ComposeActivity.REPLY_ALL;
        } else if (position == ComposeActivity.FORWARD) {
            mComposeMode = ComposeActivity.FORWARD;
        }
        initFromRefMessage(mComposeMode, mAccount);
        return true;
    }

    private class ComposeModeAdapter extends ArrayAdapter<String> {

        private LayoutInflater mInflater;

        public ComposeModeAdapter(Context context) {
            super(context, R.layout.compose_mode_item, R.id.mode, getResources()
                    .getStringArray(R.array.compose_modes));
        }

        private LayoutInflater getInflater() {
            if (mInflater == null) {
                mInflater = LayoutInflater.from(getContext());
            }
            return mInflater;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getInflater().inflate(R.layout.compose_mode_display_item, null);
            }
            ((TextView) convertView.findViewById(R.id.mode)).setText(getItem(position));
            return super.getView(position, convertView, parent);
        }
    }

    @Override
    public void onRespondInline(String text) {
        appendToBody(text, false);
    }

    /**
     * Append text to the body of the message. If there is no existing body
     * text, just sets the body to text.
     *
     * @param text
     * @param withSignature True to append a signature.
     */
    public void appendToBody(CharSequence text, boolean withSignature) {
        Editable bodyText = mBodyText.getEditableText();
        if (bodyText != null && bodyText.length() > 0) {
            bodyText.append(text);
        } else {
            setBody(text, withSignature);
        }
    }

    /**
     * Set the body of the message.
     * @param text
     * @param withSignature True to append a signature.
     */
    public void setBody(CharSequence text, boolean withSignature) {
        mBodyText.setText(text);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // TODO
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing.
    }
}