package com.example.livewallpaper;

import android.content.Context;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Represents a single sprite that can be rendered with an independent position, size, and texture.
 * This class now only owns geometry buffers and sprite properties; texture loading and draw calls
 * are delegated to TextureManager and SpriteRenderer respectively.
 */
public class Sprite {
    private static final String TAG = "Sprite";
    private static final int VERTEX_COUNT = 4;

    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;
    private FloatBuffer parallaxMultiplierBuffer;
    private int textureId = 0;
    private final int textureResourceId;

    // Sprite properties for positioning and sizing
    private float positionX = 0f;
    private float positionY = 0f;
    private float width;
    private float height;
    private float parallaxMultiplier = 1.0f;
    private float alpha = 1.0f;

    // Store original dimensions and positions for scaling
    private final float originalWidth;
    private final float originalHeight;
    private float originalPositionX = 0f;
    private float originalPositionY = 0f;

    public Sprite(int textureResourceId, float width, float height) {
        this.textureResourceId = textureResourceId;
        this.width = width;
        this.height = height;
        this.originalWidth = width;
        this.originalHeight = height;
        initializeGeometry();
        // Texture loading is handled by TextureManager; caller should set textureId via setTextureId().
    }

    /**
     * Constructor with parallax multiplier and position.
     *
     * @param textureResourceId the drawable resource ID for the texture
     * @param width the width in world units
     * @param height the height in world units
     * @param parallaxMultiplier the parallax multiplier (1.0 = full scroll, 0.5 = half, etc.)
     * @param positionX the x position in world units
     * @param positionY the y position in world units
     */
    public Sprite(int textureResourceId, float width, float height, float parallaxMultiplier, float positionX, float positionY) {
        this.textureResourceId = textureResourceId;
        this.width = width;
        this.height = height;
        this.originalWidth = width;
        this.originalHeight = height;
        this.parallaxMultiplier = parallaxMultiplier;
        this.positionX = positionX;
        this.positionY = positionY;
        this.originalPositionX = positionX;
        this.originalPositionY = positionY;
        initializeGeometry();
        // Texture loading is handled by TextureManager; caller should set textureId via setTextureId().
    }

    /**
     * Constructor using a SpriteConfig object for cleaner initialization.
     *
     * @param config the sprite configuration containing all initialization parameters
     */
    public Sprite(SpriteConfig config) {
        this(config.textureResourceId, config.width, config.height,
             config.parallaxMultiplier, config.positionX, config.positionY);
    }

    /**
     * Initialize vertex and texture coordinate buffers with default geometry.
     * Creates a unit square centered at the origin.
     */
    private void initializeGeometry() {
        // Define square vertices (centered at position, width/height on each side)
        float halfWidth = width / 2f;
        float halfHeight = height / 2f;

        float[] squareCoords = {
            positionX - halfWidth,  positionY + halfHeight, 0.0f,  // top left
            positionX - halfWidth,  positionY - halfHeight, 0.0f,  // bottom left
            positionX + halfWidth,  positionY + halfHeight, 0.0f,  // top right
            positionX + halfWidth,  positionY - halfHeight, 0.0f   // bottom right
        };

        ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);

        // Define texture coordinates (full texture mapping)
        float[] texCoords = {
            0.0f, 1.0f,  // top left
            0.0f, 0.0f,  // bottom left
            1.0f, 1.0f,  // top right
            1.0f, 0.0f   // bottom right
        };

        ByteBuffer tbb = ByteBuffer.allocateDirect(texCoords.length * 4);
        tbb.order(ByteOrder.nativeOrder());
        texCoordBuffer = tbb.asFloatBuffer();
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);

        // Initialize parallax multiplier buffer (one value per vertex)
        float[] parallaxMultipliers = {parallaxMultiplier, parallaxMultiplier, parallaxMultiplier, parallaxMultiplier};
        ByteBuffer pmbb = ByteBuffer.allocateDirect(parallaxMultipliers.length * 4);
        pmbb.order(ByteOrder.nativeOrder());
        parallaxMultiplierBuffer = pmbb.asFloatBuffer();
        parallaxMultiplierBuffer.put(parallaxMultipliers);
        parallaxMultiplierBuffer.position(0);
    }

    /**
     * Recalculates vertex coordinates based on current position and size.
     * Call this after changing position or size.
     */
    private void updateVertexBuffer() {
        float halfWidth = width / 2f;
        float halfHeight = height / 2f;

        float[] squareCoords = {
            positionX - halfWidth,  positionY + halfHeight, 0.0f,  // top left
            positionX - halfWidth,  positionY - halfHeight, 0.0f,  // bottom left
            positionX + halfWidth,  positionY + halfHeight, 0.0f,  // top right
            positionX + halfWidth,  positionY - halfHeight, 0.0f   // bottom right
        };

        vertexBuffer.position(0);
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);
    }

    /**
     * Set the position of the sprite in world-space coordinates.
     * Uses world-space units that match the worldHeight scale.
     * Position (0, 0) is the center of the screen.
     * Positive Y moves up, negative Y moves down.
     * X coordinates scale with aspect ratio.
     *
     * @param x the x position in world units
     * @param y the y position in world units
     */
    public void setPosition(float x, float y) {
        this.positionX = x;
        this.positionY = y;

        // Store as original position if this is the first time being set (both original positions are 0)
        // or if explicitly setting a new base position
        if (originalPositionX == 0f && originalPositionY == 0f && (x != 0f || y != 0f)) {
            originalPositionX = x;
            originalPositionY = y;
        }

        updateVertexBuffer();
    }

    /**
     * Reset the position back to its original value.
     * Used when disabling gyro scaling.
     */
    public void resetPosition() {
        this.positionX = originalPositionX;
        this.positionY = originalPositionY;
        updateVertexBuffer();
    }

    /**
     * Set the size of the sprite.
     *
     * @param width  the new width in world units
     * @param height the new height in world units
     */
    @SuppressWarnings("unused")
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        updateVertexBuffer();
    }

    /**
     * Set texture coordinates (for texture atlasing or partial texture use).
     *
     * @param texCoords array of 8 floats representing 4 texture coordinates (u, v pairs)
     */
    @SuppressWarnings("unused")
    public void setTextureCoordinates(float[] texCoords) {
        if (texCoords.length != 8) {
            Log.w(TAG, "Texture coordinates must be an array of 8 floats");
            return;
        }

        texCoordBuffer.position(0);
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);
    }

    /**
     * Get the current X position.
     */
    @SuppressWarnings("unused")
    public float getPositionX() {
        return positionX;
    }

    /**
     * Get the current Y position.
     */
    @SuppressWarnings("unused")
    public float getPositionY() {
        return positionY;
    }

    /**
     * Get the current width.
     */
    @SuppressWarnings("unused")
    public float getWidth() {
        return width;
    }

    /**
     * Get the current height.
     */
    @SuppressWarnings("unused")
    public float getHeight() {
        return height;
    }

    /**
     * Get the original width before any scaling.
     */
    @SuppressWarnings("unused")
    public float getOriginalWidth() {
        return originalWidth;
    }

    /**
     * Get the original height before any scaling.
     */
    @SuppressWarnings("unused")
    public float getOriginalHeight() {
        return originalHeight;
    }

    /**
     * Scale this sprite by a factor relative to its original dimensions.
     * For example, a factor of 1.2f will scale the sprite to 120% of its original size.
     * This is useful for expanding sprites when gyro motion is enabled.
     *
     * @param scaleFactor the scale factor to apply (1.0 = original size)
     */
    public void scaleFromOriginal(float scaleFactor) {
        this.width = originalWidth * scaleFactor;
        this.height = originalHeight * scaleFactor;
        updateVertexBuffer();
    }

    /**
     * Reset sprite size back to its original dimensions.
     */
    public void resetScale() {
        this.width = originalWidth;
        this.height = originalHeight;
        updateVertexBuffer();
    }

    /**
     * Get the texture resource id (original drawable id).
     */
    public int getTextureResourceId() {
        return textureResourceId;
    }

    /**
     * Set the GL texture ID that will be used for rendering. Typically obtained from TextureManager.
     */
    public void setTextureId(int textureId) {
        this.textureId = textureId;
    }

    /**
     * Get the GL texture ID assigned to this sprite.
     */
    public int getTextureId() {
        return textureId;
    }

    /**
     * Set the parallax multiplier for this sprite.
     * Controls how much this sprite responds to scroll offset (1.0 = full, 0.5 = half, etc.)
     *
     * @param multiplier the parallax multiplier value
     */
    public void setParallaxMultiplier(float multiplier) {
        this.parallaxMultiplier = multiplier;
        // Update all values in the buffer with the new multiplier
        float[] parallaxMultipliers = {multiplier, multiplier, multiplier, multiplier};
        parallaxMultiplierBuffer.position(0);
        parallaxMultiplierBuffer.put(parallaxMultipliers);
        parallaxMultiplierBuffer.position(0);
    }

    /**
     * Get the parallax multiplier for this sprite.
     */
    @SuppressWarnings("unused")
    public float getParallaxMultiplier() {
        return parallaxMultiplier;
    }

    /**
     * Set the alpha transparency value for this sprite.
     * Value should be between 0.0 (fully transparent) and 1.0 (fully opaque).
     *
     * @param alpha the alpha transparency value
     */
    public void setAlpha(float alpha) {
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    /**
     * Get the alpha transparency value for this sprite.
     */
    public float getAlpha() {
        return alpha;
    }

    /**
     * Accessors for renderer to use the geometry buffers.
     */
    public FloatBuffer getVertexBuffer() { return vertexBuffer; }
    public FloatBuffer getTexCoordBuffer() { return texCoordBuffer; }
    public FloatBuffer getParallaxMultiplierBuffer() { return parallaxMultiplierBuffer; }
    public int getVertexCount() { return VERTEX_COUNT; }

    /**
     * Destroy this sprite and release its CPU-side resources.
     * Texture deletion is handled by TextureManager.
     */
    public void destroy() {
        // Help GC by nulling buffers
        vertexBuffer = null;
        texCoordBuffer = null;
        parallaxMultiplierBuffer = null;
        Log.d(TAG, "Sprite destroyed (CPU resources released)");
    }
}
