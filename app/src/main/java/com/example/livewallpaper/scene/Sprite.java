package com.example.livewallpaper.scene;

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
    private FloatBuffer edgeLineBuffer;
    private FloatBuffer edgeLineParallaxMultiplierBuffer;
    private int textureId = 0;
    private final int textureResourceId;
    private final String name;
    private final String textureResource;

    // Sprite properties for positioning and sizing
    private float positionX;
    private float positionY;
    private float width;
    private float height;
    private float parallaxMultiplier;

    // Wipe animation handling
    private final SpriteWipe spriteWipe = new SpriteWipe();

    // Store original dimensions and positions for scaling
    private final float originalWidth;
    private final float originalHeight;
    private float originalPositionX;
    private float originalPositionY;

    // Store original texture coordinates for scaling
    private float[] originalTexCoordinates;
    private float textureScale = 1.0f;
    private float textureOffsetU = 0.0f;
    private float textureOffsetV = 0.0f;
    private float textureScaleFactor;  // The scale factor of the texture in world space, set at construction

    // Track gyro scaling state
    private boolean isGyroScaled = false;

    // Edge highlight toggle for debug/edit view
    private boolean showEdgeHighlight = false;

    /**
     * Constructor using a SpriteConfig object for cleaner initialization.
     * This is the primary constructor; all other initialization paths delegate here.
     *
     * @param config the sprite configuration containing all initialization parameters
     */
    public Sprite(SpriteData config) {
        this(config.textureResourceId, config.name, config.width, config.height,
             config.parallaxMultiplier, config.positionX, config.positionY, 1.0f, config.textureResource, config.texCoordinates);
    }

    /**
     * Constructor with parallax multiplier, position, and optional gyro scaling.
     * Supports all initialization scenarios.
     *
     * @param textureResourceId the drawable resource ID for the texture
     * @param name the name of the sprite for debugging
     * @param width the width in world units
     * @param height the height in world units
     * @param parallaxMultiplier the parallax multiplier (1.0 = full scroll, 0.5 = half, etc.)
     * @param positionX the x position in world units
     * @param positionY the y position in world units
     * @param gyroScaleFactor the gyro scale factor to apply (1.0 = no scaling, >1.0 = enlarged for gyro motion)
     * @param textureResource the texture resource name (e.g., "background", "player")
     * @param texCoordinates the texture coordinates array (8 floats) or null/empty for default
     */
    public Sprite(int textureResourceId, String name, float width, float height, float parallaxMultiplier, float positionX, float positionY, float gyroScaleFactor, String textureResource, float[] texCoordinates) {
        this.textureResourceId = textureResourceId;
        this.name = name;
        this.textureResource = textureResource;
        this.width = width;
        this.height = height;
        this.originalWidth = width;
        this.originalHeight = height;
        this.parallaxMultiplier = parallaxMultiplier;
        this.positionX = positionX;
        this.positionY = positionY;
        this.originalPositionX = positionX;
        this.originalPositionY = positionY;
        // Initialize textureScaleFactor to 1.0 at construction - the texture's base size in world space
        this.textureScaleFactor = 1.0f;
        initializeGeometry(texCoordinates);
        // Apply gyro scaling if provided
        if (gyroScaleFactor > 1.0f) {
            applyGyroScaling(gyroScaleFactor);
        }
        // Texture loading is handled by TextureManager; caller should set textureId via setTextureId().
    }


    /**
     * Initialize vertex and texture coordinate buffers with default geometry.
     * Creates a unit square centered at the origin.
     *
     * @param customTexCoordinates optional custom texture coordinates (8 floats) or null for default
     */
    private void initializeGeometry(float[] customTexCoordinates) {
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

        // Define texture coordinates (use custom if provided and non-empty, otherwise use default)
        float[] texCoords;
        if (customTexCoordinates != null && customTexCoordinates.length == 8) {
            texCoords = customTexCoordinates;
        } else {
            // Default: full texture mapping
            texCoords = new float[] {
                0.0f, 1.0f,  // top left
                0.0f, 0.0f,  // bottom left
                1.0f, 1.0f,  // top right
                1.0f, 0.0f   // bottom right
            };
        }

        // Store original texture coordinates for scaling
        this.originalTexCoordinates = texCoords.clone();

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

        // Initialize edge line buffer for highlight outline (5 vertices to draw a closed box)
        float[] edgeLineCoords = {
            positionX - halfWidth,  positionY + halfHeight, 0.0f,  // top left
            positionX + halfWidth,  positionY + halfHeight, 0.0f,  // top right
            positionX + halfWidth,  positionY - halfHeight, 0.0f,  // bottom right
            positionX - halfWidth,  positionY - halfHeight, 0.0f,  // bottom left
            positionX - halfWidth,  positionY + halfHeight, 0.0f   // back to top left
        };
        ByteBuffer ebb = ByteBuffer.allocateDirect(edgeLineCoords.length * 4);
        ebb.order(ByteOrder.nativeOrder());
        edgeLineBuffer = ebb.asFloatBuffer();
        edgeLineBuffer.put(edgeLineCoords);
        edgeLineBuffer.position(0);

        // Initialize parallax multiplier buffer for edge lines (5 vertices)
        float[] edgeLineParallaxMultipliers = {parallaxMultiplier, parallaxMultiplier, parallaxMultiplier, parallaxMultiplier, parallaxMultiplier};
        ByteBuffer epmbb = ByteBuffer.allocateDirect(edgeLineParallaxMultipliers.length * 4);
        epmbb.order(ByteOrder.nativeOrder());
        edgeLineParallaxMultiplierBuffer = epmbb.asFloatBuffer();
        edgeLineParallaxMultiplierBuffer.put(edgeLineParallaxMultipliers);
        edgeLineParallaxMultiplierBuffer.position(0);
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

        // Update edge line buffer as well
        float[] edgeLineCoords = {
            positionX - halfWidth,  positionY + halfHeight, 0.0f,  // top left
            positionX + halfWidth,  positionY + halfHeight, 0.0f,  // top right
            positionX + halfWidth,  positionY - halfHeight, 0.0f,  // bottom right
            positionX - halfWidth,  positionY - halfHeight, 0.0f,  // bottom left
            positionX - halfWidth,  positionY + halfHeight, 0.0f   // back to top left
        };
        edgeLineBuffer.position(0);
        edgeLineBuffer.put(edgeLineCoords);
        edgeLineBuffer.position(0);
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
     * Set only the X position of this sprite.
     *
     * @param x the x position in world units
     */
    public void setPositionX(float x) {
        setPosition(x, this.positionY);
    }

    /**
     * Set only the Y position of this sprite.
     *
     * @param y the y position in world units
     */
    public void setPositionY(float y) {
        setPosition(this.positionX, y);
    }

    /**
     * Set the width of this sprite.
     * Updates texture coordinates to maintain the texture's original aspect ratio.
     * The offset is naturally clamped by the window bounds in updateTextureCoordinatesForAspectRatio().
     *
     * @param width the width in world units
     */
    public void setWidth(float width) {
        this.width = width;
        updateVertexBuffer();
        updateTextureCoordinatesForAspectRatio();
    }

    /**
     * Set the height of this sprite.
     * Updates texture coordinates to maintain the texture's original aspect ratio.
     * The offset is naturally clamped by the window bounds in updateTextureCoordinatesForAspectRatio().
     *
     * @param height the height in world units
     */
    public void setHeight(float height) {
        this.height = height;
        updateVertexBuffer();
        updateTextureCoordinatesForAspectRatio();
    }

    /**
     * Set the parallax multiplier for this sprite.
     * Controls how much the sprite moves relative to scrolling (1.0 = full scroll, 0.5 = half, etc.)
     *
     * @param parallaxMultiplier the parallax multiplier value
     */
    public void setParallaxMultiplier(float parallaxMultiplier) {
        this.parallaxMultiplier = parallaxMultiplier;

        // Update the parallax multiplier buffer for rendering
        if (parallaxMultiplierBuffer != null) {
            float[] parallaxMultipliers = {parallaxMultiplier, parallaxMultiplier, parallaxMultiplier, parallaxMultiplier};
            parallaxMultiplierBuffer.clear();
            parallaxMultiplierBuffer.put(parallaxMultipliers);
            parallaxMultiplierBuffer.position(0);
        }

        // Update the edge line parallax multiplier buffer as well (5 vertices)
        if (edgeLineParallaxMultiplierBuffer != null) {
            float[] edgeLineParallaxMultipliers = {parallaxMultiplier, parallaxMultiplier, parallaxMultiplier, parallaxMultiplier, parallaxMultiplier};
            edgeLineParallaxMultiplierBuffer.clear();
            edgeLineParallaxMultiplierBuffer.put(edgeLineParallaxMultipliers);
            edgeLineParallaxMultiplierBuffer.position(0);
        }
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
     * Internal method to apply gyro scaling during sprite initialization.
     * Tracks the gyro scaling state so sprites know they're already scaled.
     *
     * @param scaleFactor the gyro scale factor (>1.0 to enlarge)
     */
    private void applyGyroScaling(float scaleFactor) {
        this.isGyroScaled = true;
        // Scale the sprite size
        scaleFromOriginal(scaleFactor);
        // Scale the position away from center (0, 0) to maintain relative spacing
        float scaledX = originalPositionX * scaleFactor;
        float scaledY = originalPositionY * scaleFactor;
        this.positionX = scaledX;
        this.positionY = scaledY;
        updateVertexBuffer();
    }

    /**
     * Check if this sprite is already scaled for gyro motion.
     */
    public boolean isGyroScaled() {
        return isGyroScaled;
    }

    /**
     * Reset sprite size back to its original dimensions.
     */
    public void resetScale() {
        this.width = originalWidth;
        this.height = originalHeight;
        this.isGyroScaled = false;
        updateVertexBuffer();
    }

    /**
     * Scale the texture coordinates relative to their original values.
     * For example, a scale factor of 2.0 will double the texture size.
     * A scale factor of 0.5 will halve the texture size.
     * The texture will be centered on the sprite.
     * This preserves any existing texture offsets from touch dragging.
     *
     * @param scaleFactor the scale factor to apply (1.0 = original size, >1.0 = enlarged, <1.0 = shrunk)
     */
    public void setTextureScale(float scaleFactor) {
        this.textureScale = Math.max(0.1f, scaleFactor); // Clamp to minimum of 0.1x
        updateTextureCoordinatesForAspectRatio();
    }


    /**
     * Get the current texture scale factor.
     */
    public float getTextureScale() {
        return textureScale;
    }

    /**
     * Get the current texture U offset.
     */
    public float getTextureOffsetU() {
        return textureOffsetU;
    }

    /**
     * Get the current texture V offset.
     */
    public float getTextureOffsetV() {
        return textureOffsetV;
    }

    /**
     * Set the texture U offset to an absolute value.
     * This is useful for applying saved texture offset values.
     *
     * @param offsetU the absolute U offset value
     */
    public void setTextureOffsetU(float offsetU) {
        this.textureOffsetU = offsetU;
        updateTextureCoordinatesForAspectRatio();
    }

    /**
     * Set the texture V offset to an absolute value.
     * This is useful for applying saved texture offset values.
     *
     * @param offsetV the absolute V offset value
     */
    public void setTextureOffsetV(float offsetV) {
        this.textureOffsetV = offsetV;
        updateTextureCoordinatesForAspectRatio();
    }

    /**
     * Offset the texture coordinates by the given amounts.
     * This allows for panning the texture across the sprite via touch input.
     * The offset is clamped to prevent texture stretching by limiting it to valid bounds.
     *
     * @param offsetU the offset in U (horizontal) direction
     * @param offsetV the offset in V (vertical) direction
     */
    public void offsetTextureCoordinates(float offsetU, float offsetV) {
        // Calculate the growth scale: how much has the sprite grown from its original size?
        float widthGrowth = width / originalWidth;
        float heightGrowth = height / originalHeight;
        float growthScale = Math.max(widthGrowth, heightGrowth);

        // Calculate texture dimensions in world space, accounting for sprite growth
        float textureWidthInWorld = originalWidth * textureScaleFactor * growthScale;
        float textureHeightInWorld = originalHeight * textureScaleFactor * growthScale;

        // Calculate how much of the texture is visible in the sprite
        float uScale = width / textureWidthInWorld;
        float vScale = height / textureHeightInWorld;

        // Calculate the visible window size in UV space after texture scale zoom
        // Apply texture scale uniformly (1.0 / textureScale) to both dimensions
        float baseWindowSize = 1.0f / textureScale;
        float zoomedWidth = baseWindowSize * uScale;
        float zoomedHeight = baseWindowSize * vScale;

        // Calculate the maximum offset allowed before hitting the texture bounds [0, 1]
        // The visible window must stay within [0, 1], so max offset is when the window edge touches the boundary
        float maxUOffset = (1.0f - zoomedWidth) * 0.5f;
        float maxVOffset = (1.0f - zoomedHeight) * 0.5f;

        // Apply the offset and clamp it directly
        this.textureOffsetU += offsetU;
        this.textureOffsetV += offsetV;

        // Clamp to valid range
        this.textureOffsetU = Math.max(-maxUOffset, Math.min(maxUOffset, this.textureOffsetU));
        this.textureOffsetV = Math.max(-maxVOffset, Math.min(maxVOffset, this.textureOffsetV));

        Log.d("Sprite", "offsetTextureCoordinates - offsetU in: " + offsetU + ", offsetV in: " + offsetV +
              ", max offsets: U=" + maxUOffset + " V=" + maxVOffset +
              ", clamped offset: U=" + this.textureOffsetU + " V=" + this.textureOffsetV);

        updateTextureCoordinatesForAspectRatio();
    }


    /**
     * Adjust texture coordinates to maintain the texture's independent 1:1 aspect ratio
     * when the sprite's dimensions change. The texture grows uniformly with the sprite;
     * when the sprite dimensions increase, the texture size increases proportionally so
     * that both dimensions scale uniformly and the texture maintains its 1:1 aspect ratio.
     */
    private void updateTextureCoordinatesForAspectRatio() {
        if (originalTexCoordinates == null) {
            return;
        }

        // Calculate the growth scale: how much has the sprite grown from its original size?
        // We use the maximum of width and height growth to ensure uniform scaling
        float widthGrowth = width / originalWidth;
        float heightGrowth = height / originalHeight;
        float growthScale = Math.max(widthGrowth, heightGrowth);

        // The texture's effective size in world space grows with the sprite
        // This ensures the texture maintains its 1:1 aspect ratio while scaling uniformly
        float textureWidthInWorld = originalWidth * textureScaleFactor * growthScale;
        float textureHeightInWorld = originalHeight * textureScaleFactor * growthScale;

        // Calculate the visible portion of the texture in each dimension independently
        // This allows the texture to maintain its aspect ratio while the sprite aspect ratio changes
        float uScale = width / textureWidthInWorld;      // How much of texture width is visible
        float vScale = height / textureHeightInWorld;    // How much of texture height is visible

        // Calculate the center point for texture scaling
        float centerU = 0.5f;
        float centerV = 0.5f;

        // Calculate the visible window size in UV space after texture scale zoom
        // Apply texture scale to both dimensions independently to preserve texture aspect ratio
        float baseWindowSizeU = 1.0f / textureScale;
        float baseWindowSizeV = 1.0f / textureScale;
        float windowSizeU = baseWindowSizeU * uScale;
        float windowSizeV = baseWindowSizeV * vScale;

        // Calculate the base window position (centered)
        float halfWindowU = windowSizeU * 0.5f;
        float halfWindowV = windowSizeV * 0.5f;
        float uMin = centerU - halfWindowU;
        float uMax = centerU + halfWindowU;
        float vMin = centerV - halfWindowV;
        float vMax = centerV + halfWindowV;

        // Apply offset to move the visible window
        uMin += textureOffsetU;
        uMax += textureOffsetU;
        vMin += textureOffsetV;
        vMax += textureOffsetV;

        // Clamp the window to [0, 1], adjusting offset to keep the window within bounds
        if (uMin < 0f) {
            float overshoot = -uMin;
            textureOffsetU += overshoot;
            uMin = 0f;
            uMax = uMax + overshoot;
        } else if (uMax > 1f) {
            float overshoot = uMax - 1f;
            textureOffsetU -= overshoot;
            uMax = 1f;
            uMin = uMin - overshoot;
        }

        if (vMin < 0f) {
            float overshoot = -vMin;
            textureOffsetV += overshoot;
            vMin = 0f;
            vMax = vMax + overshoot;
        } else if (vMax > 1f) {
            float overshoot = vMax - 1f;
            textureOffsetV -= overshoot;
            vMax = 1f;
            vMin = vMin - overshoot;
        }

        // Final clamp to ensure we stay in [0, 1]
        uMin = Math.max(0f, Math.min(1f, uMin));
        uMax = Math.max(0f, Math.min(1f, uMax));
        vMin = Math.max(0f, Math.min(1f, vMin));
        vMax = Math.max(0f, Math.min(1f, vMax));

        // Build final texture coordinates by mapping sprite corners to the visible window
        float[] texCoords = new float[] {
            uMin, vMax,  // top left
            uMin, vMin,  // bottom left
            uMax, vMax,  // top right
            uMax, vMin   // bottom right
        };

        // Update the texture coordinate buffer
        if (texCoordBuffer != null) {
            texCoordBuffer.clear();
            texCoordBuffer.put(texCoords);
            texCoordBuffer.position(0);
        }
    }

    public float getPositionX() {
        return positionX;
    }
    public float getPositionY() {
        return positionY;
    }
    public float getWidth() {
        return width;
    }
    public float getHeight() {
        return height;
    }
    public String getName() {
        return name;
    }
    public String getTextureResource() {
        return textureResource;
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
     * Set the wipe transition progress for this sprite.
     * Value should be between 0.0 (no wipe) and 1.0 (fully wiped).
     *
     * @param progress the wipe progress value
     */
    public void setWipeProgress(float progress) {
        spriteWipe.setWipeProgress(progress);
    }

    /**
     * Get the wipe transition progress for this sprite.
     */
    public float getWipeProgress() {
        return spriteWipe.getWipeProgress();
    }

    /**
     * Get the wipe direction for this sprite.
     */
    public float getWipeDirection() {
        return spriteWipe.getWipeDirection();
    }

    /**
     * Reset wipe state to default (no wipe effect, fully visible).
     */
    public void resetWipe() {
        spriteWipe.resetWipe();
    }

    /**
     * Mark this sprite as wiping out (fading away).
     */
    public void setWipingOut(boolean wipingOut) {
        spriteWipe.setWipingOut(wipingOut);
    }

    /**
     * Check if this sprite is wiping out.
     */
    public boolean isWipingOut() {
        return spriteWipe.isWipingOut();
    }

    /**
     * Mark this sprite as wiping in (fading in).
     */
    public void setWipingIn(boolean wipingIn) {
        spriteWipe.setWipingIn(wipingIn);
    }

    /**
     * Check if this sprite is wiping in.
     */
    public boolean isWipingIn() {
        return spriteWipe.isWipingIn();
    }

    /**
     * Check if this sprite is transitioning (either wiping in or out).
     */
    public boolean isTransitioning() {
        return spriteWipe.isTransitioning();
    }

    /**
     * Accessors for renderer to use the geometry buffers.
     */
    public FloatBuffer getVertexBuffer() { return vertexBuffer; }
    public FloatBuffer getTexCoordBuffer() { return texCoordBuffer; }
    public FloatBuffer getParallaxMultiplierBuffer() { return parallaxMultiplierBuffer; }
    public FloatBuffer getEdgeLineBuffer() { return edgeLineBuffer; }
    public FloatBuffer getEdgeLineParallaxMultiplierBuffer() { return edgeLineParallaxMultiplierBuffer; }
    public int getVertexCount() { return VERTEX_COUNT; }
    public int getEdgeLineVertexCount() { return 5; }

    /**
     * Get the parallax multiplier value for this sprite.
     * Used for draw order sorting (lower values = further back, drawn first).
     */
    public float getParallaxMultiplier() { return parallaxMultiplier; }

    /**
     * Set whether to show the edge highlight for this sprite.
     * @param show true to show green outline, false to hide
     */
    public void setShowEdgeHighlight(boolean show) {
        this.showEdgeHighlight = show;
    }

    /**
     * Check if edge highlight is enabled for this sprite.
     */
    public boolean isShowEdgeHighlight() {
        return showEdgeHighlight;
    }

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
