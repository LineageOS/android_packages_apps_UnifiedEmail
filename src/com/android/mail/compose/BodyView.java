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

package com.android.mail.compose;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;
import android.text.Editable;
import android.text.Html;
import android.text.SpanWatcher;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;

public class BodyView extends EditText {
    
    public static int numberOfLinesBeforeScrollingIsSet = 10;
    public static int nbLines = 0;
    
    public interface SelectionChangedListener {
        /**
         * @param start new selection start
         * @param end new selection end
         * @return true to suppress normal selection change processing
         */
        boolean onSelectionChanged(int start, int end);
    }

    private SelectionChangedListener mSelectionListener;

    public BodyView(Context c) {
        this(c, null);
        initBodyView();
    }

    public BodyView(Context c, AttributeSet attrs) {
        super(c, attrs);
        initBodyView();
    }
    
    public void initBodyView(){
        super.setSingleLine(false);
        super.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        super.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        super.setLines(5);
        
        // Use to check if the number of line is more then 10 and set weather the Input is Scrollable or not
        super.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if(before==0) {
                    nbLines = 0;
                    for (int i = 0; i < text.toString().length(); i++) {
                        if (text.toString().charAt(i) == '\n') {
                            nbLines++;
                        }
                    }
                }
                
                // cset weather the Input is Scrollable or not if the number of line is more then 10
                if (nbLines < numberOfLinesBeforeScrollingIsSet) {
                    isInputScrollable(true);
                }else{
                    isInputScrollable(false);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        super.setMovementMethod(ScrollingMovementMethod.getInstance());
        super.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
    }
    
    // Use to set if the Input is Scrollable or not
    public void isInputScrollable(boolean Boolean){
        super.setVerticalScrollBarEnabled(Boolean);
    }
    
    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if (mSelectionListener != null) {
            if (mSelectionListener.onSelectionChanged(selStart, selEnd)) {
                return;
            }
        }
        super.onSelectionChanged(selStart, selEnd);
    }

    public void setSelectionChangedListener(SelectionChangedListener l) {
        mSelectionListener = l;
    }

}
