package com.android.mail.ui;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.mail.R;
import com.android.mail.photo.util.ImageUtils;
import com.android.mail.providers.Attachment;
import com.android.mail.utils.LogUtils;

import java.io.IOException;

/**
 * Base class for attachment tiles that handles the work
 * of fetching and displaying the bitmaps for the tiles.
 */
public class AttachmentTile extends RelativeLayout {
    protected Attachment mAttachment;
    private ImageView mIcon;
    private ImageView.ScaleType mIconScaleType;
    private ThumbnailLoadTask mThumbnailTask;

    private static final String LOG_TAG = new LogUtils().getLogTag();

    /**
     * Returns true if the attachment should be rendered as a tile.
     * with a large image preview.
     * @param attachment the attachment to render
     * @return true if the attachment should be rendered as a tile
     */
    public static boolean isTiledAttachment(final Attachment attachment) {
        return ImageUtils.isImageMimeType(attachment.contentType);
    }

    public AttachmentTile(Context context) {
        this(context, null);
    }

    public AttachmentTile(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIcon = (ImageView) findViewById(R.id.attachment_tile_image);
        mIconScaleType = mIcon.getScaleType();
    }

    /**
     * Render or update an attachment's view. This happens immediately upon instantiation, and
     * repeatedly as status updates stream in, so only properties with new or changed values will
     * cause sub-views to update.
     *
     */
    public void render(Attachment attachment, Uri attachmentsListUri, int index) {
        if (attachment == null) {
            setVisibility(View.INVISIBLE);
            return;
        }

        final Attachment prevAttachment = mAttachment;
        mAttachment = attachment;

        LogUtils.d(LOG_TAG, "got attachment list row: name=%s state/dest=%d/%d dled=%d" +
                " contentUri=%s MIME=%s", attachment.name, attachment.state,
                attachment.destination, attachment.downloadedSize, attachment.contentUri,
                attachment.contentType);

        setupThumbnailPreview(attachment, prevAttachment);
    }

    private void setupThumbnailPreview(Attachment attachment, final Attachment prevAttachment) {
        final Uri imageUri = attachment.getImageUri();
        final Uri prevImageUri = (prevAttachment == null) ? null : prevAttachment.getImageUri();
        // begin loading a thumbnail if this is an image and either the thumbnail or the original
        // content is ready (and different from any existing image)
        if (imageUri != null && (prevImageUri == null || !imageUri.equals(prevImageUri))) {
            // cancel/dispose any existing task and start a new one
            if (mThumbnailTask != null) {
                mThumbnailTask.cancel(true);
            }
            mThumbnailTask = new ThumbnailLoadTask(mIcon.getWidth(), mIcon.getHeight());
            mThumbnailTask.execute(imageUri);
        } else if (imageUri == null) {
            // not an image, or no thumbnail exists. fall back to default.
            // async image load must separately ensure the default appears upon load failure.
            setThumbnailToDefault();
        }
    }

    private void setThumbnailToDefault() {
        mIcon.setImageResource(R.drawable.ic_menu_attachment_holo_light);
        mIcon.setScaleType(ImageView.ScaleType.CENTER);
    }

    private class ThumbnailLoadTask extends AsyncTask<Uri, Void, Bitmap> {

        private final int mWidth;
        private final int mHeight;

        public ThumbnailLoadTask(int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        @Override
        protected Bitmap doInBackground(Uri... params) {
            final Uri thumbnailUri = params[0];

            AssetFileDescriptor fd = null;
            Bitmap result = null;

            try {
                fd = getContext().getContentResolver().openAssetFileDescriptor(thumbnailUri, "r");
                if (isCancelled() || fd == null) {
                    return null;
                }

                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;

                BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, opts);
                if (isCancelled() || opts.outWidth == -1 || opts.outHeight == -1) {
                    return null;
                }

                opts.inJustDecodeBounds = false;

                LogUtils.d(LOG_TAG, "in background, src w/h=%d/%d dst w/h=%d/%d, divider=%d",
                        opts.outWidth, opts.outHeight, mWidth, mHeight, opts.inSampleSize);

                result = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, opts);

            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Unable to decode thumbnail %s", thumbnailUri);
            } finally {
                if (fd != null) {
                    try {
                        fd.close();
                    } catch (IOException e) {
                        LogUtils.e(LOG_TAG, e, "");
                    }
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result == null) {
                LogUtils.d(LOG_TAG, "back in UI thread, decode failed");
                setThumbnailToDefault();
                return;
            }

            LogUtils.d(LOG_TAG, "back in UI thread, decode success, w/h=%d/%d", result.getWidth(),
                    result.getHeight());
            mIcon.setImageBitmap(result);
            mIcon.setScaleType(mIconScaleType);
        }

    }
}
