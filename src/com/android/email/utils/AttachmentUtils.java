package com.android.email.utils;

import android.content.Context;

import com.android.email.R;

import java.text.DecimalFormat;

public class AttachmentUtils {
    private static final int KILO = 1024;
    private static final int MEGA = KILO * KILO;

    /**
     * @return A string suitable for display in bytes, kilobytes or megabytes
     *         depending on its size.
     */
    public static String convertToHumanReadableSize(Context context, long size) {
        if (size < KILO) {
            return size + context.getString(R.string.bytes);
        } else if (size < MEGA) {
            return (size / KILO) + context.getString(R.string.kilobytes);
        } else {
            DecimalFormat onePlace = new DecimalFormat("0.#");
            return onePlace.format((float) size / (float) MEGA)
                    + context.getString(R.string.megabytes);
        }
    }
}
