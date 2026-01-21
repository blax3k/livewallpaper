package com.example.livewallpaper.scene;

/**
 * Configuration class for creating sprites with all necessary parameters.
 * This makes sprite initialization cleaner and more readable.
 */
public class SpriteConfig {
    public final int textureResourceId;
    public final String name;
    public final float width;
    public final float height;
    public final float parallaxMultiplier;
    public final float positionX;
    public final float positionY;

    /**
     * Create a sprite configuration with all parameters.
     *
     * @param textureResourceId the drawable resource ID for the texture
     * @param name the name of the sprite for debugging
     * @param width the width in world units
     * @param height the height in world units
     * @param parallaxMultiplier the parallax multiplier (1.0 = full scroll, 0.5 = half, etc.)
     * @param positionX the x position in world units
     * @param positionY the y position in world units
     */
    public SpriteConfig(int textureResourceId, String name, float width, float height,
                       float parallaxMultiplier, float positionX, float positionY) {
        this.textureResourceId = textureResourceId;
        this.name = name;
        this.width = width;
        this.height = height;
        this.parallaxMultiplier = parallaxMultiplier;
        this.positionX = positionX;
        this.positionY = positionY;
    }
}

