/*
 * Copyright (C) 2012 Google Inc.
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

var BLOCKED_SRC_ATTR = "blocked-src";

/**
 * Returns the page offset of an element.
 *
 * @param {Element} element The element to return the page offset for.
 * @return {left: number, top: number} A tuple including a left and top value representing
 *     the page offset of the element.
 */
function getTotalOffset(el) {
    var result = {
        left: 0,
        top: 0
    };
    var parent = el;

    while (parent) {
        result.left += parent.offsetLeft;
        result.top += parent.offsetTop;
        parent = parent.offsetParent;
    }

    return result;
}

/**
 * Walks up the DOM starting at a given element, and returns an element that has the
 * specified class name or null.
 */
function up(el, className) {
    var parent = el;
    while (parent) {
        if (parent.classList && parent.classList.contains(className)) {
            break;
        }
        parent = parent.parentNode;
    }
    return parent || null;
}

function toggleQuotedText(e) {
    var toggleElement = e.target;
    var elidedTextElement = toggleElement.nextSibling;
    var isHidden = getComputedStyle(elidedTextElement).display == 'none';
    toggleElement.innerHTML = isHidden ? MSG_HIDE_ELIDED : MSG_SHOW_ELIDED;
    elidedTextElement.style.display = isHidden ? 'block' : 'none';

    // Revealing the elided text should normalize it to fit-width to prevent
    // this message from blowing out the conversation width.
    if (isHidden) {
        normalizeElementWidths([elidedTextElement]);
    }

    measurePositions();
}

function collapseAllQuotedText() {
    collapseQuotedText(document.documentElement);
}

function collapseQuotedText(elt) {
    var i;
    var elements = elt.getElementsByClassName("elided-text");
    var elidedElement, toggleElement;
    for (i = 0; i < elements.length; i++) {
        elidedElement = elements[i];
        toggleElement = document.createElement("div");
        toggleElement.className = "mail-elided-text";
        toggleElement.innerHTML = MSG_SHOW_ELIDED;
        toggleElement.onclick = toggleQuotedText;
        elidedElement.parentNode.insertBefore(toggleElement, elidedElement);
    }
}

function normalizeAllMessageWidths() {
    normalizeElementWidths(document.querySelectorAll(".expanded > .mail-message-content"));
}

/*
 * Normalizes the width of all elements supplied to the document body's overall width.
 * Narrower elements are zoomed in, and wider elements are zoomed out.
 * This method is idempotent.
 */
function normalizeElementWidths(elements) {
    var i;
    var el;
    var documentWidth = document.body.offsetWidth;
    var newZoom, oldZoom;

    for (i = 0; i < elements.length; i++) {
        el = elements[i];
        oldZoom = el.style.zoom;
        // reset any existing normalization
        if (oldZoom) {
            el.style.zoom = 1;
        }
        newZoom = documentWidth / el.scrollWidth;
        el.style.zoom = newZoom;
    }
}

function hideUnsafeImages() {
    var i, bodyCount;
    var j, imgCount;
    var body, image;
    var images;
    var showImages;
    var bodies = document.getElementsByClassName("mail-message-content");
    for (i = 0, bodyCount = bodies.length; i < bodyCount; i++) {
        body = bodies[i];
        showImages = body.classList.contains("mail-show-images");

        images = body.getElementsByTagName("img");
        for (j = 0, imgCount = images.length; j < imgCount; j++) {
            image = images[j];
            rewriteRelativeImageSrc(image);
            attachImageLoadListener(image);
            // TODO: handle inline image attachments for all supported protocols
            if (!showImages) {
                blockImage(image);
            }
        }
    }
}

/**
 * Changes relative paths to absolute path by pre-pending the account uri
 * @param {Element} imgElement Image for which the src path will be updated.
 */
function rewriteRelativeImageSrc(imgElement) {
    var src = imgElement.src;

    // DOC_BASE_URI will always be a unique x-thread:// uri for this particular conversation
    if (src.indexOf(DOC_BASE_URI) == 0 && (DOC_BASE_URI != CONVERSATION_BASE_URI)) {
        // The conversation specifies a different base uri than the document
        src = CONVERSATION_BASE_URI + src.substring(DOC_BASE_URI.length);
        imgElement.src = src;
    }
};


function attachImageLoadListener(imageElement) {
    // Reset the src attribute to the empty string because onload will only fire if the src
    // attribute is set after the onload listener.
    var originalSrc = imageElement.src;
    imageElement.src = '';
    imageElement.onload = imageOnLoad;
    imageElement.src = originalSrc;
}

function imageOnLoad(e) {
    // normalize the quoted text parent if we're in a quoted text block, or else
    // normalize the parent message content element
    var parent = up(e.target, "elided-text") || up(e.target, "mail-message-content");
    if (parent) {
        normalizeElementWidths([parent]);
    }
    measurePositions();
}

function blockImage(imageElement) {
    var src = imageElement.src;
    if (src.indexOf("http://") == 0 || src.indexOf("https://") == 0 ||
            src.indexOf("content://") == 0) {
        imageElement.setAttribute(BLOCKED_SRC_ATTR, src);
        imageElement.src = "data:";
    }
}

function setWideViewport() {
    var metaViewport = document.getElementById('meta-viewport');
    metaViewport.setAttribute('content', 'width=' + WIDE_VIEWPORT_WIDTH);
}

// BEGIN Java->JavaScript handlers
function measurePositions() {
    var overlayBottoms;
    var h;
    var i;
    var len;

    var expandedSpacerDivs = document.querySelectorAll(".expanded > .spacer");

    overlayBottoms = new Array(expandedSpacerDivs.length + 1);
    for (i = 0, len = expandedSpacerDivs.length; i < len; i++) {
        h = expandedSpacerDivs[i].offsetHeight;
        // addJavascriptInterface handler only supports string arrays
        overlayBottoms[i] = "" + (getTotalOffset(expandedSpacerDivs[i]).top + h);
    }
    // add an extra one to mark the bottom of the last message
    overlayBottoms[i] = "" + document.body.offsetHeight;

    window.mail.onWebContentGeometryChange(overlayBottoms);
}

function unblockImages(messageDomId) {
    var i, images, imgCount, image, blockedSrc;
    var msg = document.getElementById(messageDomId);
    if (!msg) {
        console.log("can't unblock, no matching message for id: " + messageDomId);
        return;
    }
    images = msg.getElementsByTagName("img");
    for (i = 0, imgCount = images.length; i < imgCount; i++) {
        image = images[i];
        blockedSrc = image.getAttribute(BLOCKED_SRC_ATTR);
        if (blockedSrc) {
            image.src = blockedSrc;
            image.removeAttribute(BLOCKED_SRC_ATTR);
        }
    }
}

function setConversationHeaderSpacerHeight(spacerHeight) {
    var spacer = document.getElementById("conversation-header");
    if (!spacer) {
        console.log("can't set spacer for conversation header");
        return;
    }
    spacer.style.height = spacerHeight + "px";
    measurePositions();
}

function setMessageHeaderSpacerHeight(messageDomId, spacerHeight) {
    var spacer = document.querySelector("#" + messageDomId + " > .mail-message-header");
    if (!spacer) {
        console.log("can't set spacer for message with id: " + messageDomId);
        return;
    }
    spacer.style.height = spacerHeight + "px";
    measurePositions();
}

function setMessageBodyVisible(messageDomId, isVisible, spacerHeight) {
    var i, len;
    var visibility = isVisible ? "block" : "none";
    var messageDiv = document.querySelector("#" + messageDomId);
    var collapsibleDivs = document.querySelectorAll("#" + messageDomId + " > .collapsible");
    if (!messageDiv || collapsibleDivs.length == 0) {
        console.log("can't set body visibility for message with id: " + messageDomId);
        return;
    }
    messageDiv.classList.toggle("expanded");
    for (i = 0, len = collapsibleDivs.length; i < len; i++) {
        collapsibleDivs[i].style.display = visibility;
    }

    // revealing new content should trigger width normalization, since the initial render
    // skips collapsed and super-collapsed messages
    if (isVisible) {
        normalizeElementWidths(messageDiv.getElementsByClassName("mail-message-content"));
    }

    setMessageHeaderSpacerHeight(messageDomId, spacerHeight);
}

function replaceSuperCollapsedBlock(startIndex) {
    var parent, block, header;

    block = document.querySelector(".mail-super-collapsed-block[index='" + startIndex + "']");
    if (!block) {
        console.log("can't expand super collapsed block at index: " + startIndex);
        return;
    }
    parent = block.parentNode;
    block.innerHTML = window.mail.getTempMessageBodies();

    header = block.firstChild;
    while (header) {
        parent.insertBefore(header, block);
        header = block.firstChild;
    }
    parent.removeChild(block);
    measurePositions();
}

function onContentReady(event) {
    window.mail.onContentReady();
}

function replaceMessageBodies(messageIds) {
    var i;
    var id;
    var msgContentDiv;

    for (i = 0, len = messageIds.length; i < len; i++) {
        id = messageIds[i];
        msgContentDiv = document.querySelector("#" + id + " > .mail-message-content");
        msgContentDiv.innerHTML = window.mail.getMessageBody(id);
        collapseQuotedText(msgContentDiv);
    }
}

// END Java->JavaScript handlers

window.onload = function() {
    // PAGE READINESS SIGNAL FOR JELLYBEAN AND NEWER
    // Notify the app on 'webkitAnimationStart' of a simple dummy element with a simple no-op
    // animation that immediately runs on page load. The app uses this as a signal that the
    // content is loaded and ready to draw, since WebView delays firing this event until the
    // layers are composited and everything is ready to draw.
    //
    // This code is conditionally enabled on JB+ by setting the 'initial-load' CSS class on this
    // dummy element.
    document.getElementById("initial-load-signal")
        .addEventListener("webkitAnimationStart", onContentReady, false);
    document.getElementById("initial-load-signal").style.webkitAnimationName = 'initial-load';
};

collapseAllQuotedText();
hideUnsafeImages();
normalizeAllMessageWidths();
//setWideViewport();
measurePositions();

