package com.example.livewallpaper;

import java.util.List;

/**
 * Data class representing a scene configuration loaded from JSON.
 * This class is used for JSON serialization/deserialization with Gson.
 */
public class SceneData {
    public String sceneName;
    public List<SpriteData> sprites;

    /**
     * Data class representing sprite configuration in JSON.
     */
    public static class SpriteData {
        public String textureResource;  // Resource name (e.g., "knight", "tower")
        public float width;
        public float height;
        public float parallaxMultiplier;
        public float positionX;
        public float positionY;

        /**
         * Default constructor for Gson.
         */
        public SpriteData() {}

        /**
         * Constructor for creating sprite data programmatically.
         */
        public SpriteData(String textureResource, float width, float height,
                         float parallaxMultiplier, float positionX, float positionY) {
            this.textureResource = textureResource;
            this.width = width;
            this.height = height;
            this.parallaxMultiplier = parallaxMultiplier;
            this.positionX = positionX;
            this.positionY = positionY;
        }
    }

    /**
     * Default constructor for Gson.
     */
    public SceneData() {}

    /**
     * Constructor for creating scene data programmatically.
     */
    public SceneData(String sceneName, List<SpriteData> sprites) {
        this.sceneName = sceneName;
        this.sprites = sprites;
    }
}

