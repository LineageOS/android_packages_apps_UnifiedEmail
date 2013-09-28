/**
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

package com.android.mail.print;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.browse.ConversationMessage;
import com.android.mail.browse.MessageCursor;
import com.android.mail.providers.Account;
import com.android.mail.providers.Address;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.AttachmentUtils;
import com.android.mail.utils.Utils;

import java.util.List;
import java.util.Map;

/**
 * Static class that provides a {@link #print} function to build a print html document.
 */
public class Printer {
    private static final String DIV_START = "<div>";
    private static final String REPLY_TO_DIV_START = "<div class=\"replyto\">";
    private static final String DIV_END = "</div>";

    /**
     * Builds an html document that is suitable for printing and returns it as a {@link String}.
     */
    public static String print(Context context, Account account,
            MessageCursor cursor, Map<String, Address> addressCache, boolean useJavascript) {
        final HtmlPrintTemplates templates = new HtmlPrintTemplates(context);
        final FormattedDateBuilder dateBuilder = new FormattedDateBuilder(context);

        if (!cursor.moveToFirst()) {
            throw new IllegalStateException("trying to print without a conversation");
        }

        // TODO - remove account name(not account.name which is email address) or get it somehow
        final Conversation conversation = cursor.getConversation();
        templates.startPrintConversation("", account.name,
                conversation.subject, conversation.getNumMessages());

        // for each message in the conversation, add message html
        final Resources res = context.getResources();
        do {
            final ConversationMessage message = cursor.getMessage();
            final Address fromAddress = Utils.getAddress(addressCache, message.getFrom());
            final long when = message.dateReceivedMs;
            final String date = res.getString(R.string.date_message_received_print,
                    dateBuilder.formatLongDayAndDate(when), dateBuilder.formatLongTime(when));


            templates.appendMessage(fromAddress.getName(), fromAddress.getAddress(), date,
                    renderRecipients(res, addressCache, message), message.getBodyAsHtml(),
                    renderAttachments(context, res, message));
        } while (cursor.moveToNext());

        // only include JavaScript if specifically requested
        return useJavascript ?
                templates.endPrintConversation() : templates.endPrintConversationNoJavascript();
    }

    /**
     * Builds html for the message header. Specifically, the (optional) lists of
     * reply-to, to, cc, and bcc.
     */
    private static String renderRecipients(Resources res, Map<String, Address> addressCache,
            ConversationMessage message) {
        final StringBuilder recipients = new StringBuilder();

        // reply-to
        final String replyTo = renderEmailList(res, message.getReplyToAddresses(), addressCache);
        buildEmailDiv(res, recipients, replyTo, REPLY_TO_DIV_START, DIV_END,
                R.string.replyto_heading);

        // to
        // To has special semantics since the message can be a draft.
        // If it is a draft and there are no to addresses, we just print "Draft".
        // If it is a draft and there are to addresses, we print "Draft To: "
        // If not a draft, we just use "To: ".
        final boolean isDraft = message.draftType != UIProvider.DraftType.NOT_A_DRAFT;
        final String to = renderEmailList(res, message.getToAddresses(), addressCache);
        if (isDraft && to == null) {
            recipients.append(DIV_START).append(res.getString(R.string.draft_heading))
                    .append(DIV_END);
        } else {
            buildEmailDiv(res, recipients, to, DIV_START, DIV_END,
                    isDraft ? R.string.draft_to_heading : R.string.to_heading);
        }

        // cc
        final String cc = renderEmailList(res, message.getCcAddresses(), addressCache);
        buildEmailDiv(res, recipients, cc, DIV_START, DIV_END,
                R.string.cc_heading);

        // bcc
        final String bcc = renderEmailList(res, message.getBccAddresses(), addressCache);
        buildEmailDiv(res, recipients, bcc, DIV_START, DIV_END,
                R.string.bcc_heading);

        return recipients.toString();
    }

    /**
     * Appends an html div containing a list of emails based on the passed in data.
     */
    private static void buildEmailDiv(Resources res, StringBuilder recipients, String emailList,
            String divStart, String divEnd, int headingId) {
        if (emailList != null) {
            recipients.append(divStart).append(res.getString(headingId))
                    .append(emailList).append(divEnd);
        }
    }

    /**
     * Builds and returns a list of comma-separated emails of the form "Name &lt;email&gt;".
     * If the email does not contain a name, "email" is used instead.
     */
    private static String renderEmailList(Resources resources, String[] emails,
            Map<String, Address> addressCache) {
        if (emails == null || emails.length == 0) {
            return null;
        }
        final String[] formattedEmails = new String[emails.length];
        for (int i = 0; i < emails.length; i++) {
            final Address email = Utils.getAddress(addressCache, emails[i]);
            final String name = email.getName();
            final String address = email.getAddress();

            if (TextUtils.isEmpty(name)) {
                formattedEmails[i] = address;
            } else {
                formattedEmails[i] = resources.getString(R.string.address_print_display_format,
                        name, address);
            }
        }

        return TextUtils.join(", ", formattedEmails);
    }

    /**
     * Builds and returns html for a message's attachments.
     */
    private static String renderAttachments(
            Context context, Resources resources, ConversationMessage message) {
        if (!message.hasAttachments) {
            return "";
        }

        final List<Attachment> attachments = message.getAttachments();
        final StringBuilder sb = new StringBuilder("<br clear=all>"
                + "<div style=\"width:50%;border-top:2px #AAAAAA solid\"></div>"
                + "<table class=att cellspacing=0 cellpadding=5 border=0>");

        // If the message has more than one attachment, list the number of attachments.
        final int numAttachments = attachments.size();
        if (numAttachments > 1) {
            sb.append("<tr><td colspan=2><b style=\"padding-left:3\">")
                    .append(resources.getQuantityString(
                            R.plurals.num_attachments, numAttachments, numAttachments))
                    .append("</b></td></tr>");
        }

        for (final Attachment attachment : attachments) {
            sb.append("<tr><td><table cellspacing=\"0\" cellpadding=\"0\"><tr>");

            // TODO - thumbnail previews of images

            sb.append("<td><img width=\"16\" height=\"16\" src=\"file:///android_asset/images/")
                    .append(getIconFilename(attachment.getContentType()))
                    .append("\"></td><td width=\"7\"></td><td><b>")
                    .append(attachment.getName())
                    .append("</b><br>").append(
                    AttachmentUtils.convertToHumanReadableSize(context, attachment.size))
                    .append("</td></tr></table></td></tr>");
        }

        sb.append("</table>");

        return sb.toString();
    }

    /**
     * Returns an appropriate filename for various attachment mime types.
     */
    private static String getIconFilename(String mimeType) {
        if (mimeType.startsWith("application/msword") ||
                mimeType.startsWith("application/vnd.oasis.opendocument.text") ||
                mimeType.equals("application/rtf") ||
                mimeType.equals("application/"
                        + "vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            return "doc.gif";
        } else if (mimeType.startsWith("image/")) {
            return "graphic.gif";
        } else if (mimeType.startsWith("text/html")) {
            return "html.gif";
        } else if (mimeType.startsWith("application/pdf")) {
            return "pdf.gif";
        } else if (mimeType.endsWith("powerpoint") ||
                mimeType.equals("application/vnd.oasis.opendocument.presentation") ||
                mimeType.equals("application/"
                        + "vnd.openxmlformats-officedocument.presentationml.presentation")) {
            return "ppt.gif";
        } else if ((mimeType.startsWith("audio/")) ||
                (mimeType.startsWith("music/"))) {
            return "sound.gif";
        } else if (mimeType.startsWith("text/plain")) {
            return "txt.gif";
        } else if (mimeType.endsWith("excel") ||
                mimeType.equals("application/vnd.oasis.opendocument.spreadsheet") ||
                mimeType.equals("application/"
                        + "vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            return "xls.gif";
        } else if ((mimeType.endsWith("zip")) ||
                (mimeType.endsWith("/x-compress")) ||
                (mimeType.endsWith("/x-compressed"))) {
            return "zip.gif";
        } else {
            return "generic.gif";
        }
    }
}
