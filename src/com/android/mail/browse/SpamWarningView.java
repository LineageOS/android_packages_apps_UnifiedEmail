package com.android.mail.browse;

import android.content.Context;
import android.text.Html;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Address;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;

public class SpamWarningView extends RelativeLayout implements OnClickListener {
    private ImageView mSpamWarningIcon;
    private TextView mSpamWarningText;
    private TextView mSpamWarningLink;
    private final int mHighWarningColor;
    private final int mLowWarningColor;

    public SpamWarningView(Context context) {
        this(context, null);
    }

    public SpamWarningView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mHighWarningColor = getResources().getColor(R.color.high_spam_color);
        mLowWarningColor = getResources().getColor(R.color.conv_header_text_light);
    }

    @Override
    public void onFinishInflate() {
        setOnClickListener(this);

        mSpamWarningIcon = (ImageView) findViewById(R.id.spam_warning_icon);
        mSpamWarningText = (TextView) findViewById(R.id.spam_warning_text);
        mSpamWarningLink = (TextView) findViewById(R.id.spam_warning_link);
        mSpamWarningLink.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();

        switch (id) {
            case R.id.spam_warning:
                break;
            case R.id.spam_warning_link:
                // TODO - once we have final design,
                // make clicking this text do what needs to be done
                break;
        }

    }

    public void showSpamWarning(Message message, Address sender) {
        setVisibility(VISIBLE);

        // Sets the text and adds any necessary formatting
        // to enable the proper display.
        final String senderAddress = sender.getAddress();
        final String senderDomain = senderAddress.substring(senderAddress.indexOf('@')+1);
        mSpamWarningText.setText(Html.fromHtml(String.format(
                message.spamWarningString, senderAddress, senderDomain)));

        // TODO - when we have updated assets, set them to the right value based
        // upon the spam warning level
        if (message.spamWarningLevel == UIProvider.SpamWarningLevel.HIGH_WARNING) {
            mSpamWarningText.setTextColor(mHighWarningColor);
            mSpamWarningIcon.setImageResource(R.drawable.ic_email_network_error);
        } else {
            mSpamWarningText.setTextColor(mLowWarningColor);
            mSpamWarningIcon.setImageResource(R.drawable.ic_email_network_error);
        }

        // Sets the link to the appropriate text
        // and sets visibility, if necessary.
        final int linkType = message.spamLinkType;
        switch (linkType) {
            case UIProvider.SpamWarningLinkType.NO_LINK:
                mSpamWarningLink.setVisibility(GONE);
                break;
            case UIProvider.SpamWarningLinkType.IGNORE_WARNING:
                mSpamWarningLink.setVisibility(VISIBLE);
                mSpamWarningLink.setText(R.string.ignore_spam_warning);
                break;
            case UIProvider.SpamWarningLinkType.REPORT_PHISHING:
                mSpamWarningLink.setVisibility(VISIBLE);
                mSpamWarningLink.setText(R.string.report_phishing);
                break;
        }
    }
}
