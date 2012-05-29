/*
 * Copyright (C) 2011 Google Inc.
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

package com.android.mail.photo.util;

import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Useful FIFE image url manipulation routines.
 */
public class FIFEUtil {
    private static final Splitter SPLIT_ON_EQUALS = Splitter.on("=").omitEmptyStrings();

    private static final Splitter SPLIT_ON_SLASH = Splitter.on("/").omitEmptyStrings();

    private static final Joiner JOIN_ON_SLASH = Joiner.on("/");

    private static final Pattern FIFE_HOSTED_IMAGE_URL_RE = Pattern.compile("^((http(s)?):)?\\/\\/"
            + "((((lh[3-6]\\.((ggpht)|(googleusercontent)|(google)))"
            + "|([1-4]\\.bp\\.blogspot)|(bp[0-3]\\.blogger))\\.com)"
            + "|(www\\.google\\.com\\/visualsearch\\/lh))\\/");

    private static final String EMPTY_STRING = "";

    // The ImageUrlOptions path part index for legacy Fife image URLs.
    private static final int LEGACY_URL_PATH_OPTIONS_INDEX = 4;

    // Num of path parts a legacy Fife image base URL contains. A base URL
    // contains
    // no ImageUrlOptions nor a filename and is terminated by a slash.
    private static final int LEGACY_BASE_URL_NUM_PATH_PARTS = 4;
    // Number of path parts a legacy Fife image URL contains that has both
    // existing
    // ImageUrlOptions and a filename.
    private static final int LEGACY_WITH_OPTIONS_FILENAME = 5;

    // Maximum number of path parts a legacy Fife image URL can contain.
    private static final int LEGACY_URL_MAX_NUM_PATH_PARTS = 6;

    // Maximum number of path parts a content Fife image URL can contain.
    private static final int CONTENT_URL_MAX_NUM_PATH_PARTS = 1;

    /**
     * Add size options to the given url.
     *
     * @param size the image size
     * @param url the url to apply the options to
     * @param crop if {@code true}, crop the photo to the dimensions
     * @return a {@code Uri} containting the new image url with options.
     */
    public static String setImageUrlSize(int size, String url, boolean crop) {
        return setImageUrlSize(size, url, crop, false);
    }

    /**
     * Add size options to the given url.
     *
     * @param size the image size
     * @param url the url to apply the options to
     * @param crop if {@code true}, crop the photo to the dimensions
     * @param includeMetadata if {@code true}, the image returned by the URL will include meta data
     * @return a {@code Uri} containting the new image url with options.
     */
    public static String setImageUrlSize(int size, String url, boolean crop,
            boolean includeMetadata) {
        if (url == null || !isFifeHostedUrl(url)) {
            return url;
        }

        final StringBuffer options = new StringBuffer();
        options.append("s").append(size);
        options.append("-d");
        if (crop) {
            options.append("-c");
        }
        if (includeMetadata) {
            options.append("-I");
        }

        final Uri uri = setImageUrlOptions(options.toString(), url);
        final String returnUrl = makeUriString(uri);

        return returnUrl;
    }

    /**
     * Add size options to the given url.
     *
     * @param width the width of the image
     * @param height the height of the image
     * @param url the url to apply the options to
     * @param crop if {@code true}, crop the photo to the dimensions
     * @param includeMetadata if {@code true}, the image returned by the URL will include meta data
     * @return a {@code Uri} containting the new image url with options.
     */
    public static String setImageUrlSize(int width, int height, String url, boolean crop,
            boolean includeMetadata) {
        if (url == null || !isFifeHostedUrl(url)) {
            return url;
        }

        final StringBuffer options = new StringBuffer();
        options.append("w").append(width);
        options.append("-h").append(height);
        options.append("-d");
        if (crop) {
            options.append("-c");
        }
        if (includeMetadata) {
            options.append("-I");
        }

        final Uri uri = setImageUrlOptions(options.toString(), url);
        final String returnUrl = makeUriString(uri);

        return returnUrl;
    }

    /**
     * Workaround. When encoding FIFE URL with content image options, the default
     * implementation for Uri.toString() encodes the equals ['='] as "%3D". The
     * FIFE servers choke on this and return a 404.
     */
    private static String makeUriString(Uri uri) {
        final StringBuilder builder = new StringBuilder();

        final String scheme = uri.getScheme();
        if (scheme != null) {
            builder.append(scheme).append(':');
        }

        final String encodedAuthority = uri.getEncodedAuthority();
        if (encodedAuthority != null) {
            // Even if the authority is "", we still want to append "//".
            builder.append("//").append(encodedAuthority);
        }

        final String path = uri.getPath();
        final String encodedPath = Uri.encode(path, "/=");
        if (encodedPath != null) {
            builder.append(encodedPath);
        }

        final String encodedQuery = uri.getEncodedQuery();
        if (!TextUtils.isEmpty(encodedQuery)) {
            builder.append('?').append(encodedQuery);
        }

        final String encodedFragment = uri.getEncodedFragment();
        if (!TextUtils.isEmpty(encodedFragment)) {
            builder.append('#').append(encodedFragment);
        }

        return builder.toString();
    }

    /**
     * Add image url options to the given url.
     *
     * @param options the options to apply
     * @param url the url to apply the options to
     * @return a {@code Uri} containting the new image url with options.
     */
    public static Uri setImageUrlOptions(String options, String url) {
        return setImageUriOptions(options, Uri.parse(url));
    }

    /**
     * Add image url options to the given url.
     *
     * @param options the options to apply
     * @param uri the uri to apply the options to
     * @return a {@code Uri} containting the new image url with options.
     */
    public static Uri setImageUriOptions(String options, Uri uri) {
        List<String> components = newArrayList(SPLIT_ON_SLASH.split(uri.getPath()));

        // Delegate setting ImageUrlOptions based on the Fife image URL format
        // determined by the number of path parts the URL contains.
        int numParts = components.size();
        if (components.size() > 1 && components.get(0).equals("image")) {
            --numParts;
        }

        Uri modifiedUri;
        if (numParts >= LEGACY_BASE_URL_NUM_PATH_PARTS
                && numParts <= LEGACY_URL_MAX_NUM_PATH_PARTS) {
            modifiedUri = setLegacyImageUrlOptions(options, uri);
        } else if (numParts == CONTENT_URL_MAX_NUM_PATH_PARTS) {
            modifiedUri = setContentImageUrlOptions(options, uri);
        } else {
            // not a valid URI; don't modify anything
            modifiedUri = uri;
        }
        return modifiedUri;
    }

    /**
     * Gets image options from the given url.
     *
     * @param url the url to get the options for
     * @return the image options. or {@link #EMPTY_STRING} if options do not exist.
     */
    public static String getImageUrlOptions(String url) {
        return getImageUriOptions(Uri.parse(url));
    }

    /**
     * Gets image options from the given uri.
     *
     * @param uri the uri to get the options for
     * @return the image options. or {@link #EMPTY_STRING} if options do not exist.
     */
    public static String getImageUriOptions(Uri uri) {
        List<String> components = newArrayList(SPLIT_ON_SLASH.split(uri.getPath()));

        // Delegate setting ImageUrlOptions based on the Fife image URL format
        // determined by the number of path parts the URL contains.
        int numParts = components.size();
        if (components.size() > 1 && components.get(0).equals("image")) {
            --numParts;
        }

        final String options;
        if (numParts >= LEGACY_BASE_URL_NUM_PATH_PARTS
                && numParts <= LEGACY_URL_MAX_NUM_PATH_PARTS) {
            options = getLegacyImageUriOptions(uri);
        } else if (numParts == CONTENT_URL_MAX_NUM_PATH_PARTS) {
            options = getContentImageUriOptions(uri);
        } else {
            // not a valid URI; don't modify anything
            options = EMPTY_STRING;
        }
        return options;
    }

    /**
     * Checks if the host is a valid FIFE host.
     *
     * @param url an image url to check
     * @return {@code true} iff the url has a valid FIFE host
     */
    public static boolean isFifeHostedUrl(String url) {
        if (url == null) {
            return false;
        }

        Matcher matcher = FIFE_HOSTED_IMAGE_URL_RE.matcher(url);
        return matcher.find();
    }

    /**
     * Checks if the host is a valid FIFE host.
     *
     * @param uri an image url to check
     * @return {@code true} iff the url has a valid FIFE host
     */
    public static boolean isFifeHostedUri(Uri uri) {
        return isFifeHostedUrl(uri.toString());
    }

    /**
     * Add image url options to the given url.
     *
     * @param options the options to apply
     * @param url the url to apply the options to
     * @return a {@code Uri} containting the new image url with options.
     */
    private static Uri setLegacyImageUrlOptions(String options, Uri url) {
        String path = url.getPath();
        List<String> components = newArrayList(SPLIT_ON_SLASH.split(path));
        boolean hasImagePrefix = false;

        if (components.size() > 0 && components.get(0).equals("image")) {
            components.remove(0);
            hasImagePrefix = true;
        }

        int numParts = components.size();
        boolean isPathSlashTerminated = path.endsWith("/");
        boolean containsFilenameNoOptions =
                !isPathSlashTerminated && numParts == LEGACY_WITH_OPTIONS_FILENAME;
        boolean isBaseUrlFormat = numParts == LEGACY_BASE_URL_NUM_PATH_PARTS;

        // Make room for the options in the path components if no options previously existed.
        if (containsFilenameNoOptions) {
            components.add(components.get(LEGACY_URL_PATH_OPTIONS_INDEX));
        }

        if (isBaseUrlFormat) {
            components.add(options);
        } else {
            components.set(LEGACY_URL_PATH_OPTIONS_INDEX, options);
        }

        // Put back image component if was there before.
        if (hasImagePrefix) {
            components.add(0, "image");
        }

        // Terminate the new path with a slash if required.
        if (isPathSlashTerminated) {
            components.add("");
        }

        return url.buildUpon().path("/" + JOIN_ON_SLASH.join(components)).build();
    }

    /**
     * Add image url options to the given url.
     *
     * @param options the options to apply
     * @param url the url to apply the options to
     * @return a {@code Uri} containting the new image url with options.
     */
    private static Uri setContentImageUrlOptions(String options, Uri url) {
        List<String> splitPath = newArrayList(SPLIT_ON_EQUALS.split(url.getPath()));
        String path = splitPath.get(0) + "=" + options;

        return url.buildUpon().path(path).build();
    }

    /**
     * Gets image options from the given URI.
     *
     * @param uri the URI to get the options for
     * @return the image options. or {@link #EMPTY_STRING} if options do not exist.
     */
    private static String getLegacyImageUriOptions(Uri uri) {
        String path = uri.getPath();
        List<String> components = newArrayList(SPLIT_ON_SLASH.split(path));

        if (components.size() > 0 && components.get(0).equals("image")) {
            components.remove(0);
        }

        int numParts = components.size();
        boolean isPathSlashTerminated = path.endsWith("/");
        boolean containsFilenameNoOptions =
                !isPathSlashTerminated && numParts == LEGACY_WITH_OPTIONS_FILENAME;
        boolean isBaseUrlFormat = numParts == LEGACY_BASE_URL_NUM_PATH_PARTS;

        // No options in the URI
        if (containsFilenameNoOptions) {
            return EMPTY_STRING;
        }

        if (!isBaseUrlFormat) {
            return components.get(LEGACY_URL_PATH_OPTIONS_INDEX);
        }

        return EMPTY_STRING;
    }

    /**
     * Gets image options from the given URI.
     *
     * @param uri the URI to get the options for
     * @return the image options. or {@link #EMPTY_STRING} if options do not exist.
     */
    private static String getContentImageUriOptions(Uri uri) {
        List<String> splitPath = newArrayList(SPLIT_ON_EQUALS.split(uri.getPath()));
        return (splitPath.size() > 1) ? splitPath.get(1) : EMPTY_STRING;
    }

    // Private. Just a class full of static functions.
    private FIFEUtil() {
    }




    /*
     * The code below has been shamelessly copied from guava to avoid bringing in it's 700+K
     * library for just a few lines of code. This is <em>NOT</em> meant to provide a fully
     * functional replacement. It only provides enough functionality to modify FIFE URLs.
     */

    /**
     * Creates a <i>mutable</i> {@code ArrayList} instance containing the given
     * elements.
     */
    private static <E> ArrayList<E> newArrayList(Iterable<? extends E> elements) {
        // Let ArrayList's sizing logic work, if possible
        Iterator<? extends E> iterator = elements.iterator();
        ArrayList<E> list = new ArrayList<E>();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }

    /**
     * Joins pieces of text with a separator.
     */
    private static class Joiner {
        public static Joiner on(String separator) {
            return new Joiner(separator);
        }

        private final String separator;

        private Joiner(String separator) {
            this.separator = separator;
        }

        /**
         * Appends each of part, using the configured separator between each.
         */
        public final StringBuilder appendTo(StringBuilder builder, Iterable<?> parts) {
            Iterator<?> iterator = parts.iterator();
            if (iterator.hasNext()) {
                builder.append(toString(iterator.next()));
                while (iterator.hasNext()) {
                    builder.append(separator);
                    builder.append(toString(iterator.next()));
                }
            }
            return builder;
        }

        /**
         * Returns a string containing the string representation of each of
         * {@code parts}, using the previously configured separator between
         * each.
         */
        public final String join(Iterable<?> parts) {
            return appendTo(new StringBuilder(), parts).toString();
        }

        CharSequence toString(Object part) {
            return (part instanceof CharSequence) ? (CharSequence) part : part.toString();
        }
    }

    /**
     * Divides strings into substrings, by recognizing a separator (a.k.a. "delimiter").
     */
    static class Splitter {
        private final boolean omitEmptyStrings;
        private final Strategy strategy;

        private Splitter(Strategy strategy) {
            this(strategy, false);
        }

        private Splitter(Strategy strategy, boolean omitEmptyStrings) {
            this.strategy = strategy;
            this.omitEmptyStrings = omitEmptyStrings;
        }

        public static Splitter on(final String separator) {
            if (separator == null || separator.length() == 0) {
                throw new IllegalArgumentException("separator may not be empty or null");
            }

            return new Splitter(new Strategy() {
                @Override
                public SplittingIterator iterator(Splitter splitter, CharSequence toSplit) {
                    return new SplittingIterator(splitter, toSplit) {
                        @Override
                        public int separatorStart(int start) {
                            int delimeterLength = separator.length();

                            positions: for (
                                    int p = start, last = toSplit.length() - delimeterLength;
                                    p <= last;
                                    p++) {
                                for (int i = 0; i < delimeterLength; i++) {
                                    if (toSplit.charAt(i + p) != separator.charAt(i)) {
                                        continue positions;
                                    }
                                }
                                return p;
                            }
                            return -1;
                        }

                        @Override
                        public int separatorEnd(int separatorPosition) {
                            return separatorPosition + separator.length();
                        }
                    };
                }
            });
        }

        public Splitter omitEmptyStrings() {
            return new Splitter(strategy, true);
        }

        public Iterable<String> split(final CharSequence sequence) {
            return new Iterable<String>() {
                @Override
                public Iterator<String> iterator() {
                    return strategy.iterator(Splitter.this, sequence);
                }
            };
        }

        private interface Strategy {
            Iterator<String> iterator(Splitter splitter, CharSequence toSplit);
        }

        private abstract static class SplittingIterator extends AbstractIterator<String> {
            final CharSequence toSplit;
            final boolean omitEmptyStrings;

            abstract int separatorStart(int start);

            abstract int separatorEnd(int separatorPosition);

            int offset = 0;

            protected SplittingIterator(Splitter splitter, CharSequence toSplit) {
                this.omitEmptyStrings = splitter.omitEmptyStrings;
                this.toSplit = toSplit;
            }

            @Override
            protected String computeNext() {
                while (offset != -1) {
                    int start = offset;
                    int end;

                    int separatorPosition = separatorStart(offset);
                    if (separatorPosition == -1) {
                        end = toSplit.length();
                        offset = -1;
                    } else {
                        end = separatorPosition;
                        offset = separatorEnd(separatorPosition);
                    }

                    if (omitEmptyStrings && start == end) {
                        continue;
                    }

                    return toSplit.subSequence(start, end).toString();
                }
                return endOfData();
            }
        }

        private static abstract class AbstractIterator<T> implements Iterator<T> {
            State state = State.NOT_READY;

            enum State {
                READY, NOT_READY, DONE, FAILED,
            }

            T next;

            protected abstract T computeNext();

            protected final T endOfData() {
                state = State.DONE;
                return null;
            }

            @Override
            public final boolean hasNext() {
                if (state == State.FAILED) {
                    throw new IllegalStateException();
                }

                switch (state) {
                    case DONE:
                        return false;
                    case READY:
                        return true;
                    default:
                }
                return tryToComputeNext();
            }

            boolean tryToComputeNext() {
                state = State.FAILED; // temporary pessimism
                next = computeNext();
                if (state != State.DONE) {
                    state = State.READY;
                    return true;
                }
                return false;
            }

            @Override
            public final T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                state = State.NOT_READY;
                return next;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        }
    }
}
