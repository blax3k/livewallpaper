package com.example.livewallpaper.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Loads and caches GL textures by resource id. Callers should call destroyAll when the GL context
 * is destroyed to release GPU resources.
 */
public class TextureManager {
    private static final String TAG = "TextureManager";
    private final Map<Integer, Integer> textureCache = new HashMap<>(); // resourceId -> textureId
    private final Queue<Integer> freedTextureIds = new LinkedList<>(); // Pool of freed texture IDs for reuse
    private int maxTextureSize = 1024; // Default fallback, will be queried from GPU
    private boolean gpuLimitsQueried = false;

    // Async loading infrastructure
    private final Executor backgroundExecutor = Executors.newFixedThreadPool(2); // 2 threads for concurrent bitmap decoding
    private final Queue<PendingTextureUpload> pendingUploads = new LinkedBlockingQueue<>(); // Thread-safe queue for pending GPU uploads
    private final Map<Integer, Bitmap> pendingBitmaps = new ConcurrentHashMap<>(); // resourceId -> decoded bitmap (pending upload)
    private final Map<Integer, TextureLoadCallback> textureCallbacks = new ConcurrentHashMap<>(); // resourceId -> callback

    /**
     * Callback interface for async texture loading completion.
     */
    public interface TextureLoadCallback {
        /**
         * Called when a texture has finished loading and is ready for use.
         *
         * @param resourceId the resource ID of the texture
         * @param textureId the GL texture ID (0 if loading failed)
         */
        void onTextureLoaded(int resourceId, int textureId);
    }

    /**
     * Internal class to represent a pending GPU upload operation.
     */
    private static class PendingTextureUpload {
        int resourceId;
        int textureId;
        Bitmap bitmap;

        PendingTextureUpload(int resourceId, int textureId, Bitmap bitmap) {
            this.resourceId = resourceId;
            this.textureId = textureId;
            this.bitmap = bitmap;
        }
    }

    /**
     * Query the GPU's actual maximum texture size. This should be called once before
     * loading textures. Uses glGetIntegerv to query GL_MAX_TEXTURE_SIZE.
     */
    private void queryGPULimits() {
        if (gpuLimitsQueried) {
            return; // Already queried
        }

        int[] maxSize = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxSize, 0);
        maxTextureSize = maxSize[0];

        Log.d(TAG, "GPU max texture size queried: " + maxTextureSize + " pixels");
        gpuLimitsQueried = true;
    }

    /**
     * Get or load a texture by resource ID. Cached textures are returned immediately,
     * new textures are loaded and cached.
     *
     * @param context Android context for loading resources
     * @param resourceId drawable resource ID
     * @return GL texture ID (0 if failed)
     */
    public int getTexture(Context context, int resourceId) {
        // Query GPU limits on first use
        if (!gpuLimitsQueried) {
            queryGPULimits();
        }

        // Check cache first
        if (textureCache.containsKey(resourceId)) {
            Log.d(TAG, "Texture for resourceId=" + resourceId + " found in cache, returning texId=" + textureCache.get(resourceId));
            Integer cached = textureCache.get(resourceId);
            if (cached != null) {
                Log.d(TAG, "Texture for resourceId=" + resourceId + " found in cache, returning texId=" + cached);
                return cached;
            }
        }

        // Allocate texture ID (from pool or generate new)
        int textureId = allocateTextureId();
        if (textureId == 0) {
            Log.e(TAG, "Failed to allocate texture ID for resourceId=" + resourceId);
            return 0;
        }

        // Load and process bitmap
        Bitmap textureBitmap = decodeBitmap(context, resourceId);
        if (textureBitmap == null) {
            freeTextureId(textureId);
            return 0;
        }

        // Upload to GPU
        if (!uploadTextureToGPU(textureId, textureBitmap, resourceId)) {
            freeTextureId(textureId);
            textureBitmap.recycle();
            return 0;
        }

        textureBitmap.recycle();

        // Cache and return
        textureCache.put(resourceId, textureId);
        Log.d(TAG, "Texture uploaded to GPU for resourceId=" + resourceId + ", assigned texId=" + textureId + " (pool size: " + freedTextureIds.size() + ")");
        return textureId;
    }

    /**
     * Load a texture asynchronously. Bitmap decoding happens on a background thread,
     * while GPU upload happens on the GL thread via processPendingUploads().
     *
     * The callback will be invoked when the texture is ready for use.
     *
     * @param context Android context for loading resources
     * @param resourceId drawable resource ID
     * @param callback callback to invoke when texture is loaded (called on GL thread)
     */
    public void getTextureAsync(final Context context, final int resourceId, final TextureLoadCallback callback) {
        // Check cache first - if already loaded, call callback immediately
        if (textureCache.containsKey(resourceId)) {
            Integer cached = textureCache.get(resourceId);
            if (cached != null) {
                Log.d(TAG, "Async texture for resourceId=" + resourceId + " found in cache, invoking callback with texId=" + cached);
                if (callback != null) {
                    callback.onTextureLoaded(resourceId, cached);
                }
                return;
            }
        }

        // Check if already being loaded - if so, just register callback
        if (pendingBitmaps.containsKey(resourceId)) {
            Log.d(TAG, "Async texture for resourceId=" + resourceId + " already being loaded, registered callback");
            if (callback != null) {
                textureCallbacks.put(resourceId, callback);
            }
            return;
        }

        // Register callback before starting background load
        if (callback != null) {
            textureCallbacks.put(resourceId, callback);
        }

        // Decode on background thread
        backgroundExecutor.execute(() -> {
            try {
                Log.d(TAG, "Starting async decode for resourceId=" + resourceId + " on background thread");
                Bitmap bitmap = decodeBitmap(context, resourceId);
                if (bitmap != null) {
                    pendingBitmaps.put(resourceId, bitmap);
                    Log.d(TAG, "Async decode complete for resourceId=" + resourceId + ", queued for GPU upload");
                } else {
                    Log.e(TAG, "Failed to decode bitmap for resourceId=" + resourceId);
                    // Still invoke callback with failure
                    TextureLoadCallback cb = textureCallbacks.remove(resourceId);
                    if (cb != null) {
                        cb.onTextureLoaded(resourceId, 0);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during async decode for resourceId=" + resourceId, e);
                TextureLoadCallback cb = textureCallbacks.remove(resourceId);
                if (cb != null) {
                    cb.onTextureLoaded(resourceId, 0);
                }
            }
        });
    }

    /**
     * Process pending texture uploads from the async loading queue. This MUST be called
     * on the GL thread during rendering. Call this every frame to upload any textures that
     * finished decoding on background threads.
     */
    public void processPendingUploads() {
        // Query GPU limits on first use (might not have happened if only using async)
        if (!gpuLimitsQueried) {
            queryGPULimits();
        }

        // Process all pending uploads that are ready
        while (!pendingUploads.isEmpty()) {
            PendingTextureUpload pending = pendingUploads.poll();
            if (pending == null) break;

            if (uploadTextureToGPU(pending.textureId, pending.bitmap, pending.resourceId)) {
                // Upload successful
                textureCache.put(pending.resourceId, pending.textureId);
                Log.d(TAG, "Async texture uploaded to GPU for resourceId=" + pending.resourceId + ", texId=" + pending.textureId);

                // Invoke callback
                TextureLoadCallback callback = textureCallbacks.remove(pending.resourceId);
                if (callback != null) {
                    callback.onTextureLoaded(pending.resourceId, pending.textureId);
                }
            } else {
                // Upload failed
                Log.e(TAG, "Failed to upload texture for resourceId=" + pending.resourceId);
                freeTextureId(pending.textureId);

                // Invoke callback with failure
                TextureLoadCallback callback = textureCallbacks.remove(pending.resourceId);
                if (callback != null) {
                    callback.onTextureLoaded(pending.resourceId, 0);
                }
            }

            pending.bitmap.recycle();
        }

        // Check if any pending bitmaps are ready to be queued for upload
        for (int resourceId : pendingBitmaps.keySet()) {
            Bitmap bitmap = pendingBitmaps.get(resourceId);
            if (bitmap != null) {
                // Allocate texture ID
                int textureId = allocateTextureId();
                if (textureId != 0) {
                    // Queue for upload
                    pendingUploads.offer(new PendingTextureUpload(resourceId, textureId, bitmap));
                    pendingBitmaps.remove(resourceId);
                    Log.d(TAG, "Queued async texture for upload: resourceId=" + resourceId + ", texId=" + textureId);
                }
            }
        }
    }

    /**
     * Allocate a texture ID, preferring reused IDs from the freed pool before generating new ones.
     *
     * @return texture ID, or 0 if allocation failed
     */
    private int allocateTextureId() {
        // Try to reuse a freed texture ID first
        if (!freedTextureIds.isEmpty()) {
            Integer reusedId = freedTextureIds.poll();
            if (reusedId != null) {
                Log.d(TAG, "Reusing freed texture ID: " + reusedId + " (remaining in pool: " + freedTextureIds.size() + ")");
                return reusedId;
            }
        }

        // No freed IDs available, generate a new one
        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        if (textureIds[0] == 0) {
            Log.e(TAG, "Failed to generate new texture ID");
            return 0;
        }
        Log.d(TAG, "Generated new texture ID: " + textureIds[0]);
        return textureIds[0];
    }

    /**
     * Return a texture ID to the freed pool for reuse later.
     *
     * @param textureId GL texture ID to free
     */
    private void freeTextureId(int textureId) {
        freedTextureIds.offer(textureId);
        Log.d(TAG, "Freed texture ID: " + textureId + " (pool size: " + freedTextureIds.size() + ")");
    }

    /**
     * Decode a bitmap from resources with automatic downscaling for large textures.
     * Returns ARGB_8888 format for consistent texture handling.
     *
     * @param context Android context
     * @param resourceId drawable resource ID
     * @return decoded bitmap, or null if failed
     */
    private Bitmap decodeBitmap(Context context, int resourceId) {
        // Get dimensions first (without loading full bitmap)
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), resourceId, options);

        // Calculate optimal sample size
        int inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, resourceId);

        // Decode with scaling
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);

        if (bitmap == null) {
            Log.e(TAG, "Failed to decode resource " + resourceId);
            return null;
        }

        Log.d(TAG, "Successfully decoded resource " + resourceId + ", bitmap size: " + bitmap.getWidth() + "x" + bitmap.getHeight() + ", format: " + bitmap.getConfig());

        // Convert to ARGB_8888 if necessary
        return ensureARGB8888Format(bitmap);
    }

    /**
     * Calculate the inSampleSize needed to scale down large textures.
     * Caps maximum texture dimension at the GPU's reported limit to prevent GPU memory exhaustion.
     *
     * @param width original width
     * @param height original height
     * @param resourceId resource ID (for logging)
     * @return inSampleSize (1, 2, 4, 8, etc.)
     */
    private int calculateInSampleSize(int width, int height, int resourceId) {
        int inSampleSize = 1;

        if (height > maxTextureSize || width > maxTextureSize) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= maxTextureSize ||
                   (halfWidth / inSampleSize) >= maxTextureSize) {
                inSampleSize *= 2;
            }
            Log.d(TAG, "Large texture detected for resourceId=" + resourceId +
                  ": original=" + width + "x" + height +
                  ", max allowed=" + maxTextureSize +
                  ", scaling with inSampleSize=" + inSampleSize);
        }

        return inSampleSize;
    }

    /**
     * Ensure bitmap is in ARGB_8888 format. Converts if necessary.
     * This ensures consistent handling of both PNG and JPEG textures.
     *
     * @param bitmap original bitmap
     * @return bitmap in ARGB_8888 format (may be same object or a copy)
     */
    private Bitmap ensureARGB8888Format(Bitmap bitmap) {
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            Log.d(TAG, "Converting bitmap from " + bitmap.getConfig() + " to ARGB_8888");
            Bitmap converted = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            bitmap.recycle();
            return converted;
        }
        return bitmap;
    }

    /**
     * Upload bitmap to GPU and bind as texture.
     * Includes error checking for both exceptions and GL errors.
     *
     * @param textureId GL texture ID
     * @param bitmap bitmap to upload
     * @param resourceId resource ID (for logging)
     * @return true if successful, false if failed
     */
    private boolean uploadTextureToGPU(int textureId, Bitmap bitmap, int resourceId) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        setTextureParameters();
        Log.d(TAG, "Uploaded texture parameters for resourceId=" + resourceId);

        // Upload texture data
        try {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error uploading texture data for resourceId=" + resourceId, e);
            return false;
        }

        // Check for GL errors
        int glError = GLES20.glGetError();
        if (glError != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "GL error after texImage2D for resourceId=" + resourceId + ": " + glError);
            return false;
        }

        return true;
    }

    /**
     * Set standard texture parameters (wrapping, filtering).
     */
    private void setTextureParameters() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    }

    /**
     * Unload textures that are in the 'resourceIdsToUnload' set, but skip any that are
     * in the 'resourceIdsToKeep' set. This is used during scene switches to release GPU
     * memory for unused textures while keeping shared textures.
     *
     * @param resourceIdsToUnload set of resource IDs to potentially unload
     * @param resourceIdsToKeep set of resource IDs to keep (not unload)
     */
    public void unloadUnusedTextures(java.util.Set<Integer> resourceIdsToUnload, java.util.Set<Integer> resourceIdsToKeep) {
        java.util.List<Integer> texturesToDelete = new java.util.ArrayList<>();

        for (Integer resourceId : resourceIdsToUnload) {
            // Skip if this texture is needed by the next scene
            if (resourceIdsToKeep.contains(resourceId)) {
                Log.d(TAG, "Keeping texture for resourceId=" + resourceId + " (shared with next scene)");
                continue;
            }

            // Mark this texture for deletion
            if (textureCache.containsKey(resourceId)) {// Mark this texture for deletion
                Integer texIdObj = textureCache.remove(resourceId);
                if (texIdObj != null) {
                    int texId = texIdObj;
                    texturesToDelete.add(texId);
                    Log.d(TAG, "Marked for deletion: resourceId=" + resourceId + " (texId=" + texId + ")");
                }
            }
        }

        // Single GL call to delete all textures at once
        if (!texturesToDelete.isEmpty()) {
            int[] textureIds = new int[texturesToDelete.size()];
            for (int i = 0; i < texturesToDelete.size(); i++) {
                textureIds[i] = texturesToDelete.get(i);
            }
            GLES20.glDeleteTextures(textureIds.length, textureIds, 0);

            // Add freed IDs to the reuse pool
            for (Integer freedId : texturesToDelete) {
                freeTextureId(freedId);
            }

            Log.d(TAG, "Texture cleanup complete: unloaded " + texturesToDelete.size() + " textures in single GL call (pool size: " + freedTextureIds.size() + ")");
        } else {
            Log.d(TAG, "Texture cleanup complete: no textures to unload");
        }
    }


    public void destroyAll() {
        // Process any final pending uploads before destroying
        processPendingUploads();

        if (textureCache.isEmpty()) return;
        int[] ids = new int[textureCache.size()];
        int i = 0;
        for (int texId : textureCache.values()) {
            ids[i++] = texId;
        }
        GLES20.glDeleteTextures(ids.length, ids, 0);
        textureCache.clear();

        // Clear the pool since GL context is being destroyed
        freedTextureIds.clear();

        // Clean up async loading resources
        pendingUploads.clear();

        // Recycle any pending bitmaps
        for (Bitmap bitmap : pendingBitmaps.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        pendingBitmaps.clear();
        textureCallbacks.clear();

        Log.d(TAG, "All textures deleted and async resources cleaned up");
    }
}

