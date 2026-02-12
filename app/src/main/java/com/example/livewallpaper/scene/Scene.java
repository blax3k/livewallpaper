package com.example.livewallpaper.scene;
import android.content.Context;
import android.util.Log;

import com.example.livewallpaper.gl.TextureManager;

import java.util.ArrayList;
import java.util.Collections;
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
    private boolean isGyroScaled = false;
    private float xFocus = 0.5f; // Default to center; represents the x-position to focus on (0.0 = left, 0.5 = center, 1.0 = right)
    public Scene(String sceneName) {
        this.sceneName = sceneName;
        // Use Collections.synchronizedList to make the list thread-safe
        // This prevents ConcurrentModificationException when sprites are added from UI thread while rendering
        this.sprites = Collections.synchronizedList(new ArrayList<>());
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
     * Remove all sprites except the one with the specified name.
     * Useful for preview rendering when you only want to display a single sprite.
     *
     * @param spriteName the name of the sprite to keep
     */
    public void keepOnlySprite(String spriteName) {
        sprites.removeIf(sprite -> !sprite.getName().equals(spriteName));
        Log.d(TAG, "Filtered scene to keep only sprite: " + spriteName + ". Remaining sprites: " + sprites.size());
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
            if (a.isWipingOut() && !b.isWipingOut()) return -1;  // a is wiping out, b is not
            if (!a.isWipingOut() && b.isWipingOut()) return 1;   // b is wiping out, a is not
            if (a.isWipingIn() && !b.isWipingIn()) return -1;    // a is wiping in, b is not
            if (!a.isWipingIn() && b.isWipingIn()) return 1;     // b is wiping in, a is not
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
        }

        isInitialized = true;
        Log.d(TAG, "Scene '" + sceneName + "' initialized successfully");
    }

    /**
     * Reload textures for all sprites after surface recreation (e.g., pause/resume).
     * Preserves all sprite properties and doesn't reset initialization state.
     *
     * @param context the Android context for loading resources
     * @param textureManager the texture manager for loading textures
     */
    public void reloadTextures(Context context, TextureManager textureManager) {
        Log.d(TAG, "Reloading textures for scene '" + sceneName + "' with " + sprites.size() + " sprites");

        for (Sprite sprite : sprites) {
            loadSpriteTexture(context, textureManager, sprite);
        }

        Log.d(TAG, "Textures reloaded for scene '" + sceneName + "'");
    }

    /**
     * Load the texture for a sprite through the TextureManager asynchronously.
     * The texture will be applied to the sprite via callback when ready.
     * Public version for external calls.
     *
     * @param context the Android context for loading resources
     * @param textureManager the texture manager for loading textures
     * @param sprite the sprite to load texture for
     */
    public void loadSpriteTexture(Context context, TextureManager textureManager, Sprite sprite) {
        int resourceId = sprite.getTextureResourceId();

        textureManager.getTextureAsync(context, resourceId, (resId, texId) -> {
            sprite.setTextureId(texId);
            Log.d(TAG, "Sprite textureResourceId=" + resourceId + " loaded with texId=" + texId);
            if (texId == 0) {
                Log.w(TAG, "WARNING: Failed to load texture for resourceId=" + resourceId + ". This sprite may not render.");
            }
        });
    }

    /**
     * Apply gyro scaling to all sprites in this scene.
     *
     * When the gyro moves, sprites are moved by their parallaxMultiplier amount.
     * This can reveal blank background behind sprites that touch the edges (e.g., a 10x10 sprite).
     *
     * To compensate, we scale up sprite sizes relative to their parallaxMultiplier:
     * - A sprite with parallaxMultiplier=1.0 can move 1.0 unit, so we increase its size by 10%
     * - A sprite with parallaxMultiplier=0.5 can move 0.5 unit, so we increase its size by 5%
     *
     * Sprites also need position adjustment. If a sprite is centered at (0,0), no adjustment needed.
     * However, if positioned at (2,2), it must also shift away from center proportionally to maintain
     * relative positioning with other sprites: newPosition = oldPosition * scaleFactor
     */
    public void applyGyroScaling() {
        if (!isGyroScaled) {
            Log.d(TAG, "Gyro scaling not enabled for scene '" + sceneName + "'");
            return;
        }

        for (Sprite sprite : sprites) {
            // Calculate the gyro scale factor based on parallax multiplier
            // Formula: scale = 1.0 + parallaxMultiplier * 0.1
            // This means:
            // - parallaxMultiplier=1.0 -> scale=1.1 (10% increase)
            // - parallaxMultiplier=0.5 -> scale=1.05 (5% increase)
            // - parallaxMultiplier=0.0 -> scale=1.0 (no change, fully parallaxed)
            float parallaxMultiplier = sprite.getParallaxMultiplier();
            float spriteGyroScaleFactor = 1.0f + (parallaxMultiplier * 0.1f);

            // Scale the sprite size based on its parallax multiplier
            sprite.scaleFromOriginal(spriteGyroScaleFactor);

            // Scale the position away from center to maintain relative positioning
            float originalX = sprite.getOriginalPositionX();
            float originalY = sprite.getOriginalPositionY();
            float scaledX = originalX * spriteGyroScaleFactor;
            float scaledY = originalY * spriteGyroScaleFactor;
            sprite.setPosition(scaledX, scaledY);

            Log.d(TAG, "Applied gyro scaling to sprite '" + sprite.getName() +
                    "' with parallaxMultiplier=" + parallaxMultiplier +
                    ", spriteGyroScaleFactor=" + spriteGyroScaleFactor +
                    ", newSize=" + sprite.getWidth() + "x" + sprite.getHeight() +
                    ", newPosition=(" + scaledX + ", " + scaledY + ")");
        }

        Log.d(TAG, "Scene '" + sceneName + "' gyro scaling applied to all sprites");
    }

    /**
     * Enable gyro scaling for this scene.
     * Call this before rendering to apply gyro scaling based on sprite parallax multipliers.
     */
    public void enableGyroScaling() {
        this.isGyroScaled = true;
        for(Sprite sprite : sprites) {
            sprite.setGyroScaled(true);
        }
    }

    /**
     * Disable gyro scaling and reset all sprites to their original sizes and positions.
     */
    public void disableGyroScaling() {
        for (Sprite sprite : sprites) {
            sprite.setGyroScaled(false);
            sprite.resetScale();
            sprite.resetPosition();
        }
        this.isGyroScaled = false;
        Log.d(TAG, "Gyro scaling disabled for scene '" + sceneName + "', all sprites reset");
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

    /**
     * Get a unique sprite name for this scene.
     * If the base name doesn't exist, returns it as-is.
     * If the name already exists, appends a number suffix (e.g., "sprite2", "sprite3").
     *
     * @param baseName the desired sprite name
     * @return a unique name that doesn't exist in this scene
     */
    public String getUniqueName(String baseName) {
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "sprite";
        }

        final String finalBaseName = baseName;

        // Check if the base name already exists
        boolean nameExists = sprites.stream().anyMatch(s -> s.getName().equals(finalBaseName));
        if (!nameExists) {
            return baseName;
        }

        // Find a unique name by appending numbers
        int counter = 2;
        while (true) {
            String candidateName = finalBaseName + counter;
            boolean candidateExists = sprites.stream().anyMatch(s -> s.getName().equals(candidateName));
            if (!candidateExists) {
                Log.d(TAG, "Generated unique name for sprite: " + finalBaseName + " -> " + candidateName);
                return candidateName;
            }
            counter++;
        }
    }
}
