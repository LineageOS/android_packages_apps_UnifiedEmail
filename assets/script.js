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

function measurePositions() {
    var messageTops;
    var i;
    var len;

    var headers = document.querySelectorAll(".mail-message");

    messageTops = new Array(headers.length);
    for (i = 0, len = headers.length; i < len; i++) {
        // addJavascriptInterface handler only supports string arrays
        messageTops[i] = "" + getTotalOffset(headers[i]).top;
    }

    window.mail.onWebContentGeometryChange(messageTops);
}

collapseQuotedText();
shrinkWideMessages();
measurePositions();

