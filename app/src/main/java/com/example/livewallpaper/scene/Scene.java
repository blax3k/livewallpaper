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
    private float currentGyroScaleFactor = 1.0f;
    private boolean isGyroScaled = false;
    private float xFocus = 0.5f; // Default to center; represents the x-position to focus on (0.0 = left, 0.5 = center, 1.0 = right)
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
     * Get the x-focus position for this scene (0.0 = left, 0.5 = center, 1.0 = right).
     * This is used to automatically set the scroll offset when scroll motion is disabled.
     */
    public float getXFocus() {
        return xFocus;
    }

    /**
     * Set the x-focus position for this scene.
     */
    public void setXFocus(float xFocus) {
        this.xFocus = xFocus;
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
     * Update wipe progress on all transitioning sprites in this scene.
     * @param progress the wipe progress (0.0 to 1.0)
     */
    public void updateWipeProgress(float progress) {
        for (Sprite sprite : sprites) {
            if (sprite.isTransitioning()) {
                sprite.setWipeProgress(progress);
            }
        }
    }

    /**
     * Sort sprites by parallax multiplier to ensure correct draw order.
     * Lower parallax values (further back) are drawn first.
     * At the same parallax level, draw order is:
     * 1. Wiping-out sprites - drawn first (behind)
     * 2. Wiping-in sprites - drawn second
     * 3. Non-transitioning sprites - drawn last (in front)
     */
    public void sortSpritesByParallax() {
        // Log before sorting
        Log.d(TAG, "=== BEFORE SORT ===");
        for (int i = 0; i < sprites.size(); i++) {
            Sprite s = sprites.get(i);
            Log.d(TAG, "  [" + i + "] " + s.getName() + " | parallax=" + s.getParallaxMultiplier() +
                  " | wipingOut=" + s.isWipingOut() + " | wipingIn=" + s.isWipingIn());
        }

        sprites.sort((a, b) -> {
            int parallaxCompare = Float.compare(a.getParallaxMultiplier(), b.getParallaxMultiplier());
            if (parallaxCompare != 0) {
                return parallaxCompare;
            }
            // Secondary sort at same parallax level:
            // 1. Wiping-out sprites (old) drawn first (behind)
            // 2. Wiping-in sprites (new) drawn second (in front)
            // 3. Non-transitioning sprites drawn last (fully in front)
            if (a.isWipingOut() && !b.isWipingOut()) return -1;  // a is wiping out, b is not
            if (!a.isWipingOut() && b.isWipingOut()) return 1;   // b is wiping out, a is not
            if (a.isWipingIn() && !b.isWipingIn()) return -1;    // a is wiping in, b is not
            if (!a.isWipingIn() && b.isWipingIn()) return 1;     // b is wiping in, a is not
            if (a.isTransitioning() && !b.isTransitioning()) return -1;  // a transitioning, b not
            if (!a.isTransitioning() && b.isTransitioning()) return 1;   // b transitioning, a not
            return 0;
        });

        // Log after sorting
        Log.d(TAG, "=== AFTER SORT ===");
        for (int i = 0; i < sprites.size(); i++) {
            Sprite s = sprites.get(i);
            Log.d(TAG, "  [" + i + "] " + s.getName() + " | parallax=" + s.getParallaxMultiplier() +
                  " | wipingOut=" + s.isWipingOut() + " | wipingIn=" + s.isWipingIn());
        }
    }
    /**
     * Initialize all sprites in this scene by loading their textures.
     * Should be called when the GL context is ready.
     * Will apply stored gyro scaling if it was set.
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

        // Load textures and apply gyro scaling in a single pass
        for (Sprite sprite : sprites) {
            loadSpriteTexture(context, textureManager, sprite);
            applyGyroScalingToSprite(sprite);
        }

        isInitialized = true;
        Log.d(TAG, "Scene '" + sceneName + "' initialized successfully");
    }

    /**
     * Load the texture for a sprite through the TextureManager.
     *
     * @param context the Android context for loading resources
     * @param textureManager the texture manager for loading textures
     * @param sprite the sprite to load texture for
     */
    private void loadSpriteTexture(Context context, TextureManager textureManager, Sprite sprite) {
        int resourceId = sprite.getTextureResourceId();
        int texId = textureManager.getTexture(context, resourceId);
        sprite.setTextureId(texId);
        Log.d(TAG, "Sprite textureResourceId=" + resourceId + " loaded with texId=" + texId);
        if (texId == 0) {
            Log.w(TAG, "WARNING: Texture ID is 0 for resourceId=" + resourceId + ". This sprite may not render.");
        }
    }

    /**
     * Apply stored gyro scaling to a sprite if gyro scaling is enabled.
     *
     * @param sprite the sprite to apply gyro scaling to
     */
    private void applyGyroScalingToSprite(Sprite sprite) {
        if (isGyroScaled && currentGyroScaleFactor > 1.0f && !sprite.isGyroScaled()) {
            // Scale the sprite size
            sprite.scaleFromOriginal(currentGyroScaleFactor);
            // Scale the position away from center (0, 0) to maintain relative spacing
            float currentX = sprite.getPositionX();
            float currentY = sprite.getPositionY();
            sprite.setPosition(currentX * currentGyroScaleFactor, currentY * currentGyroScaleFactor);
            Log.d(TAG, "Applied gyro scaling to sprite. Scale factor: " + currentGyroScaleFactor);
        }
    }

    /**
     * Set the gyro scaling state that will be applied during initialization.
     * Call this before initializing the scene to ensure sprites are created with proper scaling.
     *
     * @param scaleFactor the gyro scale factor to apply (1.0 = no scaling, >1.0 = enlarged)
     */
    public void setGyroScalingForInitialization(float scaleFactor) {
        if (scaleFactor > 1.0f) {
            this.currentGyroScaleFactor = scaleFactor;
            this.isGyroScaled = true;
            Log.d(TAG, "Scene '" + sceneName + "' marked to apply gyro scaling on initialization. Factor: " + scaleFactor);
        }
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
        this.isGyroScaled = false;
        this.currentGyroScaleFactor = 1.0f;
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
