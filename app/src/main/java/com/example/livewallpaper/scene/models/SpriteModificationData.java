package com.example.livewallpaper.scene.models;

/**
 * A single modification applied to a sprite when its condition block is satisfied.
 *
 * <pre>
 * Type                   Required fields
 * ────────────────────── ─────────────────────────────────
 * "visible"              visible
 * "texture"              textureResource
 * "texture_coordinates"  texCoordinates  (8 floats, UV quad)
 * "position"             positionX, positionY
 * </pre>
 *
 * Multiple modifications can be listed in one condition block and are all applied together.
 */
public class SpriteModificationData {
    public String type;

    /** Show or hide the sprite. Used by: visible. */
    public boolean visible;

    /** Texture resource name to swap in. Used by: texture. */
    public String textureResource;

    /**
     * Replacement UV coordinates (8 floats: [u0,v0, u0,v1, u1,v0, u1,v1]).
     * Used by: texture_coordinates.
     */
    public float[] texCoordinates;

    /** Replacement world-space X position. Used by: position. */
    public float positionX;

    /** Replacement world-space Y position. Used by: position. */
    public float positionY;
}
