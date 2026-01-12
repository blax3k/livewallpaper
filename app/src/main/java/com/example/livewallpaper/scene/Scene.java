package com.example.livewallpaper.scene;
import android.content.Context;
import android.util.Log;
import com.example.livewallpaper.gl.TextureManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/**
 * Represents a scene containing a collection of sprites.
 * Scenes can be switched to display different sets of sprites on screen.
 */
public class Scene {
    private static final String TAG = "Scene";
    private final String sceneName;
    private final List<Sprite> sprites;
    private boolean isInitialized = false;
    public Scene(String sceneName) {
        this.sceneName = sceneName;
        this.sprites = new ArrayList<>();
    }
    /**
     * Get the name of this scene.
     */
    public String getSceneName() {
        return sceneName;
    }
    /**
     * Get all sprites in this scene.
     */
    public List<Sprite> getSprites() {
        return sprites;
    }
    /**
     * Add a sprite to this scene.
     */
    public void addSprite(Sprite sprite) {
        sprites.add(sprite);
    }
    /**
     * Initialize all sprites in this scene by loading their textures.
     * Should be called when the GL context is ready.
     *
     * @param context the Android context for loading resources
     * @param textureManager the texture manager for loading textures
     */
    public void initialize(Context context, TextureManager textureManager) {
        if (isInitialized) {
            Log.d(TAG, "Scene '" + sceneName + "' already initialized");
            return;
        }
        Log.d(TAG, "Initializing scene '" + sceneName + "' with " + sprites.size() + " sprites");
        // Resolve textures for each sprite through TextureManager
        for (Sprite sprite : sprites) {
            int resourceId = sprite.getTextureResourceId();
            int texId = textureManager.getTexture(context, resourceId);
            sprite.setTextureId(texId);
            Log.d(TAG, "Sprite textureResourceId=" + resourceId + " loaded with texId=" + texId);
            if (texId == 0) {
                Log.w(TAG, "WARNING: Texture ID is 0 for resourceId=" + resourceId + ". This sprite may not render.");
            }
        }
        isInitialized = true;
        Log.d(TAG, "Scene '" + sceneName + "' initialized successfully");
    }
    /**
     * Check if this scene has been initialized.
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    /**
     * Apply gyro scaling to all sprites in this scene.
     *
     * @param scaleFactor the scale factor to apply (>1.0 to enlarge)
     */
    public void applyGyroScaling(float scaleFactor) {
        for (Sprite sprite : sprites) {
            // Scale the sprite size
            sprite.scaleFromOriginal(scaleFactor);
            // Scale the position away from center (0, 0) to maintain relative spacing
            float currentX = sprite.getPositionX();
            float currentY = sprite.getPositionY();
            sprite.setPosition(currentX * scaleFactor, currentY * scaleFactor);
        }
        Log.d(TAG, "Scene '" + sceneName + "' scaled for gyro motion. Scale factor: " + scaleFactor);
    }
    /**
     * Reset all sprites in this scene to their original sizes and positions.
     */
    public void resetGyroScaling() {
        for (Sprite sprite : sprites) {
            // Reset size to original
            sprite.resetScale();
            // Reset position to original
            sprite.resetPosition();
        }
        Log.d(TAG, "Scene '" + sceneName + "' reset to original size and position");
    }
    /**
     * Destroy all sprites in this scene and release resources.
     */
    public void destroy() {
        for (Sprite sprite : sprites) {
            sprite.destroy();
        }
        sprites.clear();
        isInitialized = false;
        Log.d(TAG, "Scene '" + sceneName + "' destroyed");
    }
    /**
     * Get the number of sprites in this scene.
     */
    public int getSpriteCount() {
        return sprites.size();
    }

    /**
     * Get all unique texture resource IDs used by sprites in this scene.
     * Useful for determining which textures to keep/unload during scene switches.
     *
     * @return set of unique resource IDs
     */
    public Set<Integer> getUsedTextureResourceIds() {
        Set<Integer> usedIds = new HashSet<>();
        for (Sprite sprite : sprites) {
            usedIds.add(sprite.getTextureResourceId());
        }
        return usedIds;
    }
}
