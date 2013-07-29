package com.android.bitmap;

import android.graphics.Bitmap;

/**
 * A simple bitmap wrapper. Currently supports reference counting and logical width/height
 * (which may differ from a bitmap's reported width/height due to bitmap reuse).
 */
public class ReusableBitmap implements RefCountable {

    public final Bitmap bmp;
    private int mWidth;
    private int mHeight;
    private int mRefCount = 0;

    public ReusableBitmap(Bitmap bitmap) {
        bmp = bitmap;
    }

    public void setLogicalWidth(int w) {
        mWidth = w;
    }

    public void setLogicalHeight(int h) {
        mHeight = h;
    }

    public int getLogicalWidth() {
        return mWidth;
    }

    public int getLogicalHeight() {
        return mHeight;
    }

    public int getByteCount() {
        return bmp.getByteCount();
    }

    @Override
    public void acquireReference() {
        mRefCount++;
    }

    @Override
    public void releaseReference() {
        if (mRefCount == 0) {
            throw new IllegalStateException();
        }
        mRefCount--;
    }

    @Override
    public int getRefCount() {
        return mRefCount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("[");
        sb.append(super.toString());
        sb.append(" refCount=");
        sb.append(mRefCount);
        sb.append(" bmp=");
        sb.append(bmp);
        sb.append(" logicalW/H=");
        sb.append(mWidth);
        sb.append("/");
        sb.append(mHeight);
        if (bmp != null) {
            sb.append(" sz=");
            sb.append(bmp.getByteCount() >> 10);
            sb.append("KB");
        }
        sb.append("]");
        return sb.toString();
    }

}
