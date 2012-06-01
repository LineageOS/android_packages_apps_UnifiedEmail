/*
 * Copyright (C) 2011 Google Inc.
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

package com.android.mail.photo.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayInputStream;

/**
 * A GIF Drawable with support for animations.
 *
 * Inspired by http://code.google.com/p/android-gifview/
 */
public class GifDrawable extends Drawable implements Runnable, Animatable {

    private static final String TAG = "GifDrawable";

    // Run the animation at the most at 60 frames per second
    private static final int MIN_FRAME_DELAY = 15;

    // Max decoder pixel stack size
    private static final int MAX_STACK_SIZE = 4096;

    // Frame disposal methods
    private static final int DISPOSAL_METHOD_UNKNOWN = 0;
    private static final int DISPOSAL_METHOD_LEAVE = 1;
    private static final int DISPOSAL_METHOD_BACKGROUND = 2;
    private static final int DISPOSAL_METHOD_RESTORE = 3;

    private static final byte[] NETSCAPE2_0 = "NETSCAPE2.0".getBytes();


    private static Paint sPaint;
    private static Paint sScalePaint;

    private final ByteArrayInputStream mStream;

    private int mIntrinsicWidth;
    private int mIntrinsicHeight;
    private int mWidth;
    private int mHeight;

    private Bitmap mBitmap;
    private int[] mColors;
    private boolean mScale;
    private float mScaleFactor;

    private Bitmap mFirstFrame;

    private boolean mError;

    private byte[] mColorTableBuffer = new byte[256 * 3];
    private int[] mGlobalColorTable = new int[256];
    private boolean mGlobalColorTableUsed;
    private boolean mLocalColorTableUsed;
    private int mGlobalColorTableSize;
    private int mLocalColorTableSize;
    private int[] mLocalColorTable;
    private int[] mActiveColorTable;
    private int mBackgroundIndex;
    private int mBackgroundColor;
    private boolean mInterlace;
    private int mFrameX, mFrameY, mFrameWidth, mFrameHeight;
    private byte[] mBlock = new byte[256];
    private int mBlockSize;
    private int mDisposalMethod = DISPOSAL_METHOD_BACKGROUND;
    private boolean mTransparency;
    private int mTransparentColorIndex;

    // LZW decoder working arrays
    private short[] mPrefix = new short[MAX_STACK_SIZE];
    private byte[] mSuffix = new byte[MAX_STACK_SIZE];
    private byte[] mPixelStack = new byte[MAX_STACK_SIZE + 1];
    private byte[] mPixels;

    private boolean mBackupSaved;
    private int[] mBackup;

    private int mFrameCount;

    private boolean mRunning;
    private boolean mDone;
    private int mFrameDelay;

    public GifDrawable(byte[] data) {
        mStream = new ByteArrayInputStream(data);
        readHeader();

        // Mark the position of the first image frame in the stream.
        mStream.mark(0);

        if (!mError) {
            mBitmap = Bitmap.createBitmap(mIntrinsicWidth, mIntrinsicHeight,
                    Bitmap.Config.ARGB_4444);

            int pixelCount = mIntrinsicWidth * mIntrinsicHeight;
            mColors = new int[pixelCount];
            mPixels = new byte[pixelCount];

            mWidth = mIntrinsicHeight;
            mHeight = mIntrinsicHeight;

            // Read the first frame
            readNextFrame();
        }

        if (sPaint == null) {
            sPaint = new Paint();
            sScalePaint = new Paint();
            sScalePaint.setFilterBitmap(true);
        }
    }

    public static boolean isGif(byte[] data) {
        return data.length >= 3 &&  data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
    }

    /**
     * Returns the bitmap for the first frame in the GIF image.
     */
    public Bitmap getFirstFrame() {
        if (mFirstFrame == null && !mError && mWidth > 0 && mHeight > 0) {
            if (mScale) {
                mFirstFrame = Bitmap.createBitmap(mWidth, mHeight,  Bitmap.Config.ARGB_4444);
                draw(new Canvas(mFirstFrame));
            } else {
                mFirstFrame = Bitmap.createBitmap(mColors, mIntrinsicWidth, mIntrinsicHeight,
                        Bitmap.Config.ARGB_4444);
            }
        }
        return mFirstFrame;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mWidth = bounds.width();
        mHeight =  bounds.height();
        mScale = mWidth != mIntrinsicWidth && mHeight != mIntrinsicHeight;
        if (mScale) {
            mScaleFactor = Math.max((float) mWidth / mIntrinsicWidth,
                    (float) mHeight / mIntrinsicHeight);
        }
        mFirstFrame = null;
        reset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) {
            if (changed || restart) {
                start();
            }
        } else {
            stop();
        }
        return changed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(Canvas canvas) {
        if (mError || mWidth == 0 || mHeight == 0) {
            return;
        }

        if (mScale) {
            canvas.save();
            canvas.scale(mScaleFactor, mScaleFactor, 0, 0);
            canvas.drawBitmap(mBitmap, 0, 0, sScalePaint);
            canvas.restore();
        } else {
            canvas.drawBitmap(mBitmap, 0, 0, sPaint);
        }

        if (!mRunning && !mDone) {
            start();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicWidth;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAlpha(int alpha) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRunning() {
        return mRunning;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (!isRunning()) {
            mRunning = true;
            run();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        if (isRunning()) {
            mRunning = false;
            unscheduleSelf(this);
        }
    }

    /**
     * Moves to the next frame.
     */
    @Override
    public void run() {
        // If the animation has been completed, see if we need to repeat it.
        if (mDone) {

            // Multiple frames - repeat
            if (mFrameCount > 1) {
                mDone = false;
                reset();
            } else {
                stop();
                return;
            }
        }

        // Compose all frames that follow each other with 0 delay.
        do {
            readNextFrame();
        } while (!mDone && mFrameDelay == 0 &&
                (mDisposalMethod == DISPOSAL_METHOD_UNKNOWN
                || mDisposalMethod == DISPOSAL_METHOD_LEAVE));

        if (mFrameDelay == 0) {
            mFrameDelay = MIN_FRAME_DELAY;
        }

        invalidateSelf();

        if (mRunning) {
            scheduleSelf(this, SystemClock.uptimeMillis() + mFrameDelay);
        } else {
            unscheduleSelf(this);
        }
    }

    /**
     * Restarts decoding the image from the beginning.
     */
    private void reset() {
        // Return to the position of the first image frame in the stream.
        mStream.reset();
        mBackupSaved = false;
        mFrameCount = 0;
        mDisposalMethod = DISPOSAL_METHOD_UNKNOWN;
    }

    /**
     * Reads GIF file header information.
     */
    private void readHeader() {
        boolean valid = read() == 'G';
        valid = valid && read() == 'I';
        valid = valid && read() == 'F';
        if (!valid) {
            mError = true;
            return;
        }

        // Skip the next three letter, which represent the variation of the GIF standard.
        read();
        read();
        read();

        readLogicalScreenDescriptor();

        if (mGlobalColorTableUsed && !mError) {
            readColorTable(mGlobalColorTable, mGlobalColorTableSize);
            mBackgroundColor = mGlobalColorTable[mBackgroundIndex];
        }
    }

    /**
     * Reads Logical Screen Descriptor
     */
    private void readLogicalScreenDescriptor() {
        // logical screen size
        mIntrinsicWidth = mFrameWidth = readShort();
        mIntrinsicHeight = mFrameHeight = readShort();
        // packed fields
        int packed = read();
        mGlobalColorTableUsed = (packed & 0x80) != 0; // 1 : global color table flag
        // 2-4 : color resolution - ignore
        // 5 : gct sort flag - ignore
        mGlobalColorTableSize = 2 << (packed & 7); // 6-8 : gct size
        mBackgroundIndex = read();
        read(); // pixel aspect ratio - ignore
    }

    /**
     * Reads color table as 256 RGB integer values
     *
     * @param ncolors int number of colors to read
     */
    private void readColorTable(int[] colorTable, int ncolors) {
        int nbytes = 3 * ncolors;
        int n = 0;
        try {
            n = mStream.read(mColorTableBuffer, 0, nbytes);
        } catch (Exception e) {
            Log.e(TAG, "Cannot read color table", e);
        }

        if (n < nbytes) {
            mError = true;
        } else {
            int i = 0;
            int j = 0;
            while (i < ncolors) {
                int r = mColorTableBuffer[j++] & 0xff;
                int g = mColorTableBuffer[j++] & 0xff;
                int b = mColorTableBuffer[j++] & 0xff;
                colorTable[i++] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    /**
     * Reads GIF content blocks.
     *
     * @return true if the next frame has been parsed successfully, false if EOF
     *         has been reached
     */
    private void readNextFrame() {
        disposeOfLastFrame();

        mDisposalMethod = DISPOSAL_METHOD_UNKNOWN;
        mTransparency = false;
        mFrameDelay = 0;
        mLocalColorTable = null;

        while (true) {
            int code = read();
            switch (code) {
                case 0x21: // Extension.  Extensions precede the corresponding image.
                    code = read();
                    switch (code) {
                        case 0xf9: // graphics control extension
                            readGraphicControlExt();
                            break;
                        case 0xff: // application extension
                            readBlock();
                            boolean netscape = true;
                            for (int i = 0; i < NETSCAPE2_0.length; i++) {
                                if (mBlock[i] != NETSCAPE2_0[i]) {
                                    netscape = false;
                                }
                            }
                            if (netscape) {
                                readNetscapeExtension();
                            } else {
                                skip(); // don't care
                            }
                            break;
                        case 0xfe:// comment extension
                            skip();
                            break;
                        case 0x01:// plain text extension
                            skip();
                            break;
                        default: // uninteresting extension
                            skip();
                    }
                    break;

                case 0x2C: // Image separator
                    readBitmap();
                    return;

                case 0x3b: // Terminator
                    mDone = true;
                    return;

                default:
                    mError = true;
                    return;
            }
        }
    }

    /**
     * Disposes of the previous frame.
     */
    private void disposeOfLastFrame() {
        switch (mDisposalMethod) {
            case DISPOSAL_METHOD_UNKNOWN:
            case DISPOSAL_METHOD_LEAVE:
                mBackupSaved = false;
                break;

            case DISPOSAL_METHOD_RESTORE:
                if (mBackupSaved) {
                    System.arraycopy(mBackup, 0, mColors, 0, mBackup.length);
                }
                break;

            case DISPOSAL_METHOD_BACKGROUND:
                mBackupSaved = false;

                // Fill last image rect area with background color
                int color = 0;
                if (!mTransparency) {
                    color = mBackgroundColor;
                }
                for (int i = 0; i < mFrameHeight; i++) {
                    int n1 = (mFrameY + i) * mIntrinsicWidth + mFrameX;
                    int n2 = n1 + mFrameWidth;
                    for (int k = n1; k < n2; k++) {
                        mColors[k] = color;
                    }
                }
                break;

        }
    }

    /**
     * Reads Graphics Control Extension values
     */
    private void readGraphicControlExt() {
        read(); // Block size, fixed

        int packed = read(); // Packed fields

        mDisposalMethod = (packed & 0x1c) >> 2;  // Disposal method
        mTransparency = (packed & 1) != 0;
        mFrameDelay = readShort() * 10; // Delay in milliseconds
        mTransparentColorIndex = read();

        read(); // Block terminator - ignore
    }

    /**
     * Reads Netscape extension to obtain iteration count
     */
    private void readNetscapeExtension() {
        do {
            readBlock();
        } while ((mBlockSize > 0) && !mError);
    }

    /**
     * Reads next frame image
     */
    private void readBitmap() {
        mFrameX = readShort(); // (sub)image position & size
        mFrameY = readShort();
        mFrameWidth = readShort();
        mFrameHeight = readShort();
        int packed = read();
        mLocalColorTableUsed = (packed & 0x80) != 0; // 1 - local color table flag interlace
        mLocalColorTableSize = (int) Math.pow(2, (packed & 0x07) + 1);

        // 3 - sort flag
        // 4-5 - reserved lctSize = 2 << (packed & 7); // 6-8 - local color
        // table size
        mInterlace = (packed & 0x40) != 0;
        if (mLocalColorTableUsed) {
            if (mLocalColorTable == null) {
                mLocalColorTable = new int[256];
            }
            readColorTable(mLocalColorTable, mLocalColorTableSize);
            mActiveColorTable = mLocalColorTable;
        } else {
            mActiveColorTable = mGlobalColorTable;
            if (mBackgroundIndex == mTransparentColorIndex) {
                mBackgroundColor = 0;
            }
        }
        int savedColor = 0;
        if (mTransparency) {
            savedColor = mActiveColorTable[mTransparentColorIndex];
            mActiveColorTable[mTransparentColorIndex] = 0;
        }

        if (mActiveColorTable == null) {
            mError = true;
        }

        if (mError) {
            return;
        }

        decodeBitmapData();

        skip();

        if (mError) {
            return;
        }

        if (mDisposalMethod == DISPOSAL_METHOD_RESTORE) {
            backupFrame();
        }

        populateImageData();

        if (mTransparency) {
            mActiveColorTable[mTransparentColorIndex] = savedColor;
        }

        mFrameCount++;
    }

    /**
     * Stores the relevant portion of the current frame so that it can be restored
     * before the next frame is rendered.
     */
    private void backupFrame() {
        if (mBackupSaved) {
            return;
        }

        if (mBackup == null) {
            mBackup = null;
            try {
                mBackup = new int[mColors.length];
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "GifDrawable.backupFrame threw an OOME", e);
            }
        }

        if (mBackup != null) {
            System.arraycopy(mColors, 0, mBackup, 0, mColors.length);
            mBackupSaved = true;
        }
    }

    /**
     * Decodes LZW image data into pixel array.
     */
    private void decodeBitmapData() {
        int nullCode = -1;
        int npix = mFrameWidth * mFrameHeight;

        // Initialize GIF data stream decoder.
        int dataSize = read();
        int clear = 1 << dataSize;
        int endOfInformation = clear + 1;
        int available = clear + 2;
        int oldCode = nullCode;
        int codeSize = dataSize + 1;
        int codeMask = (1 << codeSize) - 1;
        for (int code = 0; code < clear; code++) {
            mPrefix[code] = 0; // XXX ArrayIndexOutOfBoundsException
            mSuffix[code] = (byte) code;
        }

        // Decode GIF pixel stream.
        int datum = 0;
        int bits = 0;
        int count = 0;
        int first = 0;
        int top = 0;
        int pi = 0;
        int bi = 0;
        for (int i = 0; i < npix;) {
            if (top == 0) {
                if (bits < codeSize) {

                    // Load bytes until there are enough bits for a code.
                    if (count == 0) {

                        // Read a new data block.
                        count = readBlock();
                        if (count <= 0) {
                            break;
                        }
                        bi = 0;
                    }
                    datum += (mBlock[bi] & 0xff) << bits;
                    bits += 8;
                    bi++;
                    count--;
                    continue;
                }

                // Get the next code.
                int code = datum & codeMask;
                datum >>= codeSize;
                bits -= codeSize;

                // Interpret the code
                if ((code > available) || (code == endOfInformation)) {
                    break;
                }
                if (code == clear) {
                    // Reset decoder.
                    codeSize = dataSize + 1;
                    codeMask = (1 << codeSize) - 1;
                    available = clear + 2;
                    oldCode = nullCode;
                    continue;
                }
                if (oldCode == nullCode) {
                    mPixelStack[top++] = mSuffix[code];
                    oldCode = code;
                    first = code;
                    continue;
                }
                int inCode = code;
                if (code == available) {
                    mPixelStack[top++] = (byte) first;
                    code = oldCode;
                }
                while (code > clear) {
                    mPixelStack[top++] = mSuffix[code];
                    code = mPrefix[code];
                }
                first = mSuffix[code] & 0xff;

                // Add a new string to the string table,
                if (available >= MAX_STACK_SIZE) {
                    break;
                }

                mPixelStack[top++] = (byte) first;
                mPrefix[available] = (short) oldCode;
                mSuffix[available] = (byte) first;
                available++;
                if (((available & codeMask) == 0) && (available < MAX_STACK_SIZE)) {
                    codeSize++;
                    codeMask += available;
                }
                oldCode = inCode;
            }

            // Pop a pixel off the pixel stack.
            top--;
            mPixels[pi++] = mPixelStack[top];
            i++;
        }

        for (int i = pi; i < npix; i++) {
            mPixels[i] = 0; // clear missing pixels
        }
    }

    /**
     * Populates the color array with pixels for the next frame.
     */
    private void populateImageData() {

        // Copy each source line to the appropriate place in the destination
        int pass = 1;
        int inc = 8;
        int iline = 0;
        for (int i = 0; i < mFrameHeight; i++) {
            int line = i;
            if (mInterlace) {
                if (iline >= mFrameHeight) {
                    pass++;
                    switch (pass) {
                        case 2:
                            iline = 4;
                            break;
                        case 3:
                            iline = 2;
                            inc = 4;
                            break;
                        case 4:
                            iline = 1;
                            inc = 2;
                            break;
                        default:
                            break;
                    }
                }
                line = iline;
                iline += inc;
            }
            line += mFrameY;
            if (line < mIntrinsicHeight) {
                int k = line * mIntrinsicWidth;
                int dx = k + mFrameX; // start of line in dest
                int dlim = dx + mFrameWidth; // end of dest line
                if ((k + mIntrinsicWidth) < dlim) {
                    dlim = k + mIntrinsicWidth; // past dest edge
                }
                int sx = i * mFrameWidth; // start of line in source
                while (dx < dlim) {
                    // map color and insert in destination
                    int index = mPixels[sx++] & 0xff;
                    int c = mActiveColorTable[index];
                    if (c != 0) {
                        mColors[dx] = c;
                    }
                    dx++;
                }
            }
        }

        mBitmap.setPixels(mColors, 0, mIntrinsicWidth, 0, 0, mIntrinsicWidth, mIntrinsicHeight);
    }

    /**
     * Reads a single byte from the input stream.
     */
    private int read() {
        int curByte = 0;
        try {
            curByte = mStream.read();
        } catch (Exception e) {
            mError = true;
        }
        return curByte;
    }

    /**
     * Reads next variable length block from input.
     *
     * @return number of bytes stored in "buffer"
     */
    private int readBlock() {
        mBlockSize = read();
        int n = 0;
        if (mBlockSize > 0) {
            try {
                int count = 0;
                while (n < mBlockSize) {
                    count = mStream.read(mBlock, n, mBlockSize - n);
                    if (count == -1) {
                        break;
                    }
                    n += count;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (n < mBlockSize) {
                mError = true;
            }
        }
        return n;
    }

    /**
     * Reads next 16-bit value, LSB first
     */
    private int readShort() {
        // read 16-bit value, LSB first
        return read() | (read() << 8);
    }

    /**
     * Skips variable length blocks up to and including next zero length block.
     */
    private void skip() {
        do {
            readBlock();
        } while ((mBlockSize > 0) && !mError);
    }
}
