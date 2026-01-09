package com.example.livewallpaper;

/**
 * Configuration class for creating sprites with all necessary parameters.
 * This makes sprite initialization cleaner and more readable.
 */
public class SpriteConfig {
    public final int textureResourceId;
    public final float width;
    public final float height;
    public final float parallaxMultiplier;
    public final float positionX;
    public final float positionY;

    /**
     * Create a sprite configuration with all parameters.
     *
     * @param textureResourceId the drawable resource ID for the texture
     * @param width the width in world units
     * @param height the height in world units
     * @param parallaxMultiplier the parallax multiplier (1.0 = full scroll, 0.5 = half, etc.)
     * @param positionX the x position in world units
     * @param positionY the y position in world units
     */
    public SpriteConfig(int textureResourceId, float width, float height,
                       float parallaxMultiplier, float positionX, float positionY) {
        this.textureResourceId = textureResourceId;
        this.width = width;
        this.height = height;
        this.parallaxMultiplier = parallaxMultiplier;
        this.positionX = positionX;
        this.positionY = positionY;
    }

    /**
     * Builder pattern for more flexible sprite configuration.
     */
    public static class Builder {
        private final int textureResourceId;
        private float width;
        private float height;
        private float parallaxMultiplier = 1.0f;
        private float positionX = 0f;
        private float positionY = 0f;

        public Builder(int textureResourceId, float width, float height) {
            this.textureResourceId = textureResourceId;
            this.width = width;
            this.height = height;
        }

        public Builder parallaxMultiplier(float multiplier) {
            this.parallaxMultiplier = multiplier;
            return this;
        }

        public Builder position(float x, float y) {
            this.positionX = x;
            this.positionY = y;
            return this;
        }

        public SpriteConfig build() {
            return new SpriteConfig(textureResourceId, width, height,
                                   parallaxMultiplier, positionX, positionY);
        }
    }
}

