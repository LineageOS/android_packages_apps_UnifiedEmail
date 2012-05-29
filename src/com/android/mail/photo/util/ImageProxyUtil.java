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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Useful Image Proxy url manipulation routines.
 */
public class ImageProxyUtil {
    private static final Pattern PROXY_HOSTED_IMAGE_URL_RE =
            Pattern.compile("^(((http(s)?):)?\\/\\/"
            + "images(\\d)?-.+-opensocial\\.googleusercontent\\.com\\/gadgets\\/proxy\\?)");

    /** Default container, if we don't already have one */
    static final String DEFAULT_CONTAINER = "esmobile";

    static final String PROXY_DOMAIN_PREFIX = "images";
    static final String PROXY_DOMAIN_SUFFIX = "-opensocial.googleusercontent.com";
    static final String PROXY_PATH = "/gadgets/proxy";
    static final String PARAM_URL = "url";
    static final String PARAM_CONTAINER = "container";
    static final String PARAM_GADGET = "gadget";
    static final String PARAM_REWRITE_MIME = "rewriteMime";
    static final String PARAM_REFRESH = "refresh";
    static final String PARAM_HEIGHT = "resize_h";
    static final String PARAM_WIDTH = "resize_w";
    static final String PARAM_QUALITY = "resize_q";
    static final String PARAM_NO_EXPAND = "no_expand";
    static final String PARAM_FALLBACK_URL = "fallback_url";
    static final int PROXY_COUNT = 3;
    static int sProxyIndex;

    public static final int ORIGINAL_SIZE = -1;

    /**
     * Add size options to the given url.
     *
     * @param size the image size
     * @param url the url to apply the options to
     * @return a {@code Uri} containting the new image url with options.
     */
    public static String setImageUrlSize(int size, String url) {
        if (url == null) {
            return url;
        }

        final String proxyUrl;
        if (!isProxyHostedUrl(url)) {
            proxyUrl = createProxyUrl();
        } else {
            proxyUrl = url;
            url = null;
        }
        final Uri proxyUri = Uri.parse(proxyUrl);
        return setImageUrlSizeOptions(size, size, proxyUri, url).toString();
    }


    /**
     * Add size options to the given url.
     *
     * @param width the image width
     * @param height the image height
     * @param url the url to apply the options to
     * @return a {@code Uri} containting the new image url with options.
     */
    public static String setImageUrlSize(int width, int height, String url) {
        if (url == null) {
            return url;
        }

        final String proxyUrl;
        if (!isProxyHostedUrl(url)) {
            proxyUrl = createProxyUrl();
        } else {
            proxyUrl = url;
            url = null;
        }
        final Uri proxyUri = Uri.parse(proxyUrl);
        return setImageUrlSizeOptions(width, height, proxyUri, url).toString();
    }

    /**
     * Returns a default proxy URL.
     */
    private static String createProxyUrl() {
        StringBuffer proxy = new StringBuffer();
        proxy.append("http://")
            .append(PROXY_DOMAIN_PREFIX)
            .append(getNextProxyIndex())
            .append("-")
            .append(DEFAULT_CONTAINER)
            .append(PROXY_DOMAIN_SUFFIX)
            .append(PROXY_PATH);
        return proxy.toString();
    }

    /**
     * Returns the next proxy index.
     */
    private static synchronized int getNextProxyIndex() {
        int toReturn = ++sProxyIndex;
        sProxyIndex %= PROXY_COUNT;
        return toReturn;
    }

    /**
     * Add image url options to the given url.
     *
     * @param width the image width
     * @param height the image height
     * @param proxyUri the uri to apply the options to
     * @return a {@code Uri} containing the image url with the width and height set.
     */
    public static Uri setImageUrlSizeOptions(int width, int height, Uri proxyUri, String imageUrl) {
        Uri.Builder proxyUriBuilder;
        Uri newProxyUri;

        proxyUriBuilder = Uri.EMPTY.buildUpon();
        proxyUriBuilder.authority(proxyUri.getAuthority());
        proxyUriBuilder.scheme(proxyUri.getScheme());
        proxyUriBuilder.path(proxyUri.getPath());
        // Set these here to override any settings in the source proxy URI
        if (width != ORIGINAL_SIZE && height != ORIGINAL_SIZE) {
            proxyUriBuilder.appendQueryParameter(PARAM_WIDTH, Integer.toString(width));
            proxyUriBuilder.appendQueryParameter(PARAM_HEIGHT, Integer.toString(height));
            proxyUriBuilder.appendQueryParameter(PARAM_NO_EXPAND, "1");
        }

        newProxyUri = proxyUriBuilder.build();

        final Set<String> paramNames = getQueryParameterNames(proxyUri);
        for (String key : paramNames) {
            if (newProxyUri.getQueryParameter(key) != null) {
                continue;
            }

            proxyUriBuilder = newProxyUri.buildUpon();
            if (PARAM_URL.equals(key)) {
                // Ensure there's only one url parameter
                proxyUriBuilder.appendQueryParameter(PARAM_URL,
                        proxyUri.getQueryParameter(PARAM_URL));

            } else if ((width == ORIGINAL_SIZE || height == ORIGINAL_SIZE) &&
                    (PARAM_WIDTH.equals(key) || PARAM_HEIGHT.equals(key) ||
                    PARAM_NO_EXPAND.equals(key))) {
                // Don't allow width / height / no-expand parameters if we ask for a full-size image
                continue;

            } else {
                final List<String> values = proxyUri.getQueryParameters(key);
                for (String value : values) {
                    proxyUriBuilder.appendQueryParameter(key, value);
                }
            }
            newProxyUri = proxyUriBuilder.build();
        }

        // The following parameters are mandatory; make sure the URL has them
        if (imageUrl != null && newProxyUri.getQueryParameter(PARAM_URL) == null) {
            proxyUriBuilder = newProxyUri.buildUpon();
            proxyUriBuilder.appendQueryParameter(PARAM_URL, imageUrl);
            newProxyUri = proxyUriBuilder.build();
        }
        if (newProxyUri.getQueryParameter(PARAM_CONTAINER) == null) {
            proxyUriBuilder = newProxyUri.buildUpon();
            proxyUriBuilder.appendQueryParameter(PARAM_CONTAINER, DEFAULT_CONTAINER);
            newProxyUri = proxyUriBuilder.build();
        }
        if (newProxyUri.getQueryParameter(PARAM_GADGET) == null) {
            proxyUriBuilder = newProxyUri.buildUpon();
            proxyUriBuilder.appendQueryParameter(PARAM_GADGET, "a");
            newProxyUri = proxyUriBuilder.build();
        }
        if (newProxyUri.getQueryParameter(PARAM_REWRITE_MIME) == null) {
            proxyUriBuilder = newProxyUri.buildUpon();
            proxyUriBuilder.appendQueryParameter(PARAM_REWRITE_MIME, "image/*");
            newProxyUri = proxyUriBuilder.build();
        }

        return newProxyUri;
    }

    /**
     * Backwards-compatible implementation of
     * {@link Uri#getQueryParameterNames()}.
     */
    private static Set<String> getQueryParameterNames(Uri uri) {
        if (uri.isOpaque()) {
            throw new UnsupportedOperationException("This isn't a hierarchical URI.");
        }

        String query = uri.getEncodedQuery();
        if (query == null) {
            return Collections.emptySet();
        }

        Set<String> names = new LinkedHashSet<String>();
        int start = 0;
        do {
            int next = query.indexOf('&', start);
            int end = (next == -1) ? query.length() : next;

            int separator = query.indexOf('=', start);
            if (separator > end || separator == -1) {
                separator = end;
            }

            String name = query.substring(start, separator);
            names.add(Uri.decode(name));

            // Move start to end of name.
            start = end + 1;
        } while (start < query.length());

        return Collections.unmodifiableSet(names);
    }

    /**
     * Checks if the host is a valid FIFE host.
     *
     * @param url an image url to check
     *
     * @return {@code true} iff the url has a valid FIFE host
     */
    public static boolean isProxyHostedUrl(String url) {
        if (url == null) {
            return false;
        }

        final Matcher matcher = PROXY_HOSTED_IMAGE_URL_RE.matcher(url);
        return matcher.find();
    }

    /**
     * Checks if the host is a valid FIFE host.
     *
     * @param uri an image url to check
     *
     * @return {@code true} iff the url has a valid FIFE host
     */
    public static boolean isProxyHostedUri(Uri uri) {
        return isProxyHostedUrl(uri.toString());
    }
}
