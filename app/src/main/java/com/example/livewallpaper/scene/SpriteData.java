package com.example.livewallpaper.scene;

/**
 * Configuration class for creating sprites with all necessary parameters.
 * Used both for JSON deserialization (fields populated by GSON) and
 * programmatic sprite creation (via constructor).
 */
public class SpriteData {
    // Fields populated by GSON during JSON deserialization
    public String textureResource;
    public float width;
    public float height;
    public float parallaxMultiplier;
    public float positionX;
    public float positionY;
    public float[] texCoordinates;

    // Texture editing state fields (texture scale and offsets)
    // These are persisted alongside texCoordinates to maintain editing state across save/load cycles
    public float textureScale = 1.0f;  // Default to 1.0 (no zoom) if not specified
    public float textureOffsetU = 0.0f;  // Default to 0.0 (centered) if not specified
    public float textureOffsetV = 0.0f;  // Default to 0.0 (centered) if not specified

    // Set after resource resolution
    public int textureResourceId;
    public String name;
}

