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
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import com.android.mail.analytics.AnalyticsTimer;
import com.google.android.mail.common.html.parser.HtmlDocument;
import com.google.android.mail.common.html.parser.HtmlTree;
import com.google.common.collect.Lists;

import java.util.LinkedList;
import java.util.List;

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

        protected final SpannableStringBuilder mBuilder = new SpannableStringBuilder();
        private final LinkedList<TagWrapper> mSeenTags = Lists.newLinkedList();

        @Override
        public void addNode(HtmlDocument.Node n, int nodeNum, int endNum) {
            if (n instanceof HtmlDocument.Text) {
                // If it's just string, let's append it
                String text = ((HtmlDocument.Text) n).getText();
                // First we eliminate any \n since they don't mean anything in html but can
                // mis-translate into actual new lines during the parsing process
                text = text.replaceAll("\n", "");
                mBuilder.append(text);
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
            final String tagName = tag.getName().toLowerCase();
            if (tagName.equals("br")) {
                mBuilder.append("\n");
            } else if (tagName.equals("p")) {
                if (mBuilder.length() > 0) {
                    // Paragraphs must have 2 new lines before itself (to "fake" margin)
                    appendTwoNewLinesIfApplicable();
                }
            } else if (tagName.equals("div")) {
                if (mBuilder.length() > 0) {
                    // div should be on a newline
                    appendOneNewLineIfApplicable();
                }
            }

            if (!tag.isSelfTerminating()) {
                // Add to the stack of tags needing closing tag
                mSeenTags.push(new TagWrapper(tag.getName().toLowerCase(), mBuilder.length(),
                        tag.getAttributes()));
            }
        }

        /**
         * Helper function to handle end tag
         */
        protected void handleEnd(HtmlDocument.EndTag tag) {
            final String tagName = tag.getName().toLowerCase();
            TagWrapper lastSeen;
            while ((lastSeen = mSeenTags.poll()) != null && !lastSeen.tagName.equals(tagName)) { }

            // Misformatted html, just ignore this tag
            if (lastSeen == null) {
                return;
            }

            final Object marker;
            if (tagName.equals("b")) {
                // BOLD
                marker = new StyleSpan(Typeface.BOLD);
            } else if (tagName.equals("i")) {
                // ITALIC
                marker = new StyleSpan(Typeface.ITALIC);
            } else if (tagName.equals("u")) {
                // UNDERLINE
                marker = new UnderlineSpan();
            } else if (tagName.equals("a")) {
                // A HREF
                String href = getAttributeValue(lastSeen.tagAttributes, "href");
                // Ignore this tag if it doesn't have a link
                if (href == null) {
                    return;
                }
                marker = new URLSpan(href);
            } else if (tagName.equals("blockquote")) {
                // BLOCKQUOTE
                marker = new QuoteSpan();
            } else if (tagName.equals("font")) {
                // FONT SIZE/COLOR/FACE, since this can insert more than one span
                // we special case it and return
                handleFont(lastSeen);
                return;
            } else {
                // These tags do not add new Spanned into the mBuilder
                if (tagName.equals("p")) {
                    // paragraphs should add 2 newlines after itself.
                    // TODO (bug): currently always append 2 new lines at end of text because the
                    // body is wrapped in a <p> tag. We should only append if there are more texts
                    // after.
                    appendTwoNewLinesIfApplicable();
                } else if (tagName.equals("div")) {
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
            String color = getAttributeValue(wrapper.tagAttributes, "color");
            if (color != null) {
                int c = Color.parseColor(color);
                if (c != -1) {
                    mBuilder.setSpan(new ForegroundColorSpan(c | 0xFF000000), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            // check font size
            String size = getAttributeValue(wrapper.tagAttributes, "size");
            if (size != null) {
                int i = Integer.parseInt(size);
                if (i != -1) {
                    // default text size for pinto SEEMS to be 2, let's just use this
                    mBuilder.setSpan(new RelativeSizeSpan(i / 2f), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            // check font typeface
            String face = getAttributeValue(wrapper.tagAttributes, "face");
            if (face != null) {
                String[] families = face.split(",");
                for (String family : families) {
                    mBuilder.setSpan(new TypefaceSpan(family.trim()), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        /**
         * Helper function to get the value of an attribute from a list of attributes.
         */
        private String getAttributeValue(List<HtmlDocument.TagAttribute> attributes, String key) {
            String value = null;
            for (HtmlDocument.TagAttribute attr : attributes) {
                if (attr.getName().equalsIgnoreCase(key)) {
                    value = attr.getValue();
                    break;
                }
            }
            return value;
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

        private static class TagWrapper {
            final String tagName;
            final int startIndex;
            final List<HtmlDocument.TagAttribute> tagAttributes;

            TagWrapper(String tagName, int startIndex,
                    List<HtmlDocument.TagAttribute> tagAttributes) {
                this.tagName = tagName;
                this.startIndex = startIndex;
                this.tagAttributes = tagAttributes;
            }
        }
    }
}
