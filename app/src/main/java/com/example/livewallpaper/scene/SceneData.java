package com.example.livewallpaper.scene;

/**
 * Data class representing a scene configuration loaded from JSON.
 * This class is used for JSON serialization/deserialization with Gson.
 */
public class SceneData {
    /**
     * Enum representing different times of day in the scene.
     */
    public enum TimeOfDay {
        DAWN,
        DAY,
        SUNSET,
        NIGHT
    }

    public String sceneName;
    public SpriteData[] sprites;
    public float xFocus = 0.5f; // Default to center if not specified in JSON
    public TimeOfDay timeOfDay = TimeOfDay.DAY; // Default to DAY if not specified in JSON
}

