package com.android.mail.photomanager;

import com.android.mail.photomanager.BitmapUtil.InputStreamFactory;
import com.android.mail.providers.Attachment;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AttachmentRendition;
import com.android.mail.ui.DividedImageCanvas;
import com.android.mail.ui.ImageCanvas.Dimensions;
import com.android.mail.utils.Utils;
import com.google.common.base.Objects;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;

import com.android.mail.ui.ImageCanvas;
import com.android.mail.utils.LogUtils;
import com.google.common.primitives.Longs;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Asynchronously loads attachment image previews and maintains a cache of
 * photos.
 */
public class AttachmentPreviewsManager extends PhotoManager {

    private static final DefaultImageProvider sDefaultImageProvider
            = new AttachmentPreviewsDefaultProvider();
    private final Map<Object, AttachmentPreviewsManagerCallback> mCallbacks;

    public static int generateHash(ImageCanvas view, Object key) {
        return Objects.hashCode(view, key);
    }

    public static String transformKeyToUri(Object key) {
        return (String) ((Pair)key).second;
    }

    public AttachmentPreviewsManager(Context context) {
        super(context);
        mCallbacks = new HashMap<Object, AttachmentPreviewsManagerCallback>();
    }

    public void loadThumbnail(final PhotoIdentifier id, final ImageCanvas view,
            final Dimensions dimensions, final AttachmentPreviewsManagerCallback callback) {
        mCallbacks.put(id.getKey(), callback);
        super.loadThumbnail(id, view, dimensions);
    }

    @Override
    protected DefaultImageProvider getDefaultImageProvider() {
        return sDefaultImageProvider;
    }

    @Override
    protected int getHash(PhotoIdentifier id, ImageCanvas view) {
        return generateHash(view, id.getKey());
    }

    @Override
    protected PhotoLoaderThread getLoaderThread(ContentResolver contentResolver) {
        return new AttachmentPreviewsLoaderThread(contentResolver);
    }

    @Override
    protected void onImageDrawn(Request request, boolean success) {
        Object key = request.getKey();
        if (mCallbacks.containsKey(key)) {
            AttachmentPreviewsManagerCallback callback = mCallbacks.get(key);
            callback.onImageDrawn(request.getKey(), success);

            if (success) {
                mCallbacks.remove(key);
            }
        }
    }

    @Override
    protected void onImageLoadStarted(final Request request) {
        if (request == null) {
            return;
        }
        final Object key = request.getKey();
        if (mCallbacks.containsKey(key)) {
            AttachmentPreviewsManagerCallback callback = mCallbacks.get(key);
            callback.onImageLoadStarted(request.getKey());
        }
    }

    @Override
    protected boolean isSizeCompatible(int prevWidth, int prevHeight, int newWidth, int newHeight) {
        float ratio = (float) newWidth / prevWidth;
        boolean previousRequestSmaller = newWidth > prevWidth
                || newWidth > prevWidth * ratio
                || newHeight > prevHeight * ratio;
        return !previousRequestSmaller;
    }

    public static class AttachmentPreviewIdentifier extends PhotoIdentifier {
        public final String uri;
        public final int rendition;
        // conversationId and index used for sorting requests
        long conversationId;
        public int index;

        /**
         * <RENDITION, URI>
         */
        private Pair<Integer, String> mKey;

        public AttachmentPreviewIdentifier(String uri, int rendition, long conversationId,
                int index) {
            this.uri = uri;
            this.rendition = rendition;
            this.conversationId = conversationId;
            this.index = index;
            mKey = new Pair<Integer, String>(rendition, uri) {
                @Override
                public String toString() {
                    return "<" + first + ", " + second + ">";
                }
            };
        }

        @Override
        public boolean isValid() {
            return !TextUtils.isEmpty(uri) && rendition >= AttachmentRendition.SIMPLE;
        }

        @Override
        public Object getKey() {
            return mKey;
        }

        @Override
        public Object getKeyToShowInsteadOfDefault() {
            return new AttachmentPreviewIdentifier(uri, rendition - 1, conversationId, index)
                    .getKey();
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash = 31 * hash + (uri != null ? uri.hashCode() : 0);
            hash = 31 * hash + rendition;
            hash = 31 * hash + Longs.hashCode(conversationId);
            hash = 31 * hash + index;
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            AttachmentPreviewIdentifier that = (AttachmentPreviewIdentifier) o;

            if (rendition != that.rendition) {
                return false;
            }
            if (uri != null ? !uri.equals(that.uri) : that.uri != null) {
                return false;
            }
            if (conversationId != that.conversationId) {
                return false;
            }
            if (index != that.index) {
                return false;
            }

            return true;
        }

        @Override
        public String toString() {
            return mKey.toString();
        }

        @Override
        public int compareTo(PhotoIdentifier another) {
            if (another instanceof AttachmentPreviewIdentifier) {
                AttachmentPreviewIdentifier anotherId = (AttachmentPreviewIdentifier) another;
                // We want to load SIMPLE images first because they are super fast
                if (rendition - anotherId.rendition != 0) {
                    return rendition - anotherId.rendition;
                }

                // Load images from later messages first (later messages appear on top of the list)
                if (anotherId.conversationId - conversationId != 0) {
                    return (anotherId.conversationId - conversationId) > 0 ? 1 : -1;
                }

                // Load images from left to right
                if (index - anotherId.index != 0) {
                    return index - anotherId.index;
                }

                return 0;
            } else {
                return -1;
            }
        }
    }

    protected class AttachmentPreviewsLoaderThread extends PhotoLoaderThread {

        public AttachmentPreviewsLoaderThread(ContentResolver resolver) {
            super(resolver);
        }

        @Override
        protected int getMaxBatchCount() {
            return 1;
        }

        @Override
        protected Map<String, BitmapHolder> loadPhotos(final Collection<Request> requests) {
            final Map<String, BitmapHolder> photos = new HashMap<String, BitmapHolder>(
                    requests.size());

            LogUtils.d(TAG, "AttachmentPreviewsManager: starting batch load. Count: %d",
                    requests.size());
            for (final Request request : requests) {
                Utils.traceBeginSection("Setup load photo");
                final AttachmentPreviewIdentifier id = (AttachmentPreviewIdentifier) request
                        .getPhotoIdentifier();
                final Uri uri = Uri.parse(id.uri);
                // Get the attachment for this preview
                final Cursor cursor = getResolver()
                        .query(uri, UIProvider.ATTACHMENT_PROJECTION, null, null, null);
                if (cursor == null) {
                    Utils.traceEndSection();
                    continue;
                }
                Attachment attachment = null;
                try {
                    LogUtils.v(TAG, "AttachmentPreviewsManager: found %d attachments for uri %s",
                            cursor.getCount(), uri);
                    if (cursor.moveToFirst()) {
                        attachment = new Attachment(cursor);
                    }
                } finally {
                    cursor.close();
                }

                if (attachment == null) {
                    LogUtils.w(TAG, "AttachmentPreviewsManager: attachment not found for uri %s",
                            uri);
                    Utils.traceEndSection();
                    continue;
                }

                // Determine whether we load the SIMPLE or BEST image for this preview
                final Uri contentUri;
                if (id.rendition == UIProvider.AttachmentRendition.BEST) {
                    contentUri = attachment.contentUri;
                } else if (id.rendition == AttachmentRendition.SIMPLE) {
                    contentUri = attachment.thumbnailUri;
                } else {
                    LogUtils.w(TAG,
                            "AttachmentPreviewsManager: Cannot load rendition %d for uri %s",
                            id.rendition, uri);
                    Utils.traceEndSection();
                    continue;
                }

                LogUtils.v(TAG, "AttachmentPreviewsManager: attachments has contentUri %s",
                        contentUri);
                final InputStreamFactory factory = new InputStreamFactory() {
                    @Override
                    public InputStream newInputStream() {
                        try {
                            return getResolver().openInputStream(contentUri);
                        } catch (FileNotFoundException e) {
                            LogUtils.e(TAG,
                                    "AttachmentPreviewsManager: file not found for attachment %s."
                                            + " This may be due to the attachment not being "
                                            + "downloaded yet. But this shouldn't happen because "
                                            + "we check the state of the attachment downloads "
                                            + "before attempting to load it.",
                                    contentUri);
                            return null;
                        }
                    }
                };
                Utils.traceEndSection();

                Utils.traceBeginSection("Decode stream and crop");
                // todo:markwei read EXIF data for orientation
                // Crop it. I've seen that in real-world situations, a 5.5MB image will be
                // cropped down to about a 200KB image, so this is definitely worth it.
                final Bitmap bitmap = BitmapUtil
                        .decodeStreamWithCrop(factory, request.bitmapKey.w, request.bitmapKey.h,
                                0.5f, 1.0f / 3);
                Utils.traceEndSection();

                if (bitmap == null) {
                    LogUtils.w(TAG, "Unable to decode bitmap for contentUri %s", contentUri);
                    continue;
                }
                cacheBitmap(request.bitmapKey, bitmap);
                LogUtils.d(TAG,
                        "AttachmentPreviewsManager: finished loading attachment cropped size %db",
                        bitmap.getByteCount());
            }

            return photos;
        }
    }

    public static class AttachmentPreviewsDividedImageCanvas extends DividedImageCanvas {
        public AttachmentPreviewsDividedImageCanvas(Context context, InvalidateCallback callback) {
            super(context, callback);
        }

        @Override
        protected void drawVerticalDivider(int width, int height) {
            return; // do not draw vertical dividers
        }

        @Override
        protected boolean isPartialBitmapComplete() {
            return true; // images may not be loaded at the same time
        }

        @Override
        protected String transformKeyToDivisionId(Object key) {
            return transformKeyToUri(key);
        }
    }

    public static class AttachmentPreviewsDefaultProvider implements DefaultImageProvider {

        /**
         * All we need to do is clear the section. The ConversationItemView will draw the
         * progress bar.
         */
        @Override
        public void applyDefaultImage(PhotoIdentifier id, ImageCanvas view, int extent) {
            AttachmentPreviewsDividedImageCanvas dividedImageCanvas
                    = (AttachmentPreviewsDividedImageCanvas) view;
            dividedImageCanvas.clearDivisionImage(id.getKey());
        }
    }

    public interface AttachmentPreviewsManagerCallback {

        public void onImageDrawn(Object key, boolean success);

        public void onImageLoadStarted(Object key);
    }
}
