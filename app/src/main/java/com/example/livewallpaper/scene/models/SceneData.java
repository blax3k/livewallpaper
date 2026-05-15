package com.example.livewallpaper.scene.models;

/**
 * Data class representing a scene configuration loaded from JSON.
 * This class is used for JSON serialization/deserialization with Gson.
 */
public class SceneData {
    public String sceneName;
    public SpriteData[] sprites;
    public float xFocus = 0.5f; // Default to center if not specified in JSON
    /** Start time as minutes-of-day (0–1439, inclusive) when this scene becomes available. Defaults to 0 (00:00). */
    public int startTime = 0;
    /** End time as minutes-of-day (0–1439, inclusive) until which this scene is available. Defaults to 1439 (23:59). */
    public int endTime = 1439;
}

