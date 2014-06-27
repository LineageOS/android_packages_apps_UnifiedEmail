/**
 * Copyright (c) 2014, Google Inc.
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

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import com.android.mail.analytics.AnalyticsTimer;
import com.google.android.mail.common.base.CharMatcher;
import com.google.android.mail.common.html.parser.HTML;
import com.google.android.mail.common.html.parser.HTML4;
import com.google.android.mail.common.html.parser.HtmlDocument;
import com.google.android.mail.common.html.parser.HtmlTree;
import com.google.common.collect.Lists;

import java.util.LinkedList;

public class HtmlUtils {

    /**
     * Use our custom SpannedConverter to process the HtmlNode results from HtmlTree.
     * @param html
     * @return processed HTML as a Spanned
     */
    public static Spanned htmlToSpan(String html, HtmlTree.ConverterFactory factory) {
        AnalyticsTimer.getInstance().trackStart(AnalyticsTimer.COMPOSE_HTML_TO_SPAN);
        // Get the html "tree"
        final HtmlTree htmlTree = com.android.mail.utils.Utils.getHtmlTree(html);
        htmlTree.setConverterFactory(factory);
        final Spanned spanned = htmlTree.getSpanned();
        AnalyticsTimer.getInstance().logDuration(AnalyticsTimer.COMPOSE_HTML_TO_SPAN, true,
                "compose", "html_to_span", null);
        return spanned;
    }

    /**
     * Class that handles converting the html into a Spanned.
     * This class will only handle a subset of the html tags. Below is the full list:
     *   - bold
     *   - italic
     *   - underline
     *   - font size
     *   - font color
     *   - font face
     *   - a
     *   - blockquote
     *   - p
     *   - div
     */
    public static class SpannedConverter implements HtmlTree.Converter<Spanned> {
        // Pinto normal text size is 2 while normal for AbsoluteSizeSpan is 12.
        // So 6 seems to be the magic number here. Html.toHtml also uses 6 as divider.
        private static final int WEB_TO_ANDROID_SIZE_MULTIPLIER = 6;

        protected final SpannableStringBuilder mBuilder = new SpannableStringBuilder();
        private final LinkedList<TagWrapper> mSeenTags = Lists.newLinkedList();

        // [copied verbatim from private version in HtmlTree.java]
        //
        // White space characters that are collapsed as a single space.
        // Note that characters such as the non-breaking whitespace
        // and full-width spaces are not equivalent to the normal spaces.
        private static final String HTML_SPACE_EQUIVALENTS = " \n\r\t\f";

        @Override
        public void addNode(HtmlDocument.Node n, int nodeNum, int endNum) {
            if (n instanceof HtmlDocument.Text) {
                // If it's just string, let's append it
                // FIXME: implement proper space/newline/<pre> handling like
                // HtmlTree.PlainTextPrinter has.
                final String text = ((HtmlDocument.Text) n).getText();
                appendNormalText(text);
            } else if (n instanceof HtmlDocument.Tag) {
                handleStart((HtmlDocument.Tag) n);
            } else if (n instanceof HtmlDocument.EndTag) {
                handleEnd((HtmlDocument.EndTag) n);
            }
        }

        /**
         * Helper function to handle start tag
         */
        protected void handleStart(HtmlDocument.Tag tag) {
            // Special case these tags since they only affect the number of newlines
            HTML.Element element = tag.getElement();
            if (HTML4.BR_ELEMENT.equals(element)) {
                mBuilder.append("\n");
            } else if (HTML4.P_ELEMENT.equals(element)) {
                if (mBuilder.length() > 0) {
                    // Paragraphs must have 2 new lines before itself (to "fake" margin)
                    appendTwoNewLinesIfApplicable();
                }
            } else if (HTML4.DIV_ELEMENT.equals(element)) {
                if (mBuilder.length() > 0) {
                    // div should be on a newline
                    appendOneNewLineIfApplicable();
                }
            }

            if (!tag.isSelfTerminating()) {
                // Add to the stack of tags needing closing tag
                mSeenTags.push(new TagWrapper(tag, mBuilder.length()));
            }
        }

        /**
         * Helper function to handle end tag
         */
        protected void handleEnd(HtmlDocument.EndTag tag) {
            TagWrapper lastSeen;
            HTML.Element element = tag.getElement();
            while ((lastSeen = mSeenTags.poll()) != null && lastSeen.tag.getElement() != null &&
                    !lastSeen.tag.getElement().equals(element)) { }

            // Misformatted html, just ignore this tag
            if (lastSeen == null) {
                return;
            }

            final Object marker;
            if (HTML4.B_ELEMENT.equals(element)) {
                // BOLD
                marker = new StyleSpan(Typeface.BOLD);
            } else if (HTML4.I_ELEMENT.equals(element)) {
                // ITALIC
                marker = new StyleSpan(Typeface.ITALIC);
            } else if (HTML4.U_ELEMENT.equals(element)) {
                // UNDERLINE
                marker = new UnderlineSpan();
            } else if (HTML4.A_ELEMENT.equals(element)) {
                // A HREF
                HtmlDocument.TagAttribute attr = lastSeen.tag.getAttribute(HTML4.HREF_ATTRIBUTE);
                // Ignore this tag if it doesn't have a link
                if (attr == null) {
                    return;
                }
                marker = new URLSpan(attr.getValue());
            } else if (HTML4.BLOCKQUOTE_ELEMENT.equals(element)) {
                // BLOCKQUOTE
                marker = new QuoteSpan();
            } else if (HTML4.FONT_ELEMENT.equals(element)) {
                // FONT SIZE/COLOR/FACE, since this can insert more than one span
                // we special case it and return
                handleFont(lastSeen);
                return;
            } else {
                // These tags do not add new Spanned into the mBuilder
                if (HTML4.P_ELEMENT.equals(element)) {
                    // paragraphs should add 2 newlines after itself.
                    // TODO (bug): currently always append 2 new lines at end of text because the
                    // body is wrapped in a <p> tag. We should only append if there are more texts
                    // after.
                    appendTwoNewLinesIfApplicable();
                } else if (HTML4.DIV_ELEMENT.equals(element)) {
                    // div should add a newline before itself if it's not a newline
                    appendOneNewLineIfApplicable();
                }

                return;
            }

            final int start = lastSeen.startIndex;
            final int end = mBuilder.length();
            if (start != end) {
                mBuilder.setSpan(marker, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        /**
         * Helper function to handle end font tags
         */
        private void handleFont(TagWrapper wrapper) {
            final int start = wrapper.startIndex;
            final int end = mBuilder.length();

            // check font color
            HtmlDocument.TagAttribute attr = wrapper.tag.getAttribute(HTML4.COLOR_ATTRIBUTE);
            if (attr != null) {
                int c = Color.parseColor(attr.getValue());
                if (c != -1) {
                    mBuilder.setSpan(new ForegroundColorSpan(c | 0xFF000000), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            // check font size
            attr = wrapper.tag.getAttribute(HTML4.SIZE_ATTRIBUTE);
            if (attr != null) {
                int i = Integer.parseInt(attr.getValue());
                if (i != -1) {
                    mBuilder.setSpan(new AbsoluteSizeSpan(i * WEB_TO_ANDROID_SIZE_MULTIPLIER,
                            true /* use dip */), start, end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            // check font typeface
            attr = wrapper.tag.getAttribute(HTML4.FACE_ATTRIBUTE);
            if (attr != null) {
                String[] families = attr.getValue().split(",");
                for (String family : families) {
                    mBuilder.setSpan(new TypefaceSpan(family.trim()), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        private void appendOneNewLineIfApplicable() {
            if (mBuilder.length() == 0 || mBuilder.charAt(mBuilder.length() - 1) != '\n') {
                mBuilder.append("\n");
            }
        }

        private void appendTwoNewLinesIfApplicable() {
            appendOneNewLineIfApplicable();
            if (mBuilder.length() <= 1 || mBuilder.charAt(mBuilder.length() - 2) != '\n') {
                mBuilder.append("\n");
            }
        }

        @Override
        public int getPlainTextLength() {
            return mBuilder.length();
        }

        @Override
        public Spanned getObject() {
            return mBuilder;
        }

        protected void appendNormalText(String text) {
            // adapted from HtmlTree.PlainTextPrinter#appendNormalText(String)

            if (text.length() == 0) {
                return;
            }

            // Strip beginning and ending whitespace.
            text = CharMatcher.anyOf(HTML_SPACE_EQUIVALENTS).trimFrom(text);

            // Collapse whitespace within the text.
            text = CharMatcher.anyOf(HTML_SPACE_EQUIVALENTS).collapseFrom(text, ' ');

            mBuilder.append(text);
        }

        private static class TagWrapper {
            final HtmlDocument.Tag tag;
            final int startIndex;

            TagWrapper(HtmlDocument.Tag tag, int startIndex) {
                this.tag = tag;
                this.startIndex = startIndex;
            }
        }
    }
}
