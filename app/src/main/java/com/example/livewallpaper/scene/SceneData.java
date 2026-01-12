package com.example.livewallpaper.scene;

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

    }
}

