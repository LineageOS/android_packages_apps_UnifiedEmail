package com.android.bitmap;

public class AltBitmapCache extends AltPooledCache<DecodeTask.Request, ReusableBitmap>
        implements BitmapCache {

    public AltBitmapCache(int targetSizeBytes) {
        super(targetSizeBytes);
    }

    @Override
    protected int sizeOf(ReusableBitmap value) {
        return value.getByteCount();
    }

}
