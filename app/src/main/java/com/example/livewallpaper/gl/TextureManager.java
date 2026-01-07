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
            return textureCache.get(resourceId);
        }

        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        if (textureIds[0] == 0) {
            Log.e(TAG, "Failed to generate texture ID");
            return 0;
        }

        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId);
        if (bitmap == null) {
            Log.e(TAG, "Failed to decode resource " + resourceId);
            return 0;
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();

        textureCache.put(resourceId, textureIds[0]);
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

