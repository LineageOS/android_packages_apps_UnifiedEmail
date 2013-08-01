package com.android.bitmap;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.AsyncTask;


import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes an image from either a file descriptor or input stream on a worker thread. After the
 * decode is complete, even if the task is cancelled, the result is placed in the given cache.
 * A {@link BitmapView} client may be notified on decode begin and completion.
 * <p>
 * This class uses {@link BitmapRegionDecoder} when possible to minimize unnecessary decoding
 * and allow bitmap reuse on Jellybean 4.1 and later.
 * <p>
 *  GIFs are supported, but their decode does not reuse bitmaps at all. The resulting
 *  {@link ReusableBitmap} will be marked as not reusable
 *  ({@link ReusableBitmap#isEligibleForPooling()} will return false).
 */
public class DecodeTask extends AsyncTask<Void, Void, ReusableBitmap> {

    private final Request mKey;
    private final int mDestW;
    private final int mDestH;
    private final int mDestBufferW;
    private final int mDestBufferH;
    private final BitmapView mView;
    private final BitmapCache mCache;
    private final BitmapFactory.Options mOpts = new BitmapFactory.Options();

    private ReusableBitmap mInBitmap = null;

    private static final boolean CROP_DURING_DECODE = true;

    public static final boolean DEBUG = false;

    /**
     * The decode task uses this class to get input to decode. You must implement at least one of
     * {@link #createFd()} or {@link #createInputStream()}. {@link DecodeTask} will prioritize
     * {@link #createFd()} before falling back to {@link #createInputStream()}.
     * <p>
     * When {@link DecodeTask} is used in conjunction with a {@link BitmapCache}, objects of this
     * type will also serve as cache keys to fetch cached data.
     */
    public interface Request {
        AssetFileDescriptor createFd() throws IOException;
        InputStream createInputStream() throws IOException;
    }

    /**
     * Callback interface for clients to be notified of decode state changes and completion.
     */
    public interface BitmapView {
        /**
         * Notifies that the async task's work is about to begin. Up until this point, the task
         * may have been preempted by the scheduler or queued up by a bottlenecked executor.
         * <p>
         * N.B. this method runs on the UI thread.
         *
         * @param key
         */
        void onDecodeBegin(Request key);
        void onDecodeComplete(Request key, ReusableBitmap result);
        void onDecodeCancel(Request key);
    }

    public DecodeTask(Request key, int w, int h, int bufferW, int bufferH, BitmapView view,
            BitmapCache cache) {
        mKey = key;
        mDestW = w;
        mDestH = h;
        mDestBufferW = bufferW;
        mDestBufferH = bufferH;
        mView = view;
        mCache = cache;
    }

    @Override
    protected ReusableBitmap doInBackground(Void... params) {
        if (isCancelled()) {
            return null;
        }

        // enqueue the 'onDecodeBegin' signal on the main thread
        publishProgress();

        ReusableBitmap result = null;
        AssetFileDescriptor fd = null;
        InputStream in = null;
        try {
            Trace.beginSection("create fd or stream");
            fd = mKey.createFd();
            if (fd == null) {
                in = mKey.createInputStream();
                if (in != null && !in.markSupported()) {
                    Trace.endSection();
                    throw new IllegalArgumentException("input stream must support reset()");
                }
            }
            Trace.endSection();

            Trace.beginSection("decodeBounds");
            mOpts.inJustDecodeBounds = true;
            if (fd != null) {
                BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, mOpts);
            } else {
                BitmapFactory.decodeStream(in, null, mOpts);
            }
            Trace.endSection();

            if (isCancelled()) {
                return null;
            }
            final int srcW = mOpts.outWidth;
            final int srcH = mOpts.outHeight;

            mOpts.inJustDecodeBounds = false;
            mOpts.inMutable = true;
            mOpts.inSampleSize = calculateSampleSize(srcW, srcH, mDestW, mDestH);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                Trace.beginSection("poll for reusable bitmap");
                mInBitmap = mCache.poll();
                Trace.endSection();
                if (mInBitmap == null) {
                    if (DEBUG) System.err.println(
                            "decode thread wants a bitmap. cache dump:\n" + mCache.toDebugString());
                    Trace.beginSection("create reusable bitmap");
                    mInBitmap = new ReusableBitmap(
                            Bitmap.createBitmap(mDestBufferW, mDestBufferH,
                                    Bitmap.Config.ARGB_8888));
                    Trace.endSection();
                    if (DEBUG) System.err.println("*** allocated new bitmap in decode thread: "
                            + mInBitmap + " key=" + mKey);
                } else {
                    if (DEBUG) System.out.println("*** reusing existing bitmap in decode thread: "
                            + mInBitmap + " key=" + mKey);

                }
                mOpts.inBitmap = mInBitmap.bmp;
            }

            if (isCancelled()) {
                return null;
            }

            Bitmap decodeResult = null;

            if (in != null) {
                in.reset();
            }
            final Rect srcRect = new Rect();
            if (CROP_DURING_DECODE) {
                try {
                    Trace.beginSection("decodeCropped" + mOpts.inSampleSize);
                    decodeResult = decodeCropped(fd, in, srcRect);
                } catch (IOException e) {
                    // fall through to below and try again with the non-cropping decoder
                    e.printStackTrace();
                } finally {
                    Trace.endSection();
                }
            }

            if (!CROP_DURING_DECODE || (decodeResult == null && !isCancelled())) {
                try {
                    Trace.beginSection("decode" + mOpts.inSampleSize);
                    // disable inBitmap-- bitmap reuse doesn't work well below K
                    if (mInBitmap != null) {
                        mCache.offer(mInBitmap);
                        mInBitmap = null;
                        mOpts.inBitmap = null;
                    }
                    decodeResult = decode(fd, in);
                } catch (IllegalArgumentException e) {
                    System.err.println("decode failed: reason='" + e.getMessage() + "' ss="
                            + mOpts.inSampleSize);

                    if (mOpts.inSampleSize > 1) {
                        // try again with ss=1
                        mOpts.inSampleSize = 1;
                        decodeResult = decode(fd, in);
                    }
                } finally {
                    Trace.endSection();
                }
            }

            if (!isCancelled() && decodeResult != null) {
                if (mInBitmap != null) {
                    result = mInBitmap;
                    // srcRect is non-empty when using the cropping BitmapRegionDecoder codepath
                    if (!srcRect.isEmpty()) {
                        result.setLogicalWidth((srcRect.right - srcRect.left) / mOpts.inSampleSize);
                        result.setLogicalHeight(
                                (srcRect.bottom - srcRect.top) / mOpts.inSampleSize);
                    } else {
                        result.setLogicalWidth(mOpts.outWidth);
                        result.setLogicalHeight(mOpts.outHeight);
                    }
                } else {
                    // no mInBitmap means no pooling
                    result = new ReusableBitmap(decodeResult, false /* reusable */);
                    result.setLogicalWidth(decodeResult.getWidth());
                    result.setLogicalHeight(decodeResult.getHeight());
                }
                // System.out.println("*** async task decoded fd=" + mUri +
                // " to" +
                // " sz=" + (decodeResult.getByteCount() >> 10) + "KB dstW/H=" +
                // result.getLogicalWidth() +
                // "/" + result.getLogicalHeight() + " srcW/H=" + srcW + "/" +
                // srcH + " ss=" +
                // mOpts.inSampleSize + " mutable=" + decodeResult.isMutable() +
                // " matchesInBitmap=" + (mOpts.inBitmap == decodeResult));
            } else {
                // System.out.println("*** async task cancelled decode of fd=" +
                // mUri);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fd != null) {
                try {
                    fd.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (result != null) {
                result.acquireReference();
                mCache.put(mKey, result);
                if (DEBUG) System.out.println("placed result in cache: key=" + mKey + " bmp="
                        + result + " cancelled=" + isCancelled());
            } else if (mInBitmap != null) {
                if (DEBUG) System.out.println("placing failed/cancelled bitmap in pool: key="
                        + mKey + " bmp=" + mInBitmap);
                mCache.offer(mInBitmap);
            }
        }
        return result;
    }

    private Bitmap decodeCropped(AssetFileDescriptor fd, InputStream in, Rect outSrcRect)
            throws IOException {
        final BitmapRegionDecoder brd;
        if (fd != null) {
            brd = BitmapRegionDecoder.newInstance(fd.getFileDescriptor(),
                    true /* shareable */);
        } else {
            brd = BitmapRegionDecoder.newInstance(in,
                    true /* shareable */);
        }
        if (isCancelled()) {
            brd.recycle();
            return null;
        }

        final int srcW = mOpts.outWidth;
        final int srcH = mOpts.outHeight;

        // Trace.beginSection("DecodeRegionGetDimens");
        // final int tmpw = brd.getWidth();
        // final int tmph = brd.getHeight();
        // Trace.endSection();

        // Center the decode on the top 1/3
        BitmapUtils.calculateCroppedSrcRect(srcW, srcH, mDestW, mDestH, mDestH, mOpts.inSampleSize,
                1f / 3, true /* absoluteFraction */, 1f, outSrcRect);
        if (DEBUG) System.out.println("rect for this decode is: " + outSrcRect
                + " srcW/H=" + srcW + "/" + srcH
                + " dstW/H=" + mDestW + "/" + mDestH);
        final Bitmap result = brd.decodeRegion(outSrcRect, mOpts);
        brd.recycle();
        return result;
    }

    private Bitmap decode(AssetFileDescriptor fd, InputStream in) {
        final Bitmap result;
        if (fd != null) {
            result = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, mOpts);
        } else {
            result = BitmapFactory.decodeStream(in, null, mOpts);
        }
        return result;
    }

    private static int calculateSampleSize(int srcW, int srcH, int destW, int destH) {
        int result;

        final float sz = Math.min((float) srcW / destW, (float) srcH / destH);

        // round to the nearest power of two, or just truncate
        final boolean stricter = true;

        if (stricter) {
            result = (int) Math.pow(2, (int) (0.5 + (Math.log(sz) / Math.log(2))));
        } else {
            result = (int) sz;
        }
        return Math.max(1, result);
    }

    public void cancel() {
        cancel(true);
        mOpts.requestCancelDecode();
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        mView.onDecodeBegin(mKey);
    }

    @Override
    public void onPostExecute(ReusableBitmap result) {
        mView.onDecodeComplete(mKey, result);
    }

    @Override
    protected void onCancelled(ReusableBitmap result) {
        mView.onDecodeCancel(mKey);
        if (result == null) {
            return;
        }

        result.releaseReference();
        if (mInBitmap == null) {
            // not reusing bitmaps: can recycle immediately
            result.bmp.recycle();
        }
    }

}
