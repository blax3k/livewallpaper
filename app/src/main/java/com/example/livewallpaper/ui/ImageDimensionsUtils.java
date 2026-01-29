package com.example.livewallpaper.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * Utility class for getting drawable image dimensions without loading the full bitmap into memory.
 */
public class ImageDimensionsUtils {
    private static final String TAG = "ImageDimensionsUtils";

    /**
     * Get the dimensions of a drawable resource.
     * Uses BitmapFactory.Options.inJustDecodeBounds to avoid loading the full image.
     *
     * @param context the Android context
     * @param resourceId the drawable resource ID
     * @return an ImageDimensions object with width and height, or null if unable to load
     */
    public static ImageDimensions getImageDimensions(Context context, int resourceId) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(context.getResources(), resourceId, options);

            if (options.outWidth > 0 && options.outHeight > 0) {
                Log.d(TAG, "Image dimensions: " + options.outWidth + "x" + options.outHeight);
                return new ImageDimensions(options.outWidth, options.outHeight);
            } else {
                Log.w(TAG, "Failed to get dimensions for resourceId=" + resourceId);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting image dimensions for resourceId=" + resourceId, e);
            return null;
        }
    }

    /**
     * Simple data class to hold image dimensions.
     */
    public static class ImageDimensions {
        public final int width;
        public final int height;
        public final float aspectRatio;

        public ImageDimensions(int width, int height) {
            this.width = width;
            this.height = height;
            this.aspectRatio = (float) width / height;
        }

        @Override
        public String toString() {
            return width + "x" + height + " (aspect: " + aspectRatio + ")";
        }
    }
}
