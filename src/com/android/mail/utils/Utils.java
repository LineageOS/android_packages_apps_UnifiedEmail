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
package com.android.mail.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextUtils.SimpleStringSplitter;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.android.mail.R;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.regex.Pattern;

public class Utils {
    /**
     * longest extension we recognize is 4 characters (e.g. "html", "docx")
     */
    private static final int FILE_EXTENSION_MAX_CHARS = 4;
    private static final Map<Integer, Integer> sPriorityToLength = Maps.newHashMap();
    public static final String SENDER_LIST_TOKEN_ELIDED = "e";
    public static final String SENDER_LIST_TOKEN_NUM_MESSAGES = "n";
    public static final String SENDER_LIST_TOKEN_NUM_DRAFTS = "d";
    public static final String SENDER_LIST_TOKEN_LITERAL = "l";
    public static final String SENDER_LIST_TOKEN_SENDING = "s";
    public static final String SENDER_LIST_TOKEN_SEND_FAILED = "f";
    public static final Character SENDER_LIST_SEPARATOR = '\n';
    public static final SimpleStringSplitter sSenderListSplitter = new SimpleStringSplitter(
            SENDER_LIST_SEPARATOR);
    public static String[] sSenderFragments = new String[8];

    // "GMT" + "+" or "-" + 4 digits
    private static final Pattern DATE_CLEANUP_PATTERN_WRONG_TIMEZONE =
            Pattern.compile("GMT([-+]\\d{4})$");

    public static final String EXTRA_ACCOUNT = "account";

     /**
      * Sets WebView in a restricted mode suitable for email use.
      * @param webView The WebView to restrict
      */
     public static void restrictWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setSupportZoom(false);
     }

     /**
      * Format a plural string.
      * @param resource The identity of the resource, which must be a R.plurals
      * @param count The number of items.
      */
     public static String formatPlural(Context context, int resource, int count) {
         CharSequence formatString = context.getResources().getQuantityText(resource, count);
         return String.format(formatString.toString(), count);
     }

     /**
      * @return an ellipsized String that's at most maxCharacters long.  If the text passed is
      * longer, it will be abbreviated.  If it contains a suffix, the ellipses will be inserted
      * in the middle and the suffix will be preserved.
      */
     public static String ellipsize(String text, int maxCharacters) {
         int length = text.length();
         if (length < maxCharacters) return text;

         int realMax = Math.min(maxCharacters, length);
         // Preserve the suffix if any
         int index = text.lastIndexOf(".");
         String extension = "\u2026"; // "...";
         if (index >= 0) {
             // Limit the suffix to dot + four characters
             if (length - index <= FILE_EXTENSION_MAX_CHARS + 1) {
                 extension = extension + text.substring(index + 1);
             }
         }
         realMax -= extension.length();
         if (realMax < 0) realMax = 0;
         return text.substring(0, realMax) + extension;
     }

     /**
     * Ensures that the given string starts and ends with the double quote
     * character. The string is not modified in any way except to add the double
     * quote character to start and end if it's not already there. sample ->
     * "sample" "sample" -> "sample" ""sample"" -> "sample"
     * "sample"" -> "sample" sa"mp"le -> "sa"mp"le" "sa"mp"le" -> "sa"mp"le"
     * (empty string) -> "" " -> ""
     */
     public static String ensureQuotedString(String s) {
         if (s == null) {
             return null;
         }
         if (!s.matches("^\".*\"$")) {
             return "\"" + s + "\"";
         }
         else {
             return s;
         }
     }

     // TODO: Move this to the UI Provider.
     private static CharacterStyle sUnreadStyleSpan = null;
     private static CharacterStyle sReadStyleSpan;
     private static CharacterStyle sDraftsStyleSpan;
     private static CharSequence sMeString;
     private static CharSequence sDraftSingularString;
     private static CharSequence sDraftPluralString;
     private static CharSequence sSendingString;
     private static CharSequence sSendFailedString;

     public static void getStyledSenderSnippet(
             Context context, String senderInstructions,
             SpannableStringBuilder senderBuilder,
             SpannableStringBuilder statusBuilder,
             int maxChars, boolean forceAllUnread, boolean forceAllRead, boolean allowDraft) {
         Resources res = context.getResources();
         if (sUnreadStyleSpan == null) {
             sUnreadStyleSpan = new StyleSpan(Typeface.BOLD);
             sReadStyleSpan = new StyleSpan(Typeface.NORMAL);
             sDraftsStyleSpan = new ForegroundColorSpan(res.getColor(R.color.drafts));

             sMeString = context.getText(R.string.me);
             sDraftSingularString = res.getQuantityText(R.plurals.draft, 1);
             sDraftPluralString = res.getQuantityText(R.plurals.draft, 2);
             SpannableString sendingString = new SpannableString(context.getText(R.string.sending));
             sendingString.setSpan(
                     CharacterStyle.wrap(sDraftsStyleSpan), 0, sendingString.length(), 0);
             sSendingString = sendingString;
             sSendFailedString = context.getText(R.string.send_failed);
         }

         getSenderSnippet(
                 senderInstructions, senderBuilder, statusBuilder, maxChars,
                 sUnreadStyleSpan,
                 sReadStyleSpan,
                 sDraftsStyleSpan,
                 sMeString,
                 sDraftSingularString, sDraftPluralString,
                 sSendingString, sSendFailedString,
                 forceAllUnread, forceAllRead, allowDraft);
     }

     /**
     * Uses sender instructions to build a formatted string.
     * <p>
     * Sender list instructions contain compact information about the sender
     * list. Most work that can be done without knowing how much room will be
     * availble for the sender list is done when creating the instructions.
     * <p>
     * The instructions string consists of tokens separated by
     * SENDER_LIST_SEPARATOR. Here are the tokens, one per line:
     * <ul>
     * <li><tt>n</tt></li>
     * <li><em>int</em>, the number of non-draft messages in the conversation</li>
     * <li><tt>d</tt</li>
     * <li><em>int</em>, the number of drafts in the conversation</li>
     * <li><tt>l</tt></li>
     * <li><em>literal html to be included in the output</em></li>
     * <li><tt>s</tt> indicates that the message is sending (in the outbox
     * without errors)</li>
     * <li><tt>f</tt> indicates that the message failed to send (in the outbox
     * with errors)</li>
     * <li><em>for each message</em>
     * <ul>
     * <li><em>int</em>, 0 for read, 1 for unread</li>
     * <li><em>int</em>, the priority of the message. Zero is the most important
     * </li>
     * <li><em>text</em>, the sender text or blank for messages from 'me'</li>
     * </ul>
     * </li>
     * <li><tt>e</tt> to indicate that one or more messages have been elided</li>
     * <p>
     * The instructions indicate how many messages and drafts are in the
     * conversation and then describe the most important messages in order,
     * indicating the priority of each message and whether the message is
     * unread.
     *
     * @param instructions instructions as described above
     * @param senderBuilder the SpannableStringBuilder to append to for sender
     *            information
     * @param statusBuilder the SpannableStringBuilder to append to for status
     * @param maxChars the number of characters available to display the text
     * @param unreadStyle the CharacterStyle for unread messages, or null
     * @param draftsStyle the CharacterStyle for draft messages, or null
     * @param sendingString the string to use when there are messages scheduled
     *            to be sent
     * @param sendFailedString the string to use when there are messages that
     *            mailed to send
     * @param meString the string to use for messages sent by this user
     * @param draftString the string to use for "Draft"
     * @param draftPluralString the string to use for "Drafts"
     */
     public static synchronized void getSenderSnippet(
             String instructions, SpannableStringBuilder senderBuilder,
             SpannableStringBuilder statusBuilder, int maxChars,
             CharacterStyle unreadStyle,
             CharacterStyle readStyle,
             CharacterStyle draftsStyle,
             CharSequence meString, CharSequence draftString, CharSequence draftPluralString,
             CharSequence sendingString, CharSequence sendFailedString,
             boolean forceAllUnread, boolean forceAllRead, boolean allowDraft) {
         assert !(forceAllUnread && forceAllRead);
         boolean unreadStatusIsForced = forceAllUnread || forceAllRead;
         boolean forcedUnreadStatus = forceAllUnread;

         // Measure each fragment. It's ok to iterate over the entire set of fragments because it is
         // never a long list, even if there are many senders.
         final Map<Integer, Integer> priorityToLength = sPriorityToLength;
         priorityToLength.clear();

         int maxFoundPriority = Integer.MIN_VALUE;
         int numMessages = 0;
         int numDrafts = 0;
         CharSequence draftsFragment = "";
         CharSequence sendingFragment = "";
         CharSequence sendFailedFragment = "";

         sSenderListSplitter.setString(instructions);
         int numFragments = 0;
         String[] fragments = sSenderFragments;
         int currentSize = fragments.length;
         while (sSenderListSplitter.hasNext()) {
             fragments[numFragments++] = sSenderListSplitter.next();
             if (numFragments == currentSize) {
                 sSenderFragments = new String[2 * currentSize];
                 System.arraycopy(fragments, 0, sSenderFragments, 0, currentSize);
                 currentSize *= 2;
                 fragments = sSenderFragments;
             }
         }

         for (int i = 0; i < numFragments;) {
             String fragment0 = fragments[i++];
             if ("".equals(fragment0)) {
                 // This should be the final fragment.
             } else if (SENDER_LIST_TOKEN_ELIDED.equals(fragment0)) {
                 // ignore
             } else if (SENDER_LIST_TOKEN_NUM_MESSAGES.equals(fragment0)) {
                 numMessages = Integer.valueOf(fragments[i++]);
             } else if (SENDER_LIST_TOKEN_NUM_DRAFTS.equals(fragment0)) {
                 String numDraftsString = fragments[i++];
                 numDrafts = Integer.parseInt(numDraftsString);
                 draftsFragment = numDrafts == 1 ? draftString :
                         draftPluralString + " (" + numDraftsString + ")";
             } else if (SENDER_LIST_TOKEN_LITERAL.equals(fragment0)) {
                 senderBuilder.append(Html.fromHtml(fragments[i++]));
                 return;
             } else if (SENDER_LIST_TOKEN_SENDING.equals(fragment0)) {
                 sendingFragment = sendingString;
             } else if (SENDER_LIST_TOKEN_SEND_FAILED.equals(fragment0)) {
                 sendFailedFragment = sendFailedString;
             } else {
                 String priorityString = fragments[i++];
                 CharSequence nameString = fragments[i++];
                 if (nameString.length() == 0) nameString = meString;
                 int priority = Integer.parseInt(priorityString);
                 priorityToLength.put(priority, nameString.length());
                 maxFoundPriority = Math.max(maxFoundPriority, priority);
             }
         }
         String numMessagesFragment =
                 (numMessages != 0) ? " \u00A0" + Integer.toString(numMessages + numDrafts) : "";

         // Don't allocate fixedFragment unless we need it
         SpannableStringBuilder fixedFragment = null;
         int fixedFragmentLength = 0;
         if (draftsFragment.length() != 0 && allowDraft) {
             if (fixedFragment == null) {
                 fixedFragment = new SpannableStringBuilder();
             }
             fixedFragment.append(draftsFragment);
             if (draftsStyle != null) {
                 fixedFragment.setSpan(
                         CharacterStyle.wrap(draftsStyle),
                         0, fixedFragment.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
             }
         }
         if (sendingFragment.length() != 0) {
             if (fixedFragment == null) {
                 fixedFragment = new SpannableStringBuilder();
             }
             if (fixedFragment.length() != 0) fixedFragment.append(", ");
             fixedFragment.append(sendingFragment);
         }
         if (sendFailedFragment.length() != 0) {
             if (fixedFragment == null) {
                 fixedFragment = new SpannableStringBuilder();
             }
             if (fixedFragment.length() != 0) fixedFragment.append(", ");
             fixedFragment.append(sendFailedFragment);
         }

         if (fixedFragment != null) {
             fixedFragmentLength = fixedFragment.length();
         }
         maxChars -= fixedFragmentLength;

         final boolean normalMessagesExist =
                 numMessagesFragment.length() != 0 || maxFoundPriority != Integer.MIN_VALUE;
         int maxPriorityToInclude = -1; // inclusive
         int numCharsUsed = numMessagesFragment.length();
         int numSendersUsed = 0;
         while (maxPriorityToInclude < maxFoundPriority) {
             if (priorityToLength.containsKey(maxPriorityToInclude + 1)) {
                 int length = numCharsUsed + priorityToLength.get(maxPriorityToInclude + 1);
                 if (numCharsUsed > 0) length += 2;
                 // We must show at least two senders if they exist. If we don't have space for both
                 // then we will truncate names.
                 if (length > maxChars && numSendersUsed >= 2) {
                     break;
                 }
                 numCharsUsed = length;
                 numSendersUsed++;
             }
             maxPriorityToInclude++;
         }

         int numCharsToRemovePerWord = 0;
         if (numCharsUsed > maxChars) {
             numCharsToRemovePerWord = (numCharsUsed - maxChars) / numSendersUsed;
         }

         String lastFragment = null;
         CharacterStyle lastStyle = null;
         for (int i = 0; i < numFragments;) {
             String fragment0 = fragments[i++];
             if ("".equals(fragment0)) {
                 // This should be the final fragment.
             } else if (SENDER_LIST_TOKEN_ELIDED.equals(fragment0)) {
                 if (lastFragment != null) {
                     addStyledFragment(senderBuilder, lastFragment, lastStyle, false);
                     senderBuilder.append(" ");
                     addStyledFragment(senderBuilder, "..", lastStyle, true);
                     senderBuilder.append(" ");
                 }
                 lastFragment = null;
             } else if (SENDER_LIST_TOKEN_NUM_MESSAGES.equals(fragment0)) {
                 i++;
             } else if (SENDER_LIST_TOKEN_NUM_DRAFTS.equals(fragment0)) {
                 i++;
             } else if (SENDER_LIST_TOKEN_SENDING.equals(fragment0)) {
             } else if (SENDER_LIST_TOKEN_SEND_FAILED.equals(fragment0)) {
             } else {
                 final String unreadString = fragment0;
                 final String priorityString = fragments[i++];
                 String nameString = fragments[i++];
                 if (nameString.length() == 0) {
                     nameString = meString.toString();
                 } else {
                     nameString = Html.fromHtml(nameString).toString();
                 }
                 if (numCharsToRemovePerWord != 0) {
                     nameString = nameString.substring(
                             0, Math.max(nameString.length() - numCharsToRemovePerWord, 0));
                 }
                 final boolean unread = unreadStatusIsForced
                         ? forcedUnreadStatus : Integer.parseInt(unreadString) != 0;
                 final int priority = Integer.parseInt(priorityString);
                 if (priority <= maxPriorityToInclude) {
                     if (lastFragment != null && !lastFragment.equals(nameString)) {
                         addStyledFragment(
                                 senderBuilder, lastFragment.concat(","), lastStyle, false);
                         senderBuilder.append(" ");
                     }
                     lastFragment = nameString;
                     lastStyle = unread ? unreadStyle : readStyle;
                 } else {
                     if (lastFragment != null) {
                         addStyledFragment(senderBuilder, lastFragment, lastStyle, false);
                         // Adjacent spans can cause the TextView in Gmail widget
                         // confused and leads to weird behavior on scrolling.
                         // Our workaround here is to separate the spans by
                         // spaces.
                         senderBuilder.append(" ");
                         addStyledFragment(senderBuilder, "..", lastStyle, true);
                         senderBuilder.append(" ");
                     }
                     lastFragment = null;
                 }
             }
         }
         if (lastFragment != null) {
             addStyledFragment(senderBuilder, lastFragment, lastStyle, false);
         }
         senderBuilder.append(numMessagesFragment);
         if (fixedFragmentLength != 0) {
             statusBuilder.append(fixedFragment);
         }
     }

    /**
     * Adds a fragment with given style to a string builder.
     *
     * @param builder the current string builder
     * @param fragment the fragment to be added
     * @param style the style of the fragment
     * @param withSpaces whether to add the whole fragment or to divide it into
     *            smaller ones
     */
    private static void addStyledFragment(SpannableStringBuilder builder, String fragment,
            CharacterStyle style, boolean withSpaces) {
        if (withSpaces) {
            int pos = builder.length();
            builder.append(fragment);
            builder.setSpan(CharacterStyle.wrap(style), pos, builder.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            int start = 0;
            while (true) {
                int pos = fragment.substring(start).indexOf(' ');
                if (pos == -1) {
                    addStyledFragment(builder, fragment.substring(start), style, true);
                    break;
                } else {
                    pos += start;
                    if (start < pos) {
                        addStyledFragment(builder, fragment.substring(start, pos), style, true);
                        builder.append(' ');
                    }
                    start = pos + 1;
                    if (start >= fragment.length()) {
                        break;
                    }
                }
            }
        }
    }

     /**
      * Returns a boolean indicating whether the table UI should be shown.
      */
     public static boolean useTabletUI(Context context) {
         return context.getResources().getInteger(R.integer.use_tablet_ui) != 0;
     }

     /**
      * Try to make a date MIME(RFC 2822/5322)-compliant.
      *
      * It fixes:
      * - "Thu, 10 Dec 09 15:08:08 GMT-0700" to "Thu, 10 Dec 09 15:08:08 -0700"
      *   (4 digit zone value can't be preceded by "GMT")
      *   We got a report saying eBay sends a date in this format
      */
     public static String cleanUpMimeDate(String date) {
         if (TextUtils.isEmpty(date)) {
             return date;
         }
         date = DATE_CLEANUP_PATTERN_WRONG_TIMEZONE.matcher(date).replaceFirst("$1");
         return date;
     }

     public static String byteToHex(int b) {
         return byteToHex(new StringBuilder(), b).toString();
     }

     public static StringBuilder byteToHex(StringBuilder sb, int b) {
         b &= 0xFF;
         sb.append("0123456789ABCDEF".charAt(b >> 4));
         sb.append("0123456789ABCDEF".charAt(b & 0xF));
         return sb;
     }

     /**
      * Perform a simulated measure pass on the given child view, assuming the child has a ViewGroup
      * parent and that it should be laid out within that parent with a matching width but variable
      * height.
      *
      * Code largely lifted from AnimatedAdapter.measureChildHeight().
      *
      * @param child a child view that has already been placed within its parent ViewGroup
      * @param parent the parent ViewGroup of child
      * @return measured height of the child in px
      */
     public static int measureViewHeight(View child, ViewGroup parent) {
         int parentWSpec = MeasureSpec.makeMeasureSpec(parent.getWidth(), MeasureSpec.EXACTLY);
         int wSpec = ViewGroup.getChildMeasureSpec(parentWSpec,
                 parent.getPaddingLeft() + parent.getPaddingRight(),
                 ViewGroup.LayoutParams.MATCH_PARENT);
         int hSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
         child.measure(wSpec, hSpec);
         return child.getMeasuredHeight();
     }

    /**
     * Encode the string in HTML.
     *
     * @param removeEmptyDoubleQuotes If true, also remove any occurrence of ""
     *            found in the string
     */
     public static Object cleanUpString(String string, boolean removeEmptyDoubleQuotes) {
        return !TextUtils.isEmpty(string) ?
                TextUtils.htmlEncode(removeEmptyDoubleQuotes ? string.replace("\"\"", "")
                        : string)
                : "";
     }


     /**
      * Returns comma seperated strings as an array.
      */
     public static String[] splitCommaSeparatedString(String str) {
         return TextUtils.isEmpty(str) ? new String[0] : TextUtils.split(str, ",");
     }
}
