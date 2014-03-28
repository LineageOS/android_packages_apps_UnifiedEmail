package com.android.mail.bitmap;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.Drawable;

import com.android.oldbitmap.BitmapCache;
import com.android.oldbitmap.ReusableBitmap;
import com.android.oldbitmap.DecodeTask.Request;
import com.android.mail.R;
import com.android.mail.bitmap.ContactResolver.ContactDrawableInterface;

public class AccountAvatarDrawable extends Drawable implements ContactDrawableInterface {

    private final BitmapCache mCache;
    private final ContactResolver mContactResolver;

    private ContactRequest mContactRequest;
    private ReusableBitmap mBitmap;
    private float mBorderWidth;
    private final Paint mBitmapPaint;
    private final Paint mBorderPaint;
    private final Matrix mMatrix;

    private static Bitmap DEFAULT_AVATAR = null;

    public AccountAvatarDrawable(final Resources res, final BitmapCache cache,
            final ContactResolver contactResolver) {
        mCache = cache;
        mContactResolver = contactResolver;
        mBitmapPaint = new Paint();
        mBitmapPaint.setAntiAlias(true);
        mBitmapPaint.setFilterBitmap(true);
        mBitmapPaint.setDither(true);

        mBorderPaint = new Paint();
        mBorderPaint.setColor(Color.TRANSPARENT);
        mBorderPaint.setStyle(Style.STROKE);
        mBorderPaint.setStrokeWidth(mBorderWidth);
        mBorderPaint.setAntiAlias(true);

        mBorderWidth = res.getDimensionPixelSize(R.dimen.avatar_border_width);
        mMatrix = new Matrix();

        if (DEFAULT_AVATAR == null) {
            DEFAULT_AVATAR = BitmapFactory.decodeResource(res, R.drawable.avatar_placeholder_gray);
        }
    }

    @Override
    public void draw(final Canvas canvas) {
        final Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }

        if (mBitmap != null && mBitmap.bmp != null) {
            // Draw sender image.
            drawBitmap(mBitmap.bmp, mBitmap.getLogicalWidth(), mBitmap.getLogicalHeight(), canvas);
        } else {
            // Draw the default image
            drawBitmap(DEFAULT_AVATAR, DEFAULT_AVATAR.getWidth(), DEFAULT_AVATAR.getHeight(),
                    canvas);
        }
    }

    /**
     * Draw the bitmap onto the canvas at the current bounds taking into account the current scale.
     */
    private void drawBitmap(final Bitmap bitmap, final int width, final int height,
            final Canvas canvas) {
        Rect bounds = getBounds();
        // Draw bitmap through shader first.
        BitmapShader shader = new BitmapShader(bitmap, TileMode.CLAMP,
                TileMode.CLAMP);
        mMatrix.reset();

        // Fit bitmap to bounds.
        float scale = Math.max((float) bounds.width() / width,
                (float) bounds.height() / height);
        mMatrix.postScale(scale, scale);

        // Translate bitmap to dst bounds.
        mMatrix.postTranslate(bounds.left, bounds.top);

        shader.setLocalMatrix(mMatrix);
        mBitmapPaint.setShader(shader);
        int oldAlpha = mBitmapPaint.getAlpha();
        mBitmapPaint.setAlpha((int) (oldAlpha * 1f));
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() / 2,
                mBitmapPaint);
        mBitmapPaint.setAlpha(oldAlpha);

        // Then draw the border.
        canvas.drawCircle(bounds.centerX(), bounds.centerY(),
                bounds.width() / 2f - mBorderWidth / 2, mBorderPaint);
    }

    @Override
    public void setAlpha(final int alpha) {
        mBitmapPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(final ColorFilter cf) {
        mBitmapPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    public void setDecodeDimensions(final int decodeWidth, final int decodeHeight) {
        mCache.setPoolDimensions(decodeWidth, decodeHeight);
    }

    public void unbind() {
        setImage(null);
    }

    public void bind(final String name, final String email) {
        setImage(new ContactRequest(name, email));
    }

    private void setImage(final ContactRequest contactRequest) {
        if (mContactRequest != null && mContactRequest.equals(contactRequest)) {
            return;
        }

        if (mBitmap != null) {
            mBitmap.releaseReference();
            mBitmap = null;
        }

        mContactResolver.remove(mContactRequest, this);
        mContactRequest = contactRequest;

        if (contactRequest == null) {
            invalidateSelf();
            return;
        }

        final ReusableBitmap cached = mCache.get(contactRequest, true /* incrementRefCount */);
        if (cached != null) {
            setBitmap(cached);
        } else {
            decode();
        }
    }

    private void setBitmap(final ReusableBitmap bmp) {
        if (mBitmap != null && mBitmap != bmp) {
            mBitmap.releaseReference();
        }
        mBitmap = bmp;
        invalidateSelf();
    }

    private void decode() {
        if (mContactRequest == null) {
            return;
        }
        // Add to batch.
        mContactResolver.add(mContactRequest, this);
    }

    @Override
    public void onDecodeComplete(final Request key, final ReusableBitmap result) {
        final ContactRequest request = (ContactRequest) key;
        // Remove from batch.
        mContactResolver.remove(request, this);
        if (request.equals(mContactRequest)) {
            setBitmap(result);
        } else {
            // if the requests don't match (i.e. this request is stale), decrement the
            // ref count to allow the bitmap to be pooled
            if (result != null) {
                result.releaseReference();
            }
        }
    }
}

