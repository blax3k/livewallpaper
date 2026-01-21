package com.example.livewallpaper.scene;

/**
 * Data class representing a scene configuration loaded from JSON.
 * This class is used for JSON serialization/deserialization with Gson.
 */
public class SceneData {
    public String sceneName;
    public SpriteData[] sprites;
    public float xFocus = 0.5f; // Default to center if not specified in JSON
}

