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

function toggleQuotedText(e) {
    var toggleElement = e.target;
    var elidedTextElement = toggleElement.nextSibling;
    var isHidden = getComputedStyle(elidedTextElement).display == 'none';
    toggleElement.innerHTML = isHidden ? MSG_HIDE_ELIDED : MSG_SHOW_ELIDED;
    elidedTextElement.style.display = isHidden ? 'block' : 'none';
    measurePositions();
}

function collapseQuotedText() {
    var i;
    var elements = document.getElementsByClassName("elided-text");
    var elidedElement, toggleElement;
    for (i = 0; i < elements.length; i++) {
        elidedElement = elements[i];
        toggleElement = document.createElement("div");
        toggleElement.display = "mail-elided-text";
        toggleElement.innerHTML = MSG_SHOW_ELIDED;
        toggleElement.onclick = toggleQuotedText;
        elidedElement.parentNode.insertBefore(toggleElement, elidedElement);
    }
}

function shrinkWideMessages() {
    var i;
    var elements = document.getElementsByClassName("mail-message-content");
    var messageElement;
    var documentWidth = document.documentElement.offsetWidth;
    var scale;
    for (i = 0; i < elements.length; i++) {
        messageElement = elements[i];
        if (messageElement.scrollWidth > documentWidth) {
            scale = documentWidth / messageElement.scrollWidth;

            // TODO: 'zoom' is nice because it does a proper layout, but WebView seems to clamp the
            // minimum 'zoom' level.
            if (false) {
                // TODO: this alternative works well in Chrome but doesn't work in WebView.
                messageElement.style.webkitTransformOrigin = "left top";
                messageElement.style.webkitTransform = "scale(" + scale + ")";
                messageElement.style.height = (messageElement.offsetHeight * scale) + "px";
                messageElement.style.overflowX = "visible";
            } else {
                messageElement.style.zoom = documentWidth / messageElement.scrollWidth;
            }
        }
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
            attachImageLoadListener(image);
            // TODO: handle inline image attachments for all supported protocols
            if (!showImages) {
                blockImage(image);
            }
        }
    }
}

function attachImageLoadListener(imageElement) {
    // Reset the src attribute to the empty string because onload will only fire if the src
    // attribute is set after the onload listener.
    var originalSrc = imageElement.src;
    imageElement.src = '';
    imageElement.onload = measurePositions;
    imageElement.src = originalSrc;
}

function blockImage(imageElement) {
    var src = imageElement.src;
    if (src.indexOf("http://") == 0 || src.indexOf("https://") == 0) {
        imageElement.setAttribute(BLOCKED_SRC_ATTR, src);
        imageElement.src = "data:";
    }
}

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

// BEGIN Java->JavaScript handlers
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
// END Java->JavaScript handlers

collapseQuotedText();
hideUnsafeImages();
shrinkWideMessages();
measurePositions();

