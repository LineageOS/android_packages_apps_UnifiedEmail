package com.android.bitmap;

public interface RefCountable {
    void acquireReference();
    void releaseReference();
    int getRefCount();
}
