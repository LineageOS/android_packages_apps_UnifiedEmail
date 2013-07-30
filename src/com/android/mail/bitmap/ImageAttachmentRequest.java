package com.android.mail.bitmap;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.android.bitmap.DecodeTask;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider;

import java.io.IOException;
import java.io.InputStream;

/**
 * A request object for image attachment previews.
 * <p>
 * All requests are the same size to promote bitmap reuse.
 */
public class ImageAttachmentRequest implements DecodeTask.Request {
    private final Context mContext;
    private final String mLookupUri;
    private final int mRendition;

    public ImageAttachmentRequest(Context context, String lookupUri, int rendition) {
        mContext = context;
        mLookupUri = lookupUri;
        mRendition = rendition;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof ImageAttachmentRequest)) {
            return false;
        }
        final ImageAttachmentRequest other = (ImageAttachmentRequest) o;
        return TextUtils.equals(mLookupUri, other.mLookupUri) && mRendition == other.mRendition;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash += 31 * hash + mLookupUri.hashCode();
        hash += 31 * hash + mRendition;
        return hash;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("[");
        sb.append(super.toString());
        sb.append(" uri=");
        sb.append(mLookupUri);
        sb.append(" rendition=");
        sb.append(mRendition);
        sb.append("]");
        return sb.toString();
    }

    /**
     * Returns true iff the other request is for the same attachment, and differs only in which
     * rendition is being requested.
     *
     */
    public boolean matches(ImageAttachmentRequest other) {
        return other != null && TextUtils.equals(mLookupUri, other.mLookupUri);
    }

    @Override
    public AssetFileDescriptor createFd() throws IOException {
        AssetFileDescriptor result = null;
        Cursor cursor = null;
        final ContentResolver cr = mContext.getContentResolver();
        try {
            cursor = cr.query(Uri.parse(mLookupUri), UIProvider.ATTACHMENT_PROJECTION, null, null,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final Attachment a = new Attachment(cursor);
                result = cr.openAssetFileDescriptor(a.getUriForRendition(mRendition), "r");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    @Override
    public InputStream createInputStream() {
        return null;
    }

}
