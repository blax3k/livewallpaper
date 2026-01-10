package com.example.livewallpaper.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches GL textures by resource id. Callers should call destroyAll when the GL context
 * is destroyed to release GPU resources.
 */
public class TextureManager {
    private static final String TAG = "TextureManager";
    private final Map<Integer, Integer> textureCache = new HashMap<>(); // resourceId -> textureId

    /**
     * uploads the texture to the gpu and returns the index it was bound to
     * @param context
     * @param resourceId
     * @return
     */
    public int getTexture(Context context, int resourceId) {
        if (textureCache.containsKey(resourceId)) {
            Log.d(TAG, "Texture for resourceId=" + resourceId + " found in cache, returning texId=" + textureCache.get(resourceId));
            return textureCache.get(resourceId);
        }

        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        if (textureIds[0] == 0) {
            Log.e(TAG, "Failed to generate texture ID for resourceId=" + resourceId);
            return 0;
        }

        // Decode the bitmap with proper options
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;  // Don't scale based on density

        // First pass: get dimensions without decoding the full bitmap
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), resourceId, options);

        // Calculate inSampleSize to scale down large textures
        // Cap texture dimensions at 1024x1024 to prevent GPU memory exhaustion
        final int MAX_TEXTURE_SIZE = 1024;
        int inSampleSize = 1;
        if (options.outHeight > MAX_TEXTURE_SIZE || options.outWidth > MAX_TEXTURE_SIZE) {
            final int halfHeight = options.outHeight / 2;
            final int halfWidth = options.outWidth / 2;
            while ((halfHeight / inSampleSize) >= MAX_TEXTURE_SIZE ||
                   (halfWidth / inSampleSize) >= MAX_TEXTURE_SIZE) {
                inSampleSize *= 2;
            }
            Log.d(TAG, "Large texture detected for resourceId=" + resourceId +
                  ": original=" + options.outWidth + "x" + options.outHeight +
                  ", scaling with inSampleSize=" + inSampleSize);
        }

        // Second pass: decode with scaling
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);

        if (bitmap == null) {
            Log.e(TAG, "Failed to decode resource " + resourceId + " - bitmap is null");
            GLES20.glDeleteTextures(1, textureIds, 0);
            return 0;
        }

        Log.d(TAG, "Successfully decoded resource " + resourceId + ", bitmap size: " + bitmap.getWidth() + "x" + bitmap.getHeight() + ", format: " + bitmap.getConfig());

        // Convert JPEG (which may not have alpha) to ARGB_8888 for consistent handling
        Bitmap textureBitmap = bitmap;
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            Log.d(TAG, "Converting bitmap from " + bitmap.getConfig() + " to ARGB_8888");
            textureBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            bitmap.recycle();
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);

        // Set texture parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // Upload texture data to GPU
        try {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error uploading texture data for resourceId=" + resourceId, e);
            GLES20.glDeleteTextures(1, textureIds, 0);
            textureBitmap.recycle();
            return 0;
        }

        // Check for GL errors
        int glError = GLES20.glGetError();
        if (glError != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "GL error after texImage2D for resourceId=" + resourceId + ": " + glError);
            GLES20.glDeleteTextures(1, textureIds, 0);
            textureBitmap.recycle();
            return 0;
        }

        textureBitmap.recycle();

        textureCache.put(resourceId, textureIds[0]);
        Log.d(TAG, "Texture uploaded to GPU for resourceId=" + resourceId + ", assigned texId=" + textureIds[0]);
        return textureIds[0];
    }

    public void destroyAll() {
        if (textureCache.isEmpty()) return;
        int[] ids = new int[textureCache.size()];
        int i = 0;
        for (int texId : textureCache.values()) {
            ids[i++] = texId;
        }
        GLES20.glDeleteTextures(ids.length, ids, 0);
        textureCache.clear();
        Log.d(TAG, "All textures deleted");
    }
}

