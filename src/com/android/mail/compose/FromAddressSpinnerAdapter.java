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
package com.android.mail.compose;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.mail.R;

/**
 * FromAddressSpinnerAdapter returns the correct spinner adapter for reply from
 * addresses based on device size.
 *
 * @author mindyp@google.com
 */
public class FromAddressSpinnerAdapter extends ArrayAdapter<String[]> {
    public static int REAL_ACCOUNT = 2;

    public static int ACCOUNT_DISPLAY = 0;

    public static int ACCOUNT_ADDRESS = 1;

    private LayoutInflater mInflater;

    private Spinner mSpinner;

    public FromAddressSpinnerAdapter(Context context) {
        super(context, R.layout.from_item, R.id.spinner_account_name);
    }

    protected LayoutInflater getInflater() {
        if (mInflater == null) {
            mInflater = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
        }
        return mInflater;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String[] fromItem = getItem(position);
        View fromEntry = getInflater().inflate(R.layout.from_item, null);
        ((TextView) fromEntry.findViewById(R.id.spinner_account_name))
                .setText(fromItem[ACCOUNT_ADDRESS]);
        return fromEntry;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        String[] fromItem = getItem(position);
        View fromEntry = getInflater().inflate(R.layout.from_dropdown_item, null);
        TextView acctName = ((TextView) fromEntry.
                findViewById(R.id.spinner_account_name));
        acctName.setText(fromItem[ACCOUNT_DISPLAY]);
        return fromEntry;
    }

    /**
     * Set the spinner this adapter for which this spinner is being used.
     *
     * @param spinner Spinner widget.
     */
    public void setSpinner(Spinner spinner) {
        mSpinner = spinner;
    }

    /**
     * Get the spinner associated with this adapter.
     *
     * @return Spinner widget.
     */
    public Spinner getSpinner() {
        return mSpinner;
    }

    public int getSelectedItemPosition() {
        return mSpinner != null ? mSpinner.getSelectedItemPosition() : -1;
    }
}
