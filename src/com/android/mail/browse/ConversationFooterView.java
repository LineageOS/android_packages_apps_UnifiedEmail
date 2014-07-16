package com.android.mail.browse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.android.mail.R;
import com.android.mail.browse.ConversationViewAdapter.ConversationFooterItem;
import com.android.mail.browse.ConversationViewAdapter.MessageHeaderItem;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.providers.Account;
import com.android.mail.providers.Message;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

/**
 * A view placed at the bottom of the conversation view that allows the user to
 * reply/reply all/forward to the last message in the conversation.
 */
public class ConversationFooterView extends LinearLayout implements View.OnClickListener {

    private static final String LOG_TAG = LogTag.getLogTag();

    private ConversationFooterItem mFooterItem;
    private ConversationAccountController mAccountController;

    public ConversationFooterView(Context context) {
        super(context);
    }

    public ConversationFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConversationFooterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        findViewById(R.id.reply_button).setOnClickListener(this);
        findViewById(R.id.reply_all_button).setOnClickListener(this);
        findViewById(R.id.forward_button).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (mFooterItem == null) {
            LogUtils.i(LOG_TAG, "ignoring conversation footer tap on unbound view");
            return;
        }
        final MessageHeaderItem headerItem = mFooterItem.getLastMessageHeaderItem();
        if (headerItem == null) {
            LogUtils.i(LOG_TAG, "ignoring conversation footer tap on null header item");
            return;
        }
        final Message message = headerItem.getMessage();
        if (message == null) {
            LogUtils.i(LOG_TAG, "ignoring conversation footer tap on null message");
            return;
        }
        final int id = v.getId();
        if (id == R.id.reply_button) {
            ComposeActivity.reply(getContext(), getAccount(), message);
        } else if (id == R.id.reply_all_button) {
            ComposeActivity.replyAll(getContext(), getAccount(), message);
        } else if (id == R.id.forward_button) {
            ComposeActivity.forward(getContext(), getAccount(), message);
        }
    }

    public void bind(ConversationFooterItem footerItem) {
        mFooterItem = footerItem;
    }

    public void setAccountController(ConversationAccountController accountController) {
        mAccountController = accountController;
    }

    private Account getAccount() {
        return mAccountController != null ? mAccountController.getAccount() : null;
    }
}
