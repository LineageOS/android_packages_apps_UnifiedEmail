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

import android.content.Context;
import android.database.Cursor;
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
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu.OnMenuItemClickListener;

import com.android.mail.ContactInfoSource;
import com.android.mail.FormattedDateBuilder;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.perf.Timer;
import com.android.mail.providers.Account;
import com.android.mail.providers.Address;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider;
import com.android.mail.R;
import com.android.mail.SenderInfoLoader.ContactInfo;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

// TODO: this will probably becomes the message header view?
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

    private static final String LOG_TAG = new LogUtils().getLogTag();

    private MessageHeaderViewCallbacks mCallbacks;
    private long mLocalMessageId = UIProvider.INVALID_CONVERSATION_ID;
    private long mServerMessageId;
    private long mConversationId;
    private boolean mSizeChanged;

    private TextView mSenderNameView;
    private TextView mSenderEmailView;
    private QuickContactBadge mPhotoView;
    private ImageView mStarView;
    private ViewGroup mTitleContainerView;
    private ViewGroup mCollapsedDetailsView;
    private ViewGroup mExpandedDetailsView;
    private ViewGroup mImagePromptView;
    private View mBottomBorderView;
    private ImageView mPresenceView;

    // temporary fields to reference raw data between initial render and details
    // expansion
    private String[] mTo;
    private String[] mCc;
    private String[] mBcc;
    private String[] mReplyTo;
    private long mTimestampMs;
    private FormattedDateBuilder mDateBuilder;

    private boolean mIsDraft = false;

    private boolean mIsSending;

    private boolean mIsExpanded;

    private boolean mDetailsExpanded;

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

    private boolean mShowImagePrompt;

    private boolean mDefaultReplyAll;

    private int mDrawTranslateY;

    /**
     * List of attachments for this message. Will not be null.
     */
    private List<Attachment> mAttachments;

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

    private String mUri;

    private PopupMenu mPopup;

    public MessageHeaderView(Context context) {
        this(context, null);
    }

    public MessageHeaderView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public MessageHeaderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSenderNameView = (TextView) findViewById(R.id.sender_name);
        mSenderEmailView = (TextView) findViewById(R.id.sender_email);
        mPhotoView = (QuickContactBadge) findViewById(R.id.photo);
        mStarView = (ImageView) findViewById(R.id.star);
        mPresenceView = (ImageView) findViewById(R.id.presence);
        mTitleContainerView = (ViewGroup) findViewById(R.id.title_container);

        mCollapsedStarVis = mStarView.getVisibility();
        mTitleContainerCollapsedMarginRight = ((MarginLayoutParams) mTitleContainerView
                .getLayoutParams()).rightMargin;

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

    public interface MessageHeaderViewCallbacks {
        void setMessageSpacerHeight(long localMessageId, int height);

        void setMessageExpanded(long localMessageId, long serverMessageId, boolean expanded,
                int spacerHeight);

        Timer getLoadTimer();

        void onHeaderCreated(long headerId);

        void onHeaderDrawn(long headerId);

        void showExternalResources(long localMessageId);

        void setDisplayImagesFromSender(String fromAddress);
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

    public long getLocalMessageId() {
        return mLocalMessageId;
    }

    public boolean isExpanded() {
        return mIsExpanded;
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
        return other != null && mLocalMessageId == other.mLocalMessageId;
    }

    /**
     * Headers that are unbound will not match any rendered header (matches()
     * will return false). Unbinding is not guaranteed to *hide* the view's old
     * data, though. To re-bind this header to message data, call render() or
     * renderUpperHeaderFrom().
     */
    public void unbind() {
        mLocalMessageId = UIProvider.INVALID_MESSAGE_ID;
    }

    public void renderUpperHeaderFrom(MessageHeaderView other) {
        mLocalMessageId = other.mLocalMessageId;
        mServerMessageId = other.mServerMessageId;
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

    public void initialize(FormattedDateBuilder dateBuilder, Account account, boolean expanded,
            boolean showImagePrompt, boolean defaultReplyAll) {
        mDateBuilder = dateBuilder;
        mAccount = account;
        setExpanded(expanded);
        mShowImagePrompt = showImagePrompt;
        mDefaultReplyAll = defaultReplyAll;
    }

    public int bind(Cursor cursor) {
        Timer t = new Timer();
        t.start(HEADER_RENDER_TAG);

        mUri = cursor.getString(UIProvider.MESSAGE_URI_COLUMN);
        mLocalMessageId = cursor.getLong(UIProvider.MESSAGE_ID_COLUMN);
        mServerMessageId = cursor.getLong(UIProvider.MESSAGE_SERVER_ID_COLUMN);
        mConversationId = cursor.getLong(UIProvider.MESSAGE_CONVERSATION_ID_COLUMN);
        if (mCallbacks != null) {
            mCallbacks.onHeaderCreated(mLocalMessageId);
        }

        setTag(mLocalMessageId);

        mTimestampMs = cursor.getLong(UIProvider.MESSAGE_DATE_RECEIVED_MS_COLUMN);
        if (mDateBuilder != null) {
            mTimestampShort = mDateBuilder.formatShortDate(mTimestampMs);
        }

        mTo = Utils.splitCommaSeparatedString(cursor.getString(UIProvider.MESSAGE_TO_COLUMN));
        mCc = Utils.splitCommaSeparatedString(cursor.getString(UIProvider.MESSAGE_CC_COLUMN));
        mBcc = getBccAddresses(cursor);
        mReplyTo = Utils.splitCommaSeparatedString(cursor
                .getString(UIProvider.MESSAGE_REPLY_TO_COLUMN));

        /**
         * Turns draft mode on or off. Draft mode hides message operations other
         * than "edit", hides contact photo, hides presence, and changes the
         * sender name to "Draft".
         */
        mIsDraft = !TextUtils.isEmpty(cursor.getString(UIProvider.MESSAGE_DRAFT_TYPE_COLUMN));
        mIsSending = isInOutbox(cursor);

        updateChildVisibility();

        if (mIsDraft || isInOutbox(cursor)) {
            mSnippet = makeSnippet(cursor.getString(UIProvider.MESSAGE_SNIPPET_COLUMN));
        } else {
            mSnippet = cursor.getString(UIProvider.MESSAGE_SNIPPET_COLUMN);
        }

        // If this was a sent message AND:
        // 1. the account has a custom from, the cursor will populate the
        // selected custom from as the fromAddress when a message is sent but
        // not yet synced.
        // 2. the account has no custom froms, fromAddress will be empty, and we
        // can safely fall back and show the account name as sender since it's
        // the only possible fromAddress.
        String from = cursor.getString(UIProvider.MESSAGE_FROM_COLUMN);
        if (TextUtils.isEmpty(from)) {
            from = mAccount.name;
        }
        mSender = Address.getEmailAddress(from);

        mSenderNameView.setText(getHeaderTitle());
        mSenderEmailView.setText(getHeaderSubtitle());

        TextView upperDateView = (TextView) findViewById(R.id.upper_date);
        if (upperDateView != null) {
            upperDateView.setText(mTimestampShort);
        }

        mStarView.setSelected((cursor.getInt(UIProvider.MESSAGE_FLAGS_COLUMN)
                & UIProvider.MessageFlags.STARRED) == 1);
        mStarView.setContentDescription(getResources().getString(
                mStarView.isSelected() ? R.string.remove_star : R.string.add_star));

        updateContactInfo();

        t.pause(HEADER_RENDER_TAG);
        t.start(PREMEASURE_TAG);

        // TODO: optimize here. pre-measuring every header when many of them are
        // similar is silly.
        // also, doing a full measurement pass is more work than is strictly
        // needed. all we really need in most cases is the combined pixel height
        // of various fixed-height views. Only if the details header is expanded
        // (almost never the case during a render) is the header height
        // variable.
        int h = forceMeasuredHeight();
        t.pause(PREMEASURE_TAG);
        return h;
    }

    private boolean isInOutbox(Cursor cursor) {
        // TODO: what should this read? Folder info?
        return false;
    }

    private int forceMeasuredHeight() {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) {
            return getHeight();
        }
        mPreMeasuring = true;
        int h = Utils.measureViewHeight(this, parent);
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
            sub = mIsExpanded ? getSenderAddress(mSender) : mSnippet;
        }
        return sub;
    }

    /**
     * Return the name, if known, or just the address.
     */
    private static CharSequence getSenderName(Address sender) {
        String displayName = sender == null ? "" : sender.getName();
        return TextUtils.isEmpty(displayName) && sender != null ? sender.getAddress() : displayName;
    }

    /**
     * Return the address, if a name is present, or null if not.
     */
    private static CharSequence getSenderAddress(Address sender) {
        String displayName = sender == null ? "" : sender.getName();
        return TextUtils.isEmpty(displayName) ? null : sender.getAddress();
    }

    private void setChildVisibility(int visibility, int... resources) {
        for (int res : resources) {
            View v = findViewById(res);
            if (v != null) {
                v.setVisibility(visibility);
            }
        }
    }

    private void setExpanded(final boolean expanded) {
        // use View's 'activated' flag to store expanded state
        // child view state lists can use this to toggle drawables
        setActivated(expanded);
        mIsExpanded = expanded;
    }

    /**
     * Update the visibility of the many child views based on expanded/collapsed
     * and draft/normal state.
     */
    private void updateChildVisibility() {
        // Too bad this can't be done with an XML state list...

        if (mIsExpanded) {
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
            setChildVisibility(normalVis, R.id.photo, R.id.photo_spacer, R.id.forward,
                    R.id.sender_email, R.id.overflow);
            setChildVisibility(draftVis, R.id.draft, R.id.edit_draft);
            setChildVisibility(GONE, R.id.attachment, R.id.upper_date);
            setChildVisibility(VISIBLE, R.id.star);

            setChildMarginRight(mTitleContainerView, 0);

        } else {

            setMessageDetailsVisibility(GONE);
            setChildVisibility(VISIBLE, R.id.sender_email, R.id.upper_date);

            setChildVisibility(GONE, R.id.edit_draft, R.id.reply, R.id.reply_all, R.id.forward);
            setChildVisibility(GONE, R.id.overflow);

            setChildVisibility(mAttachments == null || mAttachments.isEmpty() ? GONE : VISIBLE,
                    R.id.attachment);

            setChildVisibility(mCollapsedStarVis, R.id.star);

            setChildMarginRight(mTitleContainerView, mTitleContainerCollapsedMarginRight);

            if (mIsDraft) {

                setChildVisibility(VISIBLE, R.id.draft);
                setChildVisibility(GONE, R.id.photo, R.id.photo_spacer);

            } else {

                setChildVisibility(GONE, R.id.draft);
                setChildVisibility(VISIBLE, R.id.photo, R.id.photo_spacer);

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
            setChildVisibility(GONE, R.id.reply, R.id.reply_all);
            return;
        } else if (findViewById(R.id.overflow) == null) {
            setChildVisibility(VISIBLE, R.id.reply, R.id.reply_all);
            return;
        }

        setChildVisibility(mDefaultReplyAll ? GONE : VISIBLE, R.id.reply);
        setChildVisibility(mDefaultReplyAll ? VISIBLE : GONE, R.id.reply_all);
    }

    private static void setChildMarginRight(View childView, int marginRight) {
        MarginLayoutParams mlp = (MarginLayoutParams) childView.getLayoutParams();
        mlp.rightMargin = marginRight;
        childView.setLayoutParams(mlp);
    }

    private void renderEmailList(int rowRes, int valueRes, String[] emails) {
        if (emails == null || emails.length == 0) {
            return;
        }
        String[] formattedEmails = new String[emails.length];
        for (int i = 0; i < emails.length; i++) {
            Address e = Address.getEmailAddress(emails[i]);
            String name = e.getName();
            String addr = e.getAddress();
            if (name == null || name.length() == 0) {
                formattedEmails[i] = addr;
            } else {
                formattedEmails[i] = getResources().getString(R.string.address_display_format,
                        name, addr);
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

        int mRecipientCount = 0;
        boolean mFirst = true;

        public RecipientListsBuilder(Context context, String me) {
            mContext = context;
            mMe = me;
            mComma = mContext.getText(R.string.enumeration_comma);
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
                Address email = Address.getEmailAddress(rawAddrs[i]);
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
            String[] cc, String[] bcc) {

        RecipientListsBuilder builder = new RecipientListsBuilder(context, me);

        builder.append(to, R.string.to_heading);
        builder.append(cc, R.string.cc_heading);
        builder.append(bcc, R.string.bcc_heading);

        return builder.build();
    }

    /**
     * Get BCC addresses attached to a recipient ONLY if this is a msg the
     * current user sent.
     *
     * @param messageCursor Cursor to query for label objects with
     */
    private static String[] getBccAddresses(Cursor cursor) {
        return Utils.splitCommaSeparatedString(cursor.getString(UIProvider.MESSAGE_CC_COLUMN));
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
                ComposeActivity.reply(getContext(), mAccount, mUri);
                break;
            case R.id.reply_all:
                ComposeActivity.replyAll(getContext(), mAccount, mUri);
                break;
            case R.id.forward:
                ComposeActivity.forward(getContext(), mAccount, mUri);
                break;
            case R.id.star: {
                boolean newValue = !v.isSelected();
                v.setSelected(newValue);
                break;
            }
            case R.id.edit_draft:
                ComposeActivity.editDraft(getContext(), mAccount, mLocalMessageId);
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

        setExpanded(!mIsExpanded);

        mSenderNameView.setText(getHeaderTitle());
        mSenderEmailView.setText(getHeaderSubtitle());

        updateChildVisibility();

        // Force-measure the new header height so we can set the spacer size and
        // reveal the message
        // div in one pass. Force-measuring makes it unnecessary to set
        // mSizeChanged.
        int h = forceMeasuredHeight();
        if (mCallbacks != null) {
            mCallbacks.setMessageExpanded(mLocalMessageId, mServerMessageId, mIsExpanded, h);
        }
    }

    private void toggleMessageDetails(View visibleDetailsView) {
        setMessageDetailsExpanded(visibleDetailsView == mCollapsedDetailsView);
        mSizeChanged = true;
    }

    private void setMessageDetailsExpanded(boolean expand) {
        if (expand) {
            showExpandedDetails();
            hideCollapsedDetails();
        } else {
            hideExpandedDetails();
            showCollapsedDetails();
        }
        mDetailsExpanded = expand;
    }

    public void setMessageDetailsVisibility(int vis) {
        if (vis == GONE) {
            hideCollapsedDetails();
            hideExpandedDetails();
            hideShowImagePrompt();
            hideAttachments();
        } else {
            setMessageDetailsExpanded(mDetailsExpanded);
            if (mShowImagePrompt) {
                showImagePrompt();
            }
            if (mAttachments != null && !mAttachments.isEmpty()) {
                showAttachments();
            }
        }
        if (mBottomBorderView != null) {
            mBottomBorderView.setVisibility(vis);
        }
    }

    private void showAttachments() {
        // Do nothing. Attachments not supported yet.
    }

    private void hideAttachments() {
        // Do nothing. Attachments not supported yet.
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

    private void hideShowImagePrompt() {
        if (mImagePromptView != null) {
            mImagePromptView.setVisibility(GONE);
        }
    }

    private void showImagePrompt() {
        if (mImagePromptView == null) {
            ViewGroup v = (ViewGroup) LayoutInflater.from(getContext()).inflate(
                    R.layout.conversation_message_show_pics, this, false);
            addView(v);
            v.setOnClickListener(this);
            v.setTag(SHOW_IMAGE_PROMPT_ONCE);

            mImagePromptView = v;
        }
        mImagePromptView.setVisibility(VISIBLE);
    }

    private void handleShowImagePromptClick(View v) {
        Integer state = (Integer) v.getTag();
        if (state == null) {
            return;
        }
        switch (state) {
            case SHOW_IMAGE_PROMPT_ONCE:
                if (mCallbacks != null) {
                    mCallbacks.showExternalResources(mLocalMessageId);
                }
                ImageView descriptionViewIcon = (ImageView) v.findViewById(R.id.show_pictures_icon);
                descriptionViewIcon.setContentDescription(getResources().getString(
                        R.string.always_show_images));
                TextView descriptionView = (TextView) v.findViewById(R.id.show_pictures_text);
                descriptionView.setText(R.string.always_show_images);
                v.setTag(SHOW_IMAGE_PROMPT_ALWAYS);
                // the new text's line count may differ, which should trigger a
                // size change to
                // update the spacer height
                mSizeChanged = true;
                break;
            case SHOW_IMAGE_PROMPT_ALWAYS:
                if (mCallbacks != null) {
                    mCallbacks.setDisplayImagesFromSender(mSender.getAddress());
                }
                mShowImagePrompt = false;
                v.setTag(null);
                v.setVisibility(GONE);
                mSizeChanged = true;
                Toast.makeText(getContext(), R.string.always_show_images_toast, Toast.LENGTH_SHORT)
                        .show();
                break;
        }
    }

    /**
     * Makes collapsed details visible. If necessary, will inflate details
     * layout and render using saved-off state (senders, timestamp, etc).
     */
    private void showCollapsedDetails() {
        if (mCollapsedDetailsView == null) {
            // Collapsed details is a merge layout that also contains the bottom
            // border. The
            // assumption is that collapsed is inflated before expanded. If we
            // ever change this
            // so either may be inflated first, the bottom border should be
            // moved out into a
            // separate layout and inflated alongside either collapsed or
            // expanded, whichever is
            // first.
            LayoutInflater.from(getContext()).inflate(R.layout.conversation_message_details_header,
                    this);

            mBottomBorderView = findViewById(R.id.details_bottom_border);
            mCollapsedDetailsView = (ViewGroup) findViewById(R.id.details_collapsed_content);

            mCollapsedDetailsView.setOnClickListener(this);

            ((TextView) findViewById(R.id.recipients_summary)).setText(getRecipientSummaryText(
                    getContext(), mAccount.name, mTo, mCc, mBcc));

            ((TextView) findViewById(R.id.date_summary)).setText(mTimestampShort);
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
            View v = LayoutInflater.from(getContext()).inflate(
                    R.layout.conversation_message_details_header_expanded, this, false);

            // Insert expanded details into the parent linear layout immediately
            // after the
            // previously inflated collapsed details view, and above any other
            // optional views
            // like 'show pictures' or attachments.
            // we assume collapsed has been inflated by now
            addView(v, indexOfChild(mCollapsedDetailsView) + 1);
            v.setOnClickListener(this);

            CharSequence longTimestamp = mDateBuilder != null ? mDateBuilder
                    .formatLongDateTime(mTimestampMs) : mTimestampMs + "";
            ((TextView) findViewById(R.id.date_value)).setText(longTimestamp);
            renderEmailList(R.id.replyto_row, R.id.replyto_value, mReplyTo);
            renderEmailList(R.id.to_row, R.id.to_value, mTo);
            renderEmailList(R.id.cc_row, R.id.cc_value, mCc);
            renderEmailList(R.id.bcc_row, R.id.bcc_value, mBcc);
            // don't need these any more, release them
            mReplyTo = mTo = mCc = mBcc = null;

            mExpandedDetailsView = (ViewGroup) v;
        }
        mExpandedDetailsView.setVisibility(VISIBLE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (mSizeChanged) {
            // propagate new size to webview header spacer
            // only do this for known size changes
            if (mCallbacks != null) {
                mCallbacks.setMessageSpacerHeight(mLocalMessageId, h);
            }

            mSizeChanged = false;
        }
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
        if (mCallbacks != null) {
            mCallbacks.onHeaderDrawn(mLocalMessageId);
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
            t.count("header measure id=" + mLocalMessageId);
            t.start(MEASURE_TAG);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!mPreMeasuring) {
            t.pause(MEASURE_TAG);
        }
    }
}
