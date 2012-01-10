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
package com.android.mail.compose;

import com.android.mail.R;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.Utils;

import java.text.DateFormat;
import java.util.Date;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.text.Html;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;

/*
 * View for displaying the quoted text in the compose screen for a reply
 * or forward. A close button is included in the upper right to remove
 * the quoted text from the message.
 */
class QuotedTextView extends LinearLayout implements OnClickListener {
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
    private static final String HEADER_SEPARATOR = "<br type='attribution'>";
    private static final int HEADER_SEPARATOR_LENGTH = HEADER_SEPARATOR.length();

    private CharSequence mQuotedText;
    private WebView mQuotedTextWebView;
    private ShowHideQuotedTextListener mShowHideListener;
    private CheckBox mShowHideCheckBox;
    private boolean mIncludeText = true;
    private Button mRespondInlineButton;
    private RespondInlineListener mRespondInlineListener;

    public QuotedTextView(Context context) {
        this(context, null);
    }

    public QuotedTextView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public QuotedTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        LayoutInflater factory = LayoutInflater.from(context);
        factory.inflate(R.layout.quoted_text, this);

        mQuotedTextWebView = (WebView) findViewById(R.id.quoted_text_web_view);
        Utils.restrictWebView(mQuotedTextWebView);

        mShowHideCheckBox = (CheckBox) findViewById(R.id.hide_quoted_text);
        mShowHideCheckBox.setChecked(true);
        mShowHideCheckBox.setOnClickListener(this);
        findViewById(R.id.hide_quoted_text_label).setOnClickListener(this);


        mRespondInlineButton = (Button) findViewById(R.id.respond_inline_button);
        if (mRespondInlineButton != null) {
            mRespondInlineButton.setEnabled(false);
        }
    }

    public void onDestroy() {
        if (mQuotedTextWebView != null) {
            mQuotedTextWebView.destroy();
        }
    }

    /**
     * Allow the user to include quoted text.
     * @param allow
     */
    public void allowQuotedText(boolean allow) {
        View quotedTextRow = findViewById(R.id.quoted_text_row);
        if (quotedTextRow != null) {
            quotedTextRow.setVisibility(allow? View.VISIBLE: View.GONE);
        }
    }

    /**
     * Allow the user to respond inline.
     * @param allow
     */
    public void allowRespondInline(boolean allow) {
        if (mRespondInlineButton != null) {
            mRespondInlineButton.setVisibility(allow? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Returns the quoted text if the user hasn't dismissed it, otherwise
     * returns null.
     */
    public CharSequence getQuotedTextIfIncluded() {
        if (mIncludeText) {
            return mQuotedText;
        }
        return null;
    }

    /**
     * Always returns the quoted text.
     */
    public CharSequence getQuotedText() {
        return mQuotedText;
    }

    /**
     * @return whether or not the user has selected to include quoted text.
     */
    public boolean isTextIncluded() {
        return mIncludeText;
    }

    public void setShowHideListener(ShowHideQuotedTextListener listener) {
        mShowHideListener = listener;
    }


    public void setRespondInlineListener(RespondInlineListener listener) {
        mRespondInlineListener = listener;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.respond_inline_button: {
                respondInline();
                break;
            }
            case R.id.hide_quoted_text:
                updateCheckedState(mShowHideCheckBox.isChecked());
                break;
            case R.id.hide_quoted_text_label:
                updateCheckedState(!mShowHideCheckBox.isChecked());
                break;
        }
    }

    /**
     * Update the state of the checkbox for the QuotedTextView as if it were
     * tapped by the user. Also updates the visibility of the QuotedText area.
     * @param checked Either true or false.
     */
    private void updateCheckedState(boolean checked) {
        mShowHideCheckBox.setChecked(checked);
        updateQuotedTextVisibility(checked);
        if (mShowHideListener != null) {
            mShowHideListener.onShowHideQuotedText(checked);
        }
    }

    private void updateQuotedTextVisibility(boolean show) {
        mQuotedTextWebView.setVisibility(show ? View.VISIBLE : View.GONE);
        mIncludeText = show;
    }

    private void populateData() {
        String backgroundColor = getContext().getResources().getString(
                R.string.quoted_text_background_color_string);
        String fontColor = getContext().getResources().getString(
                R.string.quoted_text_font_color_string);
        String html = "<head><style type=\"text/css\">* body { background-color: "
                + backgroundColor + "; color: " + fontColor + "; }</style></head>"
                + mQuotedText.toString();
        mQuotedTextWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    private void respondInline() {
        // Copy the text in the quoted message to the body of the
        // message after stripping the html.
        String quotedText = getQuotedText().toString();
        String html = TextUtils.isEmpty(quotedText) ?
                "" : Html.fromHtml(quotedText).toString();
        if (mRespondInlineListener != null) {
            mRespondInlineListener.onRespondInline("\n" + html);
        }
        // Set quoted text to unchecked and not visible.
        updateCheckedState(false);
        mRespondInlineButton.setVisibility(View.GONE);
        // Hide everything to do with quoted text.
        View quotedTextView = findViewById(R.id.quoted_text_area);
        if (quotedTextView != null) {
            quotedTextView.setVisibility(View.GONE);
        }
    }

    /**
     * Interface for listeners that want to be notified when quoted text
     * is shown / hidden.
     */
    public interface ShowHideQuotedTextListener {
        public void onShowHideQuotedText(boolean show);
    }

    /**
     * Interface for listeners that want to be notified when the user
     * chooses to respond inline.
     */
    public interface RespondInlineListener {
        public void onRespondInline(String text);
    }

    public void setQuotedText(int action, Cursor refMessage, boolean allow) {
        StringBuffer quotedText = new StringBuffer();
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        Date date = new Date(refMessage.getLong(UIProvider.MESSAGE_DATE_RECEIVED_MS_COLUMN));
        Resources resources = getContext().getResources();
        if (action == ComposeActivity.REPLY || action == ComposeActivity.REPLY_ALL) {
            quotedText.append(QUOTE_BEGIN);
            quotedText
                    .append(String.format(
                            resources.getString(R.string.reply_attribution),
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
                    .append(String.format(resources.getString(R.string.forward_attribution), Utils
                            .cleanUpString(refMessage.getString(UIProvider.MESSAGE_FROM_COLUMN),
                                    true /* remove empty quotes */), dateFormat.format(date), Utils
                            .cleanUpString(refMessage.getString(UIProvider.MESSAGE_SUBJECT_COLUMN),
                                    false /* don't remove empty quotes */), Utils.cleanUpString(
                            refMessage.getString(UIProvider.MESSAGE_TO_COLUMN), true)));
            String ccAddresses = refMessage.getString(UIProvider.MESSAGE_CC_COLUMN);
            quotedText.append(String.format(resources.getString(R.string.cc_attribution),
                    Utils.cleanUpString(ccAddresses, true /* remove empty quotes */)));
        }
        quotedText.append(HEADER_SEPARATOR);
        quotedText.append(refMessage.getString(UIProvider.MESSAGE_BODY_HTML));
        quotedText.append(QUOTE_END);
        setQuotedText(quotedText);
        allowQuotedText(allow);
        // If there is quoted text, we always allow respond inline, since this
        // may be a forward.
        allowRespondInline(true);
    }


    /**
     * Set quoted text. Some use cases may not want to display the check box (i.e. forwarding) so
     * allow control of that.
     */
    private void setQuotedText(CharSequence quotedText) {
        mQuotedText = quotedText;
        populateData();
        if (mRespondInlineButton != null) {
            if (!TextUtils.isEmpty(quotedText)) {
                mRespondInlineButton.setVisibility(View.VISIBLE);
                mRespondInlineButton.setEnabled(true);
                mRespondInlineButton.setOnClickListener(this);
            } else {
                // No text to copy; disable the respond inline button.
                mRespondInlineButton.setVisibility(View.GONE);
                mRespondInlineButton.setEnabled(false);
            }
        }
    }

    public static boolean containsQuotedText(String text) {
        int pos = text.indexOf(QuotedTextView.HEADER_SEPARATOR);
        return pos >= 0;
    }

    public static int getQuotedTextOffset(String text) {
        return text.indexOf(QuotedTextView.HEADER_SEPARATOR) + HEADER_SEPARATOR_LENGTH;
    }
}
