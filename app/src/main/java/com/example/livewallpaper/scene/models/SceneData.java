package com.example.livewallpaper.scene.models;

/**
 * Data class representing a scene configuration loaded from JSON.
 * This class is used for JSON serialization/deserialization with Gson.
 */
public class SceneData {
    public String sceneName;
    public SpriteData[] sprites;
    public float xFocus = 0.5f; // Default to center if not specified in JSON

    /**
     * Flag-based declarations controlling when this scene is eligible and how it scores.
     * This is the primary mechanism for scene selection in the storytelling system.
     * May be null for scenes that have not yet been configured with flags (scores 0, always eligible).
     */
    public SceneFlagDeclarations flags;

    /**
     * @deprecated Use time-of-day flags ("morning", "afternoon", "evening", "night") via
     * {@code flags.required} instead. These fields are retained for backward compatibility
     * with existing scene JSON that predates the flag system and will be removed in a future version.
     */
    @Deprecated
    public int startTime = 0;

    /**
     * @deprecated See {@code startTime}.
     */
    @Deprecated
    public int endTime = 1439;
}

