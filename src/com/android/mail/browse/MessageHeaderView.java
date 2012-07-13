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

package com.android.mail.browse;

import android.content.AsyncQueryHandler;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.provider.ContactsContract;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mail.ContactInfoSource;
import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.SenderInfoLoader.ContactInfo;
import com.android.mail.browse.ConversationViewAdapter.MessageHeaderItem;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.perf.Timer;
import com.android.mail.providers.Account;
import com.android.mail.providers.Address;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

public class MessageHeaderView extends LinearLayout implements OnClickListener,
        OnMenuItemClickListener, HeaderBlock {

    /**
     * Cap very long recipient lists during summary construction for efficiency.
     */
    private static final int SUMMARY_MAX_RECIPIENTS = 50;

    private static final int MAX_SNIPPET_LENGTH = 100;

    private static final int SHOW_IMAGE_PROMPT_ONCE = 1;
    private static final int SHOW_IMAGE_PROMPT_ALWAYS = 2;

    private static final String HEADER_INFLATE_TAG = "message header inflate";
    private static final String HEADER_ADDVIEW_TAG = "message header addView";
    private static final String HEADER_RENDER_TAG = "message header render";
    private static final String PREMEASURE_TAG = "message header pre-measure";
    private static final String LAYOUT_TAG = "message header layout";
    private static final String MEASURE_TAG = "message header measure";

    private static final String RECIPIENT_HEADING_DELIMITER = "   ";

    private static final String LOG_TAG = LogTag.getLogTag();

    private MessageHeaderViewCallbacks mCallbacks;

    private ViewGroup mUpperHeaderView;
    private TextView mSenderNameView;
    private TextView mSenderEmailView;
    private QuickContactBadge mPhotoView;
    private ImageView mStarView;
    private ViewGroup mTitleContainerView;
    private ViewGroup mCollapsedDetailsView;
    private ViewGroup mExpandedDetailsView;
    private SpamWarningView mSpamWarningView;
    private ViewGroup mImagePromptView;
    private MessageInviteView mInviteView;
    private View mBottomBorderView;
    private ImageView mPresenceView;
    private View mPhotoSpacerView;
    private View mForwardButton;
    private View mOverflowButton;
    private View mDraftIcon;
    private View mEditDraftButton;
    private TextView mUpperDateView;
    private View mReplyButton;
    private View mReplyAllButton;
    private View mAttachmentIcon;

    // temporary fields to reference raw data between initial render and details
    // expansion
    private String[] mFrom;
    private String[] mTo;
    private String[] mCc;
    private String[] mBcc;
    private String[] mReplyTo;
    private long mTimestampMs;
    private FormattedDateBuilder mDateBuilder;

    private boolean mIsDraft = false;

    private boolean mIsSending;

    /**
     * The snappy header has special visibility rules (i.e. no details header,
     * even though it has an expanded appearance)
     */
    private boolean mIsSnappy;

    private String mSnippet;

    private Address mSender;

    private ContactInfoSource mContactInfoSource;

    private boolean mPreMeasuring;

    private Account mAccount;

    private Map<String, Address> mAddressCache;

    private boolean mShowImagePrompt;

    private boolean mDefaultReplyAll;

    private int mDrawTranslateY;

    private CharSequence mTimestampShort;

    /**
     * Take the initial visibility of the star view to mean its collapsed
     * visibility. Star is always visible when expanded, but sometimes, like on
     * phones, there isn't enough room to warrant showing star when collapsed.
     */
    private int mCollapsedStarVis;

    /**
     * Take the initial right margin of the header title container to mean its
     * right margin when collapsed. There's currently no need for additional
     * margin when expanded, but if that need ever arises, title_container can
     * simply tack on some extra right padding.
     */
    private int mTitleContainerCollapsedMarginRight;

    private PopupMenu mPopup;

    private MessageHeaderItem mMessageHeaderItem;
    private Message mMessage;

    private boolean mCollapsedDetailsValid;
    private boolean mExpandedDetailsValid;

    private final LayoutInflater mInflater;

    private AsyncQueryHandler mQueryHandler;

    public interface MessageHeaderViewCallbacks {
        void setMessageSpacerHeight(MessageHeaderItem item, int newSpacerHeight);

        void setMessageExpanded(MessageHeaderItem item, int newSpacerHeight);

        void showExternalResources(Message msg);
    }

    public MessageHeaderView(Context context) {
        this(context, null);
    }

    public MessageHeaderView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public MessageHeaderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mInflater = LayoutInflater.from(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mUpperHeaderView = (ViewGroup) findViewById(R.id.upper_header);
        mSenderNameView = (TextView) findViewById(R.id.sender_name);
        mSenderEmailView = (TextView) findViewById(R.id.sender_email);
        mPhotoView = (QuickContactBadge) findViewById(R.id.photo);
        mPhotoSpacerView = findViewById(R.id.photo_spacer);
        mReplyButton = findViewById(R.id.reply);
        mReplyAllButton = findViewById(R.id.reply_all);
        mForwardButton = findViewById(R.id.forward);
        mStarView = (ImageView) findViewById(R.id.star);
        mPresenceView = (ImageView) findViewById(R.id.presence);
        mTitleContainerView = (ViewGroup) findViewById(R.id.title_container);
        mOverflowButton = findViewById(R.id.overflow);
        mDraftIcon = findViewById(R.id.draft);
        mEditDraftButton = findViewById(R.id.edit_draft);
        mUpperDateView = (TextView) findViewById(R.id.upper_date);
        mAttachmentIcon = findViewById(R.id.attachment);

        mCollapsedStarVis = mStarView.getVisibility();
        mTitleContainerCollapsedMarginRight = ((MarginLayoutParams) mTitleContainerView
                .getLayoutParams()).rightMargin;

        mBottomBorderView = findViewById(R.id.details_bottom_border);

        setExpanded(true);

        registerMessageClickTargets(R.id.reply, R.id.reply_all, R.id.forward, R.id.star,
                R.id.edit_draft, R.id.overflow, R.id.upper_header);
    }

    private void registerMessageClickTargets(int... ids) {
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) {
                v.setOnClickListener(this);
            }
        }
    }

    /**
     * Associate the header with a contact info source for later contact
     * presence/photo lookup.
     */
    public void setContactInfoSource(ContactInfoSource contactInfoSource) {
        mContactInfoSource = contactInfoSource;
    }

    public void setCallbacks(MessageHeaderViewCallbacks callbacks) {
        mCallbacks = callbacks;
    }

    /**
     * Find the header view corresponding to a message with given local ID.
     *
     * @param parent the view parent to search within
     * @param localMessageId local message ID
     * @return a header view or null
     */
    public static MessageHeaderView find(ViewGroup parent, long localMessageId) {
        return (MessageHeaderView) parent.findViewWithTag(localMessageId);
    }

    public boolean isExpanded() {
        // (let's just arbitrarily say that unbound views are expanded by default)
        return mMessageHeaderItem == null || mMessageHeaderItem.isExpanded();
    }

    @Override
    public boolean canSnap() {
        return isExpanded();
    }

    @Override
    public MessageHeaderView getSnapView() {
        return this;
    }

    public void setSnappy(boolean snappy) {
        mIsSnappy = snappy;
        hideMessageDetails();
        if (snappy) {
            setBackgroundDrawable(null);
        } else {
            setBackgroundColor(android.R.color.white);
        }
    }

    /**
     * Check if this header's displayed data matches that of another header.
     *
     * @param other another header
     * @return true if the headers are displaying data for the same message
     */
    public boolean matches(MessageHeaderView other) {
        return other != null && mMessage != null && mMessage.equals(other.mMessage);
    }

    /**
     * Headers that are unbound will not match any rendered header (matches()
     * will return false). Unbinding is not guaranteed to *hide* the view's old
     * data, though. To re-bind this header to message data, call render() or
     * renderUpperHeaderFrom().
     */
    public void unbind() {
        mMessageHeaderItem = null;
        mMessage = null;
    }

    public void renderUpperHeaderFrom(MessageHeaderView other) {
        mMessageHeaderItem = other.mMessageHeaderItem;
        mMessage = other.mMessage;
        mSender = other.mSender;
        mDefaultReplyAll = other.mDefaultReplyAll;

        mSenderNameView.setText(other.mSenderNameView.getText());
        mSenderEmailView.setText(other.mSenderEmailView.getText());
        mStarView.setSelected(other.mStarView.isSelected());
        mStarView.setContentDescription(getResources().getString(
                mStarView.isSelected() ? R.string.remove_star : R.string.add_star));

        updateContactInfo();

        mIsDraft = other.mIsDraft;
        updateChildVisibility();
    }

    public void initialize(FormattedDateBuilder dateBuilder, Account account,
            Map<String, Address> addressCache) {
        mDateBuilder = dateBuilder;
        mAccount = account;
        mAddressCache = addressCache;
    }

    public void bind(MessageHeaderItem headerItem, boolean defaultReplyAll) {
        Timer t = new Timer();
        t.start(HEADER_RENDER_TAG);

        mCollapsedDetailsValid = false;
        mExpandedDetailsValid = false;

        mMessageHeaderItem = headerItem;
        mMessage = headerItem.message;
        mShowImagePrompt = mMessage.shouldShowImagePrompt();
        mDefaultReplyAll = defaultReplyAll;
        setExpanded(headerItem.isExpanded());

        mTimestampMs = mMessage.dateReceivedMs;
        mTimestampShort = headerItem.timestampShort;
        if (mTimestampShort == null) {
            mTimestampShort = mDateBuilder.formatShortDate(mTimestampMs);
            headerItem.timestampShort = mTimestampShort;
        }

        mFrom = mMessage.getFromAddresses();
        mTo = mMessage.getToAddresses();
        mCc = mMessage.getCcAddresses();
        mBcc = mMessage.getBccAddresses();
        mReplyTo = mMessage.getReplyToAddresses();

        /**
         * Turns draft mode on or off. Draft mode hides message operations other
         * than "edit", hides contact photo, hides presence, and changes the
         * sender name to "Draft".
         */
        mIsDraft = mMessage.draftType != UIProvider.DraftType.NOT_A_DRAFT;
        mIsSending = isInOutbox();

        // If this was a sent message AND:
        // 1. the account has a custom from, the cursor will populate the
        // selected custom from as the fromAddress when a message is sent but
        // not yet synced.
        // 2. the account has no custom froms, fromAddress will be empty, and we
        // can safely fall back and show the account name as sender since it's
        // the only possible fromAddress.
        String from = mMessage.from;
        if (TextUtils.isEmpty(from)) {
            from = mAccount.name;
        }
        mSender = getAddress(from);

        updateChildVisibility();

        if (mIsDraft || isInOutbox()) {
            mSnippet = makeSnippet(mMessage.snippet);
        } else {
            mSnippet = mMessage.snippet;
        }

        mSenderNameView.setText(getHeaderTitle());
        mSenderEmailView.setText(getHeaderSubtitle());

        if (mUpperDateView != null) {
            mUpperDateView.setText(mTimestampShort);
        }

        mStarView.setSelected(mMessage.starred);
        mStarView.setContentDescription(getResources().getString(
                mStarView.isSelected() ? R.string.remove_star : R.string.add_star));

        updateContactInfo();

        t.pause(HEADER_RENDER_TAG);
    }

    private Address getAddress(String emailStr) {
        return getAddress(mAddressCache, emailStr);
    }

    private static Address getAddress(Map<String, Address> cache, String emailStr) {
        Address addr = null;
        if (cache != null) {
            addr = cache.get(emailStr);
        }
        if (addr == null) {
            addr = Address.getEmailAddress(emailStr);
            if (cache != null) {
                cache.put(emailStr, addr);
            }
        }
        return addr;
    }

    private boolean isInOutbox() {
        // TODO: what should this read? Folder info?
        return false;
    }

    private void updateSpacerHeight() {
        final int h = measureHeight();

        mMessageHeaderItem.setHeight(h);
        if (mCallbacks != null) {
            mCallbacks.setMessageSpacerHeight(mMessageHeaderItem, h);
        }
    }

    private int measureHeight() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) {
            LogUtils.e(LOG_TAG, new Error(), "Unable to measure height of detached header");
            return getHeight();
        }
        mPreMeasuring = true;
        final int h = Utils.measureViewHeight(this, parent);
        mPreMeasuring = false;
        return h;
    }

    private CharSequence getHeaderTitle() {
        CharSequence title;

        if (mIsDraft) {
            title = getResources().getQuantityText(R.plurals.draft, 1);
        } else if (mIsSending) {
            title = getResources().getString(R.string.sending);
        } else {
            title = getSenderName(mSender);
        }

        return title;
    }

    private CharSequence getHeaderSubtitle() {
        CharSequence sub;
        if (mIsSending) {
            sub = null;
        } else {
            if (isExpanded()) {
                if (mMessage.viaDomain != null) {
                    sub = getResources().getString(
                            R.string.via_domain, mMessage.viaDomain);
                } else {
                    sub = getSenderAddress(mSender);
                }
            } else {
                sub = mSnippet;
            }
        }
        return sub;
    }

    /**
     * Return the name, if known, or just the address.
     */
    private static CharSequence getSenderName(Address sender) {
        final String displayName = sender.getName();
        return TextUtils.isEmpty(displayName) ? sender.getAddress() : displayName;
    }

    /**
     * Return the address, if a name is present, or null if not.
     */
    private static CharSequence getSenderAddress(Address sender) {
        String displayName = sender == null ? "" : sender.getName();
        return TextUtils.isEmpty(displayName) ? null : sender.getAddress();
    }

    private void setChildVisibility(int visibility, View... children) {
        for (View v : children) {
            if (v != null) {
                v.setVisibility(visibility);
            }
        }
    }

    private void setExpanded(final boolean expanded) {
        // use View's 'activated' flag to store expanded state
        // child view state lists can use this to toggle drawables
        setActivated(expanded);
        if (mMessageHeaderItem != null) {
            mMessageHeaderItem.setExpanded(expanded);
        }
    }

    /**
     * Update the visibility of the many child views based on expanded/collapsed
     * and draft/normal state.
     */
    private void updateChildVisibility() {
        // Too bad this can't be done with an XML state list...

        if (isExpanded()) {
            int normalVis, draftVis;

            setMessageDetailsVisibility((mIsSnappy) ? GONE : VISIBLE);

            if (mIsDraft) {
                normalVis = GONE;
                draftVis = VISIBLE;
            } else {
                normalVis = VISIBLE;
                draftVis = GONE;
            }

            setReplyOrReplyAllVisible();
            setChildVisibility(normalVis, mPhotoView, mPhotoSpacerView, mForwardButton,
                    mSenderEmailView, mOverflowButton);
            setChildVisibility(draftVis, mDraftIcon, mEditDraftButton);
            setChildVisibility(GONE, mAttachmentIcon, mUpperDateView);
            setChildVisibility(VISIBLE, mStarView);

            setChildMarginRight(mTitleContainerView, 0);

        } else {

            setMessageDetailsVisibility(GONE);
            setChildVisibility(VISIBLE, mSenderEmailView, mUpperDateView);

            setChildVisibility(GONE, mEditDraftButton, mReplyButton, mReplyAllButton,
                    mForwardButton);
            setChildVisibility(GONE, mOverflowButton);

            setChildVisibility(mMessage.hasAttachments ? VISIBLE : GONE,
                    mAttachmentIcon);

            setChildVisibility(mCollapsedStarVis, mStarView);

            setChildMarginRight(mTitleContainerView, mTitleContainerCollapsedMarginRight);

            if (mIsDraft) {

                setChildVisibility(VISIBLE, mDraftIcon);
                setChildVisibility(GONE, mPhotoView, mPhotoSpacerView);

            } else {

                setChildVisibility(GONE, mDraftIcon);
                setChildVisibility(VISIBLE, mPhotoView, mPhotoSpacerView);

            }
        }

    }

    /**
     * If an overflow menu is present in this header's layout, set the
     * visibility of "Reply" and "Reply All" actions based on a user preference.
     * Only one of those actions will be visible when an overflow is present. If
     * no overflow is present (e.g. big phone or tablet), it's assumed we have
     * plenty of screen real estate and can show both.
     */
    private void setReplyOrReplyAllVisible() {
        if (mIsDraft) {
            setChildVisibility(GONE, mReplyButton, mReplyAllButton);
            return;
        } else if (mOverflowButton == null) {
            setChildVisibility(VISIBLE, mReplyButton, mReplyAllButton);
            return;
        }

        setChildVisibility(mDefaultReplyAll ? GONE : VISIBLE, mReplyButton);
        setChildVisibility(mDefaultReplyAll ? VISIBLE : GONE, mReplyAllButton);
    }

    private static void setChildMarginRight(View childView, int marginRight) {
        MarginLayoutParams mlp = (MarginLayoutParams) childView.getLayoutParams();
        mlp.rightMargin = marginRight;
        childView.setLayoutParams(mlp);
    }

    private void renderEmailList(int rowRes, int valueRes, String[] emails, boolean showViaDomain) {
        if (emails == null || emails.length == 0) {
            return;
        }
        String[] formattedEmails = new String[emails.length];
        for (int i = 0; i < emails.length; i++) {
            Address e = getAddress(emails[i]);
            String name = e.getName();
            String addr = e.getAddress();
            if (name == null || name.length() == 0) {
                formattedEmails[i] = addr;
            } else {
                // The one downside to having the showViaDomain here is that
                // if the sender does not have a name, it will not show the via info
                if (showViaDomain) {
                    formattedEmails[i] = getResources().getString(
                            R.string.address_display_format_with_via_domain,
                            name, addr, mMessage.viaDomain);
                } else {
                    formattedEmails[i] = getResources().getString(R.string.address_display_format,
                            name, addr);
                }
            }
        }
        ((TextView) findViewById(valueRes)).setText(TextUtils.join("\n", formattedEmails));
        findViewById(rowRes).setVisibility(VISIBLE);
    }

    @Override
    public void setMarginBottom(int bottomMargin) {
        MarginLayoutParams p = (MarginLayoutParams) getLayoutParams();
        if (p.bottomMargin != bottomMargin) {
            p.bottomMargin = bottomMargin;
            setLayoutParams(p);
        }
    }

    public void setMarginTop(int topMargin) {
        MarginLayoutParams p = (MarginLayoutParams) getLayoutParams();
        if (p.topMargin != topMargin) {
            p.topMargin = topMargin;
            setLayoutParams(p);
        }
    }

    public void setTranslateY(int offsetY) {
        if (mDrawTranslateY != offsetY) {
            mDrawTranslateY = offsetY;
            invalidate();
        }
    }

    /**
     * Utility class to build a list of recipient lists.
     */
    private static class RecipientListsBuilder {
        private final Context mContext;
        private final String mMe;
        private final SpannableStringBuilder mBuilder = new SpannableStringBuilder();
        private final CharSequence mComma;
        private final Map<String, Address> mAddressCache;

        int mRecipientCount = 0;
        boolean mFirst = true;

        public RecipientListsBuilder(Context context, String me,
                Map<String, Address> addressCache) {
            mContext = context;
            mMe = me;
            mComma = mContext.getText(R.string.enumeration_comma);
            mAddressCache = addressCache;
        }

        public void append(String[] recipients, int headingRes) {
            int addLimit = SUMMARY_MAX_RECIPIENTS - mRecipientCount;
            CharSequence recipientList = getSummaryTextForHeading(headingRes, recipients, addLimit);
            if (recipientList != null) {
                // duplicate TextUtils.join() logic to minimize temporary
                // allocations, and because we need to support spans
                if (mFirst) {
                    mFirst = false;
                } else {
                    mBuilder.append(RECIPIENT_HEADING_DELIMITER);
                }
                mBuilder.append(recipientList);
                mRecipientCount += Math.min(addLimit, recipients.length);
            }
        }

        private CharSequence getSummaryTextForHeading(int headingStrRes, String[] rawAddrs,
                int maxToCopy) {
            if (rawAddrs == null || rawAddrs.length == 0 || maxToCopy == 0) {
                return null;
            }

            SpannableStringBuilder ssb = new SpannableStringBuilder(
                    mContext.getString(headingStrRes));
            ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, ssb.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(' ');

            final int len = Math.min(maxToCopy, rawAddrs.length);
            boolean first = true;
            for (int i = 0; i < len; i++) {
                Address email = getAddress(mAddressCache, rawAddrs[i]);
                String name = (mMe.equals(email.getAddress())) ? mContext.getString(R.string.me)
                        : email.getSimplifiedName();

                // duplicate TextUtils.join() logic to minimize temporary
                // allocations, and because we need to support spans
                if (first) {
                    first = false;
                } else {
                    ssb.append(mComma);
                }
                ssb.append(name);
            }

            return ssb;
        }

        public CharSequence build() {
            return mBuilder;
        }
    }

    @VisibleForTesting
    static CharSequence getRecipientSummaryText(Context context, String me, String[] to,
            String[] cc, String[] bcc, Map<String, Address> addressCache) {

        RecipientListsBuilder builder = new RecipientListsBuilder(context, me, addressCache);

        builder.append(to, R.string.to_heading);
        builder.append(cc, R.string.cc_heading);
        builder.append(bcc, R.string.bcc_heading);

        return builder.build();
    }

    @Override
    public void updateContactInfo() {

        mPresenceView.setImageDrawable(null);
        mPresenceView.setVisibility(GONE);
        if (mContactInfoSource == null || mSender == null) {
            mPhotoView.setImageToDefault();
            mPhotoView.setContentDescription(getResources().getString(
                    R.string.contact_info_string_default));
            return;
        }

        // Set the photo to either a found Bitmap or the default
        // and ensure either the contact URI or email is set so the click
        // handling works
        String contentDesc = getResources().getString(R.string.contact_info_string,
                !TextUtils.isEmpty(mSender.getName()) ? mSender.getName() : mSender.getAddress());
        mPhotoView.setContentDescription(contentDesc);
        boolean photoSet = false;
        String email = mSender.getAddress();
        ContactInfo info = mContactInfoSource.getContactInfo(email);
        if (info != null) {
            mPhotoView.assignContactUri(info.contactUri);
            if (info.photo != null) {
                mPhotoView.setImageBitmap(info.photo);
                contentDesc = String.format(contentDesc, mSender.getName());
                photoSet = true;
            }
            if (!mIsDraft && info.status != null) {
                mPresenceView.setImageResource(ContactsContract.StatusUpdates
                        .getPresenceIconResourceId(info.status));
                mPresenceView.setVisibility(VISIBLE);
            }
        } else {
            mPhotoView.assignContactFromEmail(email, true /* lazyLookup */);
        }

        if (!photoSet) {
            mPhotoView.setImageToDefault();
        }
    }


    @Override
    public boolean onMenuItemClick(MenuItem item) {
        mPopup.dismiss();
        return onClick(null, item.getItemId());
    }

    @Override
    public void onClick(View v) {
        onClick(v, v.getId());
    }

    /**
     * Handles clicks on either views or menu items. View parameter can be null
     * for menu item clicks.
     */
    public boolean onClick(View v, int id) {
        boolean handled = true;

        switch (id) {
            case R.id.reply:
                ComposeActivity.reply(getContext(), mAccount, mMessage);
                break;
            case R.id.reply_all:
                ComposeActivity.replyAll(getContext(), mAccount, mMessage);
                break;
            case R.id.forward:
                ComposeActivity.forward(getContext(), mAccount, mMessage);
                break;
            case R.id.star: {
                final boolean newValue = !v.isSelected();
                v.setSelected(newValue);
                mMessage.star(newValue, getQueryHandler(), 0 /* token */, null /* cookie */);
                // TODO: propagate the change to the entry in conversation list
                break;
            }
            case R.id.edit_draft:
                ComposeActivity.editDraft(getContext(), mAccount, mMessage);
                break;
            case R.id.overflow: {
                if (mPopup == null) {
                    mPopup = new PopupMenu(getContext(), v);
                    mPopup.getMenuInflater().inflate(R.menu.message_header_overflow_menu,
                            mPopup.getMenu());
                    mPopup.setOnMenuItemClickListener(this);
                }
                mPopup.getMenu().findItem(R.id.reply).setVisible(mDefaultReplyAll);
                mPopup.getMenu().findItem(R.id.reply_all).setVisible(!mDefaultReplyAll);

                mPopup.show();
                break;
            }
            case R.id.details_collapsed_content:
            case R.id.details_expanded_content:
                toggleMessageDetails(v);
                break;
            case R.id.upper_header:
                toggleExpanded();
                break;
            case R.id.show_pictures:
                handleShowImagePromptClick(v);
                break;
            default:
                LogUtils.i(LOG_TAG, "unrecognized header tap: %d", id);
                handled = false;
                break;
        }
        return handled;
    }

    public void toggleExpanded() {
        if (mIsSnappy) {
            // In addition to making the snappy header disappear, this will
            // propagate the change to the normal header. It should only be
            // possible to collapse an expanded snappy header; collapsed snappy
            // headers should never exist.

            // TODO: make this work right. the scroll position jumps and the
            // snappy header doesn't re-appear bound to a subsequent message.
            // mCallbacks.setMessageExpanded(mLocalMessageId, mServerMessageId,
            // false);
            // setVisibility(GONE);
            // unbind();
            return;
        }

        setExpanded(!isExpanded());

        mSenderNameView.setText(getHeaderTitle());
        mSenderEmailView.setText(getHeaderSubtitle());

        updateChildVisibility();

        // Force-measure the new header height so we can set the spacer size and
        // reveal the message
        // div in one pass. Force-measuring makes it unnecessary to set
        // mSizeChanged.
        int h = measureHeight();
        mMessageHeaderItem.setHeight(h);
        if (mCallbacks != null) {
            mCallbacks.setMessageExpanded(mMessageHeaderItem, h);
        }
    }

    private void toggleMessageDetails(View visibleDetailsView) {
        final boolean detailsExpanded = (visibleDetailsView == mCollapsedDetailsView);
        setMessageDetailsExpanded(detailsExpanded);
        updateSpacerHeight();
    }

    private void setMessageDetailsExpanded(boolean expand) {
        if (expand) {
            showExpandedDetails();
            hideCollapsedDetails();
        } else {
            hideExpandedDetails();
            showCollapsedDetails();
        }
        if (mMessageHeaderItem != null) {
            mMessageHeaderItem.detailsExpanded = expand;
        }
    }

    public void setMessageDetailsVisibility(int vis) {
        if (vis == GONE) {
            hideCollapsedDetails();
            hideExpandedDetails();
            hideSpamWarning();
            hideShowImagePrompt();
            hideInvite();
        } else {
            setMessageDetailsExpanded(mMessageHeaderItem.detailsExpanded);
            if (mMessage.spamWarningString == null) {
                hideSpamWarning();
            } else {
                showSpamWarning();
            }
            if (mShowImagePrompt) {
                showImagePrompt();
            } else {
                hideShowImagePrompt();
            }
            if (mMessage.isFlaggedCalendarInvite()) {
                showInvite();
            } else {
                hideInvite();
            }
        }
        if (mBottomBorderView != null) {
            mBottomBorderView.setVisibility(vis);
        }
    }

    public void hideMessageDetails() {
        setMessageDetailsVisibility(GONE);
    }

    @Override
    public void setStarDisplay(boolean starred) {
        if (mStarView.isSelected() != starred) {
            mStarView.setSelected(starred);
        }
    }

    private void hideCollapsedDetails() {
        if (mCollapsedDetailsView != null) {
            mCollapsedDetailsView.setVisibility(GONE);
        }
    }

    private void hideExpandedDetails() {
        if (mExpandedDetailsView != null) {
            mExpandedDetailsView.setVisibility(GONE);
        }
    }

    private void hideInvite() {
        if (mInviteView != null) {
            mInviteView.setVisibility(GONE);
        }
    }

    private void showInvite() {
        if (mInviteView == null) {
            mInviteView = (MessageInviteView) mInflater.inflate(
                    R.layout.conversation_message_invite, this, false);
            addView(mInviteView);
        }
        mInviteView.bind(mMessage);
        mInviteView.setVisibility(VISIBLE);
    }

    private void hideShowImagePrompt() {
        if (mImagePromptView != null) {
            mImagePromptView.setVisibility(GONE);
        }
    }

    private void showImagePrompt() {
        if (mImagePromptView == null) {
            ViewGroup v = (ViewGroup) mInflater.inflate(R.layout.conversation_message_show_pics,
                    this, false);
            addView(v);
            v.setOnClickListener(this);
            v.setTag(SHOW_IMAGE_PROMPT_ONCE);

            mImagePromptView = v;
        }
        mImagePromptView.setVisibility(VISIBLE);
    }

    private void hideSpamWarning() {
        if (mSpamWarningView != null) {
            mSpamWarningView.setVisibility(GONE);
        }
    }

    private void showSpamWarning() {
        if (mSpamWarningView == null) {
            mSpamWarningView = (SpamWarningView)
                    mInflater.inflate(R.layout.conversation_message_spam_warning, this, false);
            addView(mSpamWarningView);
        }

        mSpamWarningView.showSpamWarning(mMessage, mSender);
    }

    private void handleShowImagePromptClick(View v) {
        Integer state = (Integer) v.getTag();
        if (state == null) {
            return;
        }
        switch (state) {
            case SHOW_IMAGE_PROMPT_ONCE:
                if (mCallbacks != null) {
                    mCallbacks.showExternalResources(mMessage);
                }
                ImageView descriptionViewIcon = (ImageView) v.findViewById(R.id.show_pictures_icon);
                descriptionViewIcon.setContentDescription(getResources().getString(
                        R.string.always_show_images));
                TextView descriptionView = (TextView) v.findViewById(R.id.show_pictures_text);
                descriptionView.setText(R.string.always_show_images);
                v.setTag(SHOW_IMAGE_PROMPT_ALWAYS);
                // the new text's line count may differ, so update the spacer height
                updateSpacerHeight();
                break;
            case SHOW_IMAGE_PROMPT_ALWAYS:
                mMessage.markAlwaysShowImages(getQueryHandler(), 0 /* token */, null /* cookie */);

                mShowImagePrompt = false;
                v.setTag(null);
                v.setVisibility(GONE);
                updateSpacerHeight();
                Toast.makeText(getContext(), R.string.always_show_images_toast, Toast.LENGTH_SHORT)
                        .show();
                break;
        }
    }

    private AsyncQueryHandler getQueryHandler() {
        if (mQueryHandler == null) {
            mQueryHandler = new AsyncQueryHandler(getContext().getContentResolver()) {};
        }
        return mQueryHandler;
    }

    /**
     * Makes collapsed details visible. If necessary, will inflate details
     * layout and render using saved-off state (senders, timestamp, etc).
     */
    private void showCollapsedDetails() {
        if (mCollapsedDetailsView == null) {
            mCollapsedDetailsView = (ViewGroup) mInflater.inflate(
                    R.layout.conversation_message_details_header, this, false);
            addView(mCollapsedDetailsView, indexOfChild(mUpperHeaderView) + 1);
            mCollapsedDetailsView.setOnClickListener(this);
        }
        if (!mCollapsedDetailsValid) {
            if (mMessageHeaderItem.recipientSummaryText == null) {
                mMessageHeaderItem.recipientSummaryText = getRecipientSummaryText(getContext(),
                        mAccount.name, mTo, mCc, mBcc, mAddressCache);
            }
            ((TextView) findViewById(R.id.recipients_summary))
                    .setText(mMessageHeaderItem.recipientSummaryText);

            ((TextView) findViewById(R.id.date_summary)).setText(mTimestampShort);

            mCollapsedDetailsValid = true;
        }
        mCollapsedDetailsView.setVisibility(VISIBLE);
    }

    /**
     * Makes expanded details visible. If necessary, will inflate expanded
     * details layout and render using saved-off state (senders, timestamp,
     * etc).
     */
    private void showExpandedDetails() {
        // lazily create expanded details view
        if (mExpandedDetailsView == null) {
            View v = mInflater.inflate(R.layout.conversation_message_details_header_expanded,
                    this, false);
            addView(v, indexOfChild(mUpperHeaderView) + 1);
            v.setOnClickListener(this);

            mExpandedDetailsView = (ViewGroup) v;
        }
        if (!mExpandedDetailsValid) {
            if (mMessageHeaderItem.timestampLong == null) {
                mMessageHeaderItem.timestampLong = mDateBuilder.formatLongDateTime(mTimestampMs);
            }
            ((TextView) findViewById(R.id.date_value)).setText(mMessageHeaderItem.timestampLong);
            renderEmailList(R.id.replyto_row, R.id.replyto_value, mReplyTo, false);
            if (mMessage.viaDomain != null) {
                renderEmailList(R.id.from_row, R.id.from_value, mFrom, true);
            }
            renderEmailList(R.id.to_row, R.id.to_value, mTo, false);
            renderEmailList(R.id.cc_row, R.id.cc_value, mCc, false);
            renderEmailList(R.id.bcc_row, R.id.bcc_value, mBcc, false);

            mExpandedDetailsValid = true;
        }
        mExpandedDetailsView.setVisibility(VISIBLE);
    }

    /**
     * Returns a short plaintext snippet generated from the given HTML message
     * body. Collapses whitespace, ignores '&lt;' and '&gt;' characters and
     * everything in between, and truncates the snippet to no more than 100
     * characters.
     *
     * @return Short plaintext snippet
     */
    @VisibleForTesting
    static String makeSnippet(final String messageBody) {
        StringBuilder snippet = new StringBuilder(MAX_SNIPPET_LENGTH);

        StringReader reader = new StringReader(messageBody);
        try {
            int c;
            while ((c = reader.read()) != -1 && snippet.length() < MAX_SNIPPET_LENGTH) {
                // Collapse whitespace.
                if (Character.isWhitespace(c)) {
                    snippet.append(' ');
                    do {
                        c = reader.read();
                    } while (Character.isWhitespace(c));
                    if (c == -1) {
                        break;
                    }
                }

                if (c == '<') {
                    // Ignore everything up to and including the next '>'
                    // character.
                    while ((c = reader.read()) != -1) {
                        if (c == '>') {
                            break;
                        }
                    }

                    // If we reached the end of the message body, exit.
                    if (c == -1) {
                        break;
                    }
                } else if (c == '&') {
                    // Read HTML entity.
                    StringBuilder sb = new StringBuilder();

                    while ((c = reader.read()) != -1) {
                        if (c == ';') {
                            break;
                        }
                        sb.append((char) c);
                    }

                    String entity = sb.toString();
                    if ("nbsp".equals(entity)) {
                        snippet.append(' ');
                    } else if ("lt".equals(entity)) {
                        snippet.append('<');
                    } else if ("gt".equals(entity)) {
                        snippet.append('>');
                    } else if ("amp".equals(entity)) {
                        snippet.append('&');
                    } else if ("quot".equals(entity)) {
                        snippet.append('"');
                    } else if ("apos".equals(entity) || "#39".equals(entity)) {
                        snippet.append('\'');
                    } else {
                        // Unknown entity; just append the literal string.
                        snippet.append('&').append(entity);
                        if (c == ';') {
                            snippet.append(';');
                        }
                    }

                    // If we reached the end of the message body, exit.
                    if (c == -1) {
                        break;
                    }
                } else {
                    // The current character is a non-whitespace character that
                    // isn't inside some
                    // HTML tag and is not part of an HTML entity.
                    snippet.append((char) c);
                }
            }
        } catch (IOException e) {
            LogUtils.wtf(LOG_TAG, e, "Really? IOException while reading a freaking string?!? ");
        }

        return snippet.toString();
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        boolean transform = mIsSnappy && (mDrawTranslateY != 0);
        int saved = -1;
        if (transform) {
            saved = canvas.save();
            canvas.translate(0, mDrawTranslateY);
        }
        super.dispatchDraw(canvas);
        if (transform) {
            canvas.restoreToCount(saved);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Timer perf = new Timer();
        perf.start(LAYOUT_TAG);
        super.onLayout(changed, l, t, r, b);
        perf.pause(LAYOUT_TAG);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Timer t = new Timer();
        if (Timer.ENABLE_TIMER && !mPreMeasuring) {
            t.count("header measure id=" + mMessage.id);
            t.start(MEASURE_TAG);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!mPreMeasuring) {
            t.pause(MEASURE_TAG);
        }
    }

}
