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
package com.android.email.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.android.email.R;

public class Utils {
    /**
     * longest extension we recognize is 4 characters (e.g. "html", "docx")
     */
    private static final int FILE_EXTENSION_MAX_CHARS = 4;

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

     /*    Gmail.getSenderSnippet(
                 senderInstructions, senderBuilder, statusBuilder, maxChars,
                 sUnreadStyleSpan,
                 sReadStyleSpan,
                 sDraftsStyleSpan,
                 sMeString,
                 sDraftSingularString, sDraftPluralString,
                 sSendingString, sSendFailedString,
                 forceAllUnread, forceAllRead, allowDraft);*/
     }

     /**
      * Returns a boolean indicating whether the table UI should be shown.
      */
     public static boolean useTabletUI(Context context) {
         return context.getResources().getInteger(R.integer.use_tablet_ui) != 0;
     }
}
