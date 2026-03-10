package com.example.livewallpaper.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.graphics.Bitmap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for TextureManager class.
 * Tests texture caching, async loading, and resource cleanup.
 *
 * Note: Tests that require actual GL context (like GPU limits querying) are simplified
 * since Robolectric provides a simulated GL environment.
 */
@RunWith(RobolectricTestRunner.class)
public class TextureManagerTest {
    private Context context;
    private TextureManager textureManager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        textureManager = new TextureManager();
    }

    // ==================== Texture Cache Tests ====================

    /**
     * Test that async loading with a cached texture invokes callback immediately.
     */
    @Test
    public void testGetTextureAsync_CachedTexture() {
        TextureManager.TextureLoadCallback callback = mock(TextureManager.TextureLoadCallback.class);
        final int resourceId = 123;
        final int textureId = 456;

        // Manually add a texture to cache using reflection
        addTextureToCache(resourceId, textureId);

        // Call async load for cached texture
        textureManager.getTextureAsync(context, resourceId, callback);

        // Verify callback was invoked immediately with cached texture ID
        verify(callback, times(1)).onTextureLoaded(resourceId, textureId);
    }

    /**
     * Test that getTextureAsync handles null callback gracefully.
     */
    @Test
    public void testGetTextureAsync_NullCallback() {
        final int resourceId = 789;

        // Should not throw even with null callback
        textureManager.getTextureAsync(context, resourceId, null);

        // Test passes if no exception is thrown
        assertTrue("Test completed without exception", true);
    }

    /**
     * Test that multiple callbacks for the same resource are handled correctly.
     */
    @Test
    public void testGetTextureAsync_MultipleCallbacks() {
        TextureManager.TextureLoadCallback callback1 = mock(TextureManager.TextureLoadCallback.class);
        TextureManager.TextureLoadCallback callback2 = mock(TextureManager.TextureLoadCallback.class);
        final int resourceId = 999;

        // First callback registers for async load
        textureManager.getTextureAsync(context, resourceId, callback1);

        // Second callback for same resource should replace first
        textureManager.getTextureAsync(context, resourceId, callback2);

        // Verify that second callback is registered
        TextureManager.TextureLoadCallback registeredCallback = getRegisteredCallback(resourceId);
        assertEquals("Second callback should be registered", callback2, registeredCallback);
    }

    /**
     * Test that texture cache correctly stores and retrieves texture IDs.
     */
    @Test
    public void testTextureCache_StoreAndRetrieve() {
        final int resourceId = 111;
        final int textureId = 222;

        addTextureToCache(resourceId, textureId);

        Integer cachedId = getTextureFromCache(resourceId);
        assertEquals("Cached texture ID should match stored value", Integer.valueOf(textureId), cachedId);
    }

    /**
     * Test that empty cache is handled correctly.
     */
    @Test
    public void testTextureCache_EmptyCache() {
        Integer cachedId = getTextureFromCache(999);
        assertTrue("Empty cache should return no texture", cachedId == null);
    }

    // ==================== Async Loading Tests ====================

    /**
     * Test that pending uploads queue is processed without error when empty.
     */
    @Test
    public void testProcessPendingUploads_EmptyQueue() {
        // Process empty queue should not throw
        textureManager.processPendingUploads();

        // Test passes if no exception is thrown
        assertTrue("Test completed without exception", true);
    }

    /**
     * Test that destroyAll handles empty texture cache.
     */
    @Test
    public void testDestroyAll_EmptyCache() {
        // Empty cache should not throw
        textureManager.destroyAll();

        // Verify cache is empty
        assertTrue("Cache should be empty", getTextureCacheSize() == 0);
    }

    /**
     * Test that destroyAll cleans up textures and callbacks.
     */
    @Test
    public void testDestroyAll_WithTextures() {
        // Add some textures to cache
        addTextureToCache(1, 100);
        addTextureToCache(2, 200);

        // Verify textures were added
        assertEquals("Should have 2 textures in cache", 2, getTextureCacheSize());

        // Destroy should clean up
        textureManager.destroyAll();

        // Verify cache was cleared
        assertEquals("Cache should be empty after destroy", 0, getTextureCacheSize());
    }

    /**
     * Test that destroyAll clears pending bitmaps.
     */
    @Test
    public void testDestroyAll_ClearsPendingBitmaps() {
        // Add a pending bitmap
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        addPendingBitmap(1, bitmap);

        // Verify bitmap was added
        assertTrue("Should have pending bitmaps", getPendingBitmapCount() > 0);

        // Destroy should clean up
        textureManager.destroyAll();

        // Verify pending bitmaps were cleared
        assertEquals("Pending bitmaps should be empty after destroy", 0, getPendingBitmapCount());
    }

    // ==================== Texture Unloading Tests ====================

    /**
     * Test unload with empty sets.
     */
    @Test
    public void testUnloadUnusedTextures_EmptySets() {
        Set<Integer> toUnload = new HashSet<>();
        Set<Integer> toKeep = new HashSet<>();

        // Should not throw with empty sets
        textureManager.unloadUnusedTextures(toUnload, toKeep);

        assertTrue("Test completed without exception", true);
    }

    /**
     * Test unload preserves shared textures.
     */
    @Test
    public void testUnloadUnusedTextures_PreservesSharedTextures() {
        // Add textures to cache
        addTextureToCache(1, 100);
        addTextureToCache(2, 200);
        addTextureToCache(3, 300);

        assertEquals("Should have 3 textures initially", 3, getTextureCacheSize());

        // Prepare unload sets
        Set<Integer> toUnload = new HashSet<>();
        toUnload.add(1);
        toUnload.add(2);

        Set<Integer> toKeep = new HashSet<>();
        toKeep.add(2); // Keep texture 2

        // Unload
        textureManager.unloadUnusedTextures(toUnload, toKeep);

        // Verify texture 2 was not removed (shared)
        Integer texture2 = getTextureFromCache(2);
        assertEquals("Shared texture 2 should be preserved", Integer.valueOf(200), texture2);
    }

    /**
     * Test unload removes non-shared textures.
     */
    @Test
    public void testUnloadUnusedTextures_RemovesNonShared() {
        // Add textures to cache
        addTextureToCache(1, 100);
        addTextureToCache(2, 200);
        addTextureToCache(3, 300);

        // Prepare unload sets
        Set<Integer> toUnload = new HashSet<>();
        toUnload.add(1);
        toUnload.add(2);

        Set<Integer> toKeep = new HashSet<>();
        // No textures to keep

        // Unload
        textureManager.unloadUnusedTextures(toUnload, toKeep);

        // Verify unloaded textures were removed
        assertFalse("Texture 1 should be removed", getTextureFromCache(1) != null);
        assertFalse("Texture 2 should be removed", getTextureFromCache(2) != null);
    }

    // ==================== Helper Methods ====================

    /**
     * Add a texture to the cache using reflection.
     */
    private void addTextureToCache(int resourceId, int textureId) {
        try {
            java.lang.reflect.Field cacheField = TextureManager.class.getDeclaredField("textureCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Integer, Integer> cache = (java.util.Map<Integer, Integer>) cacheField.get(textureManager);
            cache.put(resourceId, textureId);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get a texture from the cache using reflection.
     */
    private Integer getTextureFromCache(int resourceId) {
        try {
            java.lang.reflect.Field cacheField = TextureManager.class.getDeclaredField("textureCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Integer, Integer> cache = (java.util.Map<Integer, Integer>) cacheField.get(textureManager);
            return cache.get(resourceId);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the size of the texture cache using reflection.
     */
    private int getTextureCacheSize() {
        try {
            java.lang.reflect.Field cacheField = TextureManager.class.getDeclaredField("textureCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Integer, Integer> cache = (java.util.Map<Integer, Integer>) cacheField.get(textureManager);
            return cache.size();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Register a callback using reflection.
     */
    private TextureManager.TextureLoadCallback getRegisteredCallback(int resourceId) {
        try {
            java.lang.reflect.Field callbacksField = TextureManager.class.getDeclaredField("textureCallbacks");
            callbacksField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Integer, TextureManager.TextureLoadCallback> callbacks = (java.util.Map<Integer, TextureManager.TextureLoadCallback>) callbacksField.get(textureManager);
            return callbacks.get(resourceId);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add a pending bitmap using reflection.
     */
    private void addPendingBitmap(int resourceId, Bitmap bitmap) {
        try {
            java.lang.reflect.Field pendingField = TextureManager.class.getDeclaredField("pendingBitmaps");
            pendingField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Integer, Bitmap> pending = (java.util.Map<Integer, Bitmap>) pendingField.get(textureManager);
            pending.put(resourceId, bitmap);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the count of pending bitmaps using reflection.
     */
    private int getPendingBitmapCount() {
        try {
            java.lang.reflect.Field pendingField = TextureManager.class.getDeclaredField("pendingBitmaps");
            pendingField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Integer, Bitmap> pending = (java.util.Map<Integer, Bitmap>) pendingField.get(textureManager);
            return pending.size();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}






