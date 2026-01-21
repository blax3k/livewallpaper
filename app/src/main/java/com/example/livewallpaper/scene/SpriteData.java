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

    // Set after resource resolution
    public int textureResourceId;
    public String name;
}

