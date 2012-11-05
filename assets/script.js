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

// the set of Elements currently scheduled for processing in handleAllImageLoads
// this is an Array, but we treat it like a Set and only insert unique items
var gImageLoadElements = [];

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

function onToggleClick(e) {
    toggleQuotedText(e.target);
    measurePositions();
}

function toggleQuotedText(toggleElement) {
    var elidedTextElement = toggleElement.nextSibling;
    var isHidden = getComputedStyle(elidedTextElement).display == 'none';
    toggleElement.innerHTML = isHidden ? MSG_HIDE_ELIDED : MSG_SHOW_ELIDED;
    elidedTextElement.style.display = isHidden ? 'block' : 'none';

    // Revealing the elided text should normalize it to fit-width to prevent
    // this message from blowing out the conversation width.
    if (isHidden) {
        normalizeElementWidths([elidedTextElement]);
    }
}

function collapseAllQuotedText() {
    processQuotedText(document.documentElement, false /* showElided */);
}

function processQuotedText(elt, showElided) {
    var i;
    var elements = elt.getElementsByClassName("elided-text");
    var elidedElement, toggleElement;
    for (i = 0; i < elements.length; i++) {
        elidedElement = elements[i];
        toggleElement = document.createElement("div");
        toggleElement.className = "mail-elided-text";
        toggleElement.innerHTML = MSG_SHOW_ELIDED;
        toggleElement.onclick = onToggleClick;
        elidedElement.style.display = 'none';
        elidedElement.parentNode.insertBefore(toggleElement, elidedElement);
        if (showElided) {
            toggleQuotedText(toggleElement);
        }
    }
}

function isConversationEmpty(bodyDivs) {
    var i, len;
    var msgBody;
    var text;

    // Check if given divs are empty (in appearance), and disable zoom if so.
    for (i = 0, len = bodyDivs.length; i < len; i++) {
        msgBody = bodyDivs[i];
        // use 'textContent' to exclude markup when determining whether bodies are empty
        // (fall back to more expensive 'innerText' if 'textContent' isn't implemented)
        text = msgBody.textContent || msgBody.innerText;
        if (text.trim().length > 0) {
            return false;
        }
    }
    return true;
}

function normalizeAllMessageWidths() {
    var expandedBodyDivs;
    var metaViewport;
    var contentValues;
    var isEmpty;

    if (!NORMALIZE_MESSAGE_WIDTHS) {
        return;
    }

    expandedBodyDivs = document.querySelectorAll(".expanded > .mail-message-content");

    isEmpty = isConversationEmpty(expandedBodyDivs);

    normalizeElementWidths(expandedBodyDivs);

    // assemble a working <meta> viewport "content" value from the base value in the
    // document, plus any dynamically determined options
    metaViewport = document.getElementById("meta-viewport");
    contentValues = [metaViewport.getAttribute("content")];
    if (isEmpty) {
        contentValues.push(metaViewport.getAttribute("data-zoom-off"));
    } else {
        contentValues.push(metaViewport.getAttribute("data-zoom-on"));
    }
    metaViewport.setAttribute("content", contentValues.join(","));
}

/*
 * Normalizes the width of all elements supplied to the document body's overall width.
 * Narrower elements are zoomed in, and wider elements are zoomed out.
 * This method is idempotent.
 */
function normalizeElementWidths(elements) {
    var i;
    var el;
    var documentWidth;
    var newZoom, oldZoom;

    if (!NORMALIZE_MESSAGE_WIDTHS) {
        return;
    }

    documentWidth = document.body.offsetWidth;

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

function hideAllUnsafeImages() {
    hideUnsafeImages(document.getElementsByClassName("mail-message-content"));
}

function hideUnsafeImages(msgContentDivs) {
    var i, msgContentCount;
    var j, imgCount;
    var msgContentDiv, image;
    var images;
    var showImages;
    for (i = 0, msgContentCount = msgContentDivs.length; i < msgContentCount; i++) {
        msgContentDiv = msgContentDivs[i];
        showImages = msgContentDiv.classList.contains("mail-show-images");

        images = msgContentDiv.getElementsByTagName("img");
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

/**
 * Handle an onload event for an <img> tag.
 * The image could be within an elided-text block, or at the top level of a message.
 * When a new image loads, its new bounds may affect message or elided-text geometry,
 * so we need to inspect and adjust the enclosing element's zoom level where necessary.
 *
 * Because this method can be called really often, and zoom-level adjustment is slow,
 * we collect the elements to be processed and do them all later in a single deferred pass.
 */
function imageOnLoad(e) {
    // normalize the quoted text parent if we're in a quoted text block, or else
    // normalize the parent message content element
    var parent = up(e.target, "elided-text") || up(e.target, "mail-message-content");
    if (!parent) {
        // sanity check. shouldn't really happen.
        return;
    }

    // if there was no previous work, schedule a new deferred job
    if (gImageLoadElements.length == 0) {
        window.setTimeout(handleAllImageOnLoads, 0);
    }

    // enqueue the work if it wasn't already enqueued
    if (gImageLoadElements.indexOf(parent) == -1) {
        gImageLoadElements.push(parent);
    }
}

// handle all deferred work from image onload events
function handleAllImageOnLoads() {
    normalizeElementWidths(gImageLoadElements);
    measurePositions();
    // clear the queue so the next onload event starts a new job
    gImageLoadElements = [];
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

function restoreScrollPosition() {
    var scrollYPercent = window.mail.getScrollYPercent();
    if (scrollYPercent && document.body.offsetHeight > window.innerHeight) {
        document.body.scrollTop = Math.floor(scrollYPercent * document.body.offsetHeight);
    }
}

function onContentReady(event) {
    window.mail.onContentReady();
}

function setupContentReady() {
    var signalDiv;

    // PAGE READINESS SIGNAL FOR JELLYBEAN AND NEWER
    // Notify the app on 'webkitAnimationStart' of a simple dummy element with a simple no-op
    // animation that immediately runs on page load. The app uses this as a signal that the
    // content is loaded and ready to draw, since WebView delays firing this event until the
    // layers are composited and everything is ready to draw.
    //
    // This code is conditionally enabled on JB+ by setting the ENABLE_CONTENT_READY flag.
    if (ENABLE_CONTENT_READY) {
        signalDiv = document.getElementById("initial-load-signal");
        signalDiv.addEventListener("webkitAnimationStart", onContentReady, false);
    }
}

// BEGIN Java->JavaScript handlers
function measurePositions() {
    var overlayTops, overlayBottoms;
    var i;
    var len;

    var expandedBody, headerSpacer;
    var prevBodyBottom = 0;
    var expandedBodyDivs = document.querySelectorAll(".expanded > .mail-message-content");

    // N.B. offsetTop and offsetHeight of an element with the "zoom:" style applied cannot be
    // trusted.

    overlayTops = new Array(expandedBodyDivs.length + 1);
    overlayBottoms = new Array(expandedBodyDivs.length + 1);
    for (i = 0, len = expandedBodyDivs.length; i < len; i++) {
        expandedBody = expandedBodyDivs[i];
        headerSpacer = expandedBody.previousElementSibling;
        // addJavascriptInterface handler only supports string arrays
        overlayTops[i] = "" + prevBodyBottom;
        overlayBottoms[i] = "" + (getTotalOffset(headerSpacer).top + headerSpacer.offsetHeight);
        prevBodyBottom = getTotalOffset(expandedBody.nextElementSibling).top;
    }
    // add an extra one to mark the top/bottom of the last message footer spacer
    overlayTops[i] = "" + prevBodyBottom;
    overlayBottoms[i] = "" + document.body.offsetHeight;

    window.mail.onWebContentGeometryChange(overlayTops, overlayBottoms);
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
    var parent, block, msg;

    block = document.querySelector(".mail-super-collapsed-block[index='" + startIndex + "']");
    if (!block) {
        console.log("can't expand super collapsed block at index: " + startIndex);
        return;
    }
    parent = block.parentNode;
    block.innerHTML = window.mail.getTempMessageBodies();

    // process the new block contents in one go before we pluck them out of the common ancestor
    processQuotedText(block, false /* showElided */);
    hideUnsafeImages(block.getElementsByClassName("mail-message-content"));

    msg = block.firstChild;
    while (msg) {
        parent.insertBefore(msg, block);
        msg = block.firstChild;
    }
    parent.removeChild(block);
    measurePositions();
}

function replaceMessageBodies(messageIds) {
    var i;
    var id;
    var msgContentDiv;

    for (i = 0, len = messageIds.length; i < len; i++) {
        id = messageIds[i];
        msgContentDiv = document.querySelector("#" + id + " > .mail-message-content");
        msgContentDiv.innerHTML = window.mail.getMessageBody(id);
        processQuotedText(msgContentDiv, true /* showElided */);
        hideUnsafeImages([msgContentDiv]);
    }
    measurePositions();
}

// handle the special case of adding a single new message at the end of a conversation
function appendMessageHtml() {
    var msg = document.createElement("div");
    msg.innerHTML = window.mail.getTempMessageBodies();
    msg = msg.children[0];  // toss the outer div, it was just to render innerHTML into
    document.body.appendChild(msg);
    processQuotedText(msg, true /* showElided */);
    hideUnsafeImages(msg.getElementsByClassName("mail-message-content"));
    measurePositions();
}

// END Java->JavaScript handlers

// Do this first to ensure that the readiness signal comes through,
// even if a stray exception later occurs.
setupContentReady();

collapseAllQuotedText();
hideAllUnsafeImages();
normalizeAllMessageWidths();
//setWideViewport();
restoreScrollPosition();
measurePositions();

