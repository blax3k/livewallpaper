package com.example.livewallpaper.scene.models;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.example.livewallpaper.scene.TextureCoordinateCalculator;
import com.example.livewallpaper.scene.TextureEditState;
import com.example.livewallpaper.scene.animation.SpriteWipe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Represents a single sprite that can be rendered with an independent position, size, and texture.
 * This class now only owns geometry buffers and sprite properties; texture loading and draw calls
 * are delegated to TextureManager and SpriteRenderer respectively.
 */
public class Sprite implements Parcelable {
    private static final String TAG = "Sprite";
    private static final int VERTEX_COUNT = 4;

    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;
    private FloatBuffer normalizedPositionBuffer;
    private FloatBuffer parallaxMultiplierBuffer;
    private FloatBuffer edgeLineBuffer;
    private FloatBuffer edgeLineParallaxMultiplierBuffer;
    private int textureId = 0;
    private int textureResourceId;
    private String name;
    private String textureResource;

    // Sprite properties for positioning and sizing
    private float positionX;
    private float positionY;
    private float width;
    private float height;
    private float parallaxMultiplier;

    // Wipe animation handling
    private final SpriteWipe spriteWipe = new SpriteWipe();

    // Store original dimensions and positions for scaling
    private float originalWidth;
    private float originalHeight;
    private float originalPositionX;
    private float originalPositionY;

    // Store texture editing baseline dimensions (reference dimensions for texture coordinate calculations)
    // These are set once when entering a texture editing session and remain fixed during editing
    // This ensures texture coordinates are calculated relative to a stable baseline, preventing stretching
    // as the sprite dimensions change via sliders
    private float textureEditingBaselineWidth;
    private float textureEditingBaselineHeight;

    // Store original texture coordinates for scaling
    private float[] originalTexCoordinates;
    private final float textureScaleFactor;  // The scale factor of the texture in world space, set at construction

    // Store the current texture edit state (scale and offsets) for accurate retrieval
    private TextureEditState currentTextureEditState = new TextureEditState(1.0f, 0.0f, 0.0f);

    // Track gyro scaling state
    private boolean isGyroScaled = false;

    // Edge highlight toggle for debug/edit view
    private boolean showEdgeHighlight = false;

    // Flag to force sprite to render at (0, 0) regardless of actual position
    // Used when editing texture in EditTextureActivity
    private boolean positionAtZero = false;

    /**
     * Constructor using a SpriteConfig object for cleaner initialization.
     * This is the primary constructor; all other initialization paths delegate here.
     *
     * @param config the sprite configuration containing all initialization parameters
     */
    public Sprite(SpriteData config) {
        this(config.textureResourceId, config.name, config.width, config.height,
                config.parallaxMultiplier, config.positionX, config.positionY, 1.0f, config.textureResource,
                config.texCoordinates != null ? config.texCoordinates.clone() : null);

        // Initialize texture edit state to default (scale/offset will be derived from texture coordinates if needed)
        this.currentTextureEditState = new TextureEditState(1.0f, 0.0f, 0.0f);
    }

    /**
     * Constructor with parallax multiplier, position, and optional gyro scaling.
     * Supports all initialization scenarios.
     *
     * @param textureResourceId  the drawable resource ID for the texture
     * @param name               the name of the sprite for debugging
     * @param width              the width in world units
     * @param height             the height in world units
     * @param parallaxMultiplier the parallax multiplier (1.0 = full scroll, 0.5 = half, etc.)
     * @param positionX          the x position in world units
     * @param positionY          the y position in world units
     * @param gyroScaleFactor    the gyro scale factor to apply (1.0 = no scaling, >1.0 = enlarged for gyro motion)
     * @param textureResource    the texture resource name (e.g., "background", "player")
     * @param texCoordinates     the texture coordinates array (8 floats) or null/empty for default
     */
    public Sprite(int textureResourceId, String name, float width, float height, float parallaxMultiplier, float positionX, float positionY, float gyroScaleFactor, String textureResource, float[] texCoordinates) {
        this.textureResourceId = textureResourceId;
        this.name = name;
        this.textureResource = textureResource;
        this.width = width;
        this.height = height;
        this.originalWidth = width;
        this.originalHeight = height;
        // Initialize texture editing baseline to match original dimensions
        this.textureEditingBaselineWidth = width;
        this.textureEditingBaselineHeight = height;
        this.parallaxMultiplier = parallaxMultiplier;
        this.positionX = positionX;
        this.positionY = positionY;
        this.originalPositionX = positionX;
        this.originalPositionY = positionY;
        // Initialize textureScaleFactor to 1.0 at construction - the texture's base size in world space
        this.textureScaleFactor = 1.0f;
        initializeGeometry(texCoordinates);
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
                positionX - halfWidth, positionY + halfHeight, 0.0f,  // top left
                positionX - halfWidth, positionY - halfHeight, 0.0f,  // bottom left
                positionX + halfWidth, positionY + halfHeight, 0.0f,  // top right
                positionX + halfWidth, positionY - halfHeight, 0.0f   // bottom right
        };

        ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);

        // Define texture coordinates (use custom if provided and non-empty, otherwise use default)
        float[] texCoords;
        if (customTexCoordinates != null && customTexCoordinates.length == 8) {
            // ALWAYS clone the passed-in coordinates to avoid sharing references between sprites
            texCoords = customTexCoordinates.clone();
        } else {
            // Default: full texture mapping
            texCoords = new float[]{
                    0.0f, 1.0f,  // top left
                    0.0f, 0.0f,  // bottom left
                    1.0f, 1.0f,  // top right
                    1.0f, 0.0f   // bottom right
            };
        }

        // Store original texture coordinates for scaling (always make a fresh clone)
        this.originalTexCoordinates = texCoords.clone();

        ByteBuffer tbb = ByteBuffer.allocateDirect(texCoords.length * 4);
        tbb.order(ByteOrder.nativeOrder());
        texCoordBuffer = tbb.asFloatBuffer();
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);

        // Initialize normalized position buffer (normalized 0-1 coordinates within sprite bounds)
        float[] normalizedPositions = {
                0.0f, 1.0f,  // top left
                0.0f, 0.0f,  // bottom left
                1.0f, 1.0f,  // top right
                1.0f, 0.0f   // bottom right
        };
        ByteBuffer npbb = ByteBuffer.allocateDirect(normalizedPositions.length * 4);
        npbb.order(ByteOrder.nativeOrder());
        normalizedPositionBuffer = npbb.asFloatBuffer();
        normalizedPositionBuffer.put(normalizedPositions);
        normalizedPositionBuffer.position(0);

        // Initialize parallax multiplier buffer (one value per vertex)
        float[] parallaxMultipliers = {parallaxMultiplier, parallaxMultiplier, parallaxMultiplier, parallaxMultiplier};
        ByteBuffer pmbb = ByteBuffer.allocateDirect(parallaxMultipliers.length * 4);
        pmbb.order(ByteOrder.nativeOrder());
        parallaxMultiplierBuffer = pmbb.asFloatBuffer();
        parallaxMultiplierBuffer.put(parallaxMultipliers);
        parallaxMultiplierBuffer.position(0);

        // Initialize edge line buffer for highlight outline (5 vertices to draw a closed box)
        float[] edgeLineCoords = {
                positionX - halfWidth, positionY + halfHeight, 0.0f,  // top left
                positionX + halfWidth, positionY + halfHeight, 0.0f,  // top right
                positionX + halfWidth, positionY - halfHeight, 0.0f,  // bottom right
                positionX - halfWidth, positionY - halfHeight, 0.0f,  // bottom left
                positionX - halfWidth, positionY + halfHeight, 0.0f   // back to top left
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

    public void updateVertexBuffer() {
        updateVertexBuffer(1.0f);
    }

    /**
     * Recalculates vertex coordinates based on current position and size.
     * Call this after changing position or size.
     */
    private void updateVertexBuffer(float scaleFactor) {
        float halfWidth = width / 2f;
        float halfHeight = height / 2f;

        // Use (0, 0) if positionAtZero flag is true, otherwise use actual position
        float effectivePositionX = positionAtZero ? 0.0f : positionX;
        float effectivePositionY = positionAtZero ? 0.0f : positionY;

        float scaledPositionX = effectivePositionX * scaleFactor;
        float scaledPositionY = effectivePositionY * scaleFactor;

        float[] squareCoords = {
                scaledPositionX - halfWidth, scaledPositionY + halfHeight, 0.0f,  // top left
                scaledPositionX - halfWidth, scaledPositionY - halfHeight, 0.0f,  // bottom left
                scaledPositionX + halfWidth, scaledPositionY + halfHeight, 0.0f,  // top right
                scaledPositionX + halfWidth, scaledPositionY - halfHeight, 0.0f   // bottom right
        };

        vertexBuffer.position(0);
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);

        // Update edge line buffer as well
        float[] edgeLineCoords = {
                scaledPositionX - halfWidth, scaledPositionY + halfHeight, 0.0f,  // top left
                scaledPositionX + halfWidth, scaledPositionY + halfHeight, 0.0f,  // top right
                scaledPositionX + halfWidth, scaledPositionY - halfHeight, 0.0f,  // bottom right
                scaledPositionX - halfWidth, scaledPositionY - halfHeight, 0.0f,  // bottom left
                scaledPositionX - halfWidth, scaledPositionY + halfHeight, 0.0f   // back to top left
        };
        edgeLineBuffer.position(0);
        edgeLineBuffer.put(edgeLineCoords);
        edgeLineBuffer.position(0);
    }

    public void setPosition(float x, float y) {
        setPosition(x, y, 1.0f);
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
    public void setPosition(float x, float y, float scaleFactor) {

        this.positionX = x;
        this.positionY = y;

        // Store as original position if this is the first time being set (both original positions are 0)
        // or if explicitly setting a new base position
        if (originalPositionX == 0f && originalPositionY == 0f && (x != 0f || y != 0f)) {
            originalPositionX = x;
            originalPositionY = y;
        }

        updateVertexBuffer(scaleFactor);
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
     * Set the position and update the original position (for user edits in the UI).
     * Use this when the user manually changes the sprite position to ensure
     * the new position is saved to JSON correctly.
     *
     * @param x the x position in world units
     * @param y the y position in world units
     */
    public void setPositionAndUpdateOriginal(float x, float y) {
        this.positionX = x;
        this.positionY = y;
        this.originalPositionX = x;
        this.originalPositionY = y;
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
     * Set only the X position and update the original position (for user edits in the UI).
     * Use this when the user manually changes the sprite X position to ensure
     * the new position is saved to JSON correctly.
     *
     * @param x the x position in world units
     */
    public void setPositionXAndUpdateOriginal(float x) {
        setPositionAndUpdateOriginal(x, this.positionY);
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
     * Set only the Y position and update the original position (for user edits in the UI).
     * Use this when the user manually changes the sprite Y position to ensure
     * the new position is saved to JSON correctly.
     *
     * @param y the y position in world units
     */
    public void setPositionYAndUpdateOriginal(float y) {
        setPositionAndUpdateOriginal(this.positionX, y);
    }

    /**
     * Set the width of this sprite.
     *
     * @param width the width in world units
     */
    public void setWidth(float width) {
        this.width = width;
        updateVertexBuffer();
    }

    /**
     * Set the height of this sprite.
     *
     * @param height the height in world units
     */
    public void setHeight(float height) {
        this.height = height;
        updateVertexBuffer();
    }

    /**
     * Set width and update the original width (for user edits in the UI).
     * Use this when the user manually changes the sprite dimensions to ensure
     * the new dimensions are saved to JSON correctly.
     *
     * @param width the width in world units
     */
    public void setWidthAndUpdateOriginal(float width) {
        this.width = width;
        // Cast to float to match field type
        this.originalWidth = width;
        updateVertexBuffer();
    }

    /**
     * Set height and update the original height (for user edits in the UI).
     * Use this when the user manually changes the sprite dimensions to ensure
     * the new dimensions are saved to JSON correctly.
     *
     * @param height the height in world units
     */
    public void setHeightAndUpdateOriginal(float height) {
        this.height = height;
        // Cast to float to match field type
        this.originalHeight = height;
        updateVertexBuffer();
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
     * Set whether this sprite has been scaled for gyro motion.
     * Used during transitions to preserve gyro scaling state.
     */
    public void setGyroScaled(boolean gyroScaled) {
        this.isGyroScaled = gyroScaled;
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
    public float getOriginalWidth() {
        return originalWidth;
    }
    public float getOriginalHeight() {
        return originalHeight;
    }
    public float getOriginalPositionX() {
        return originalPositionX;
    }
    public float getOriginalPositionY() {
        return originalPositionY;
    }

    /**
     * Get the texture editing baseline width.
     * Used for texture coordinate calculations during editing sessions.
     */
    public float getTextureEditingBaselineWidth() {
        return textureEditingBaselineWidth;
    }

    /**
     * Get the texture editing baseline height.
     * Used for texture coordinate calculations during editing sessions.
     */
    public float getTextureEditingBaselineHeight() {
        return textureEditingBaselineHeight;
    }

    /**
     * Set the texture editing baseline dimensions.
     * Call this when entering an editing session to establish the base dimensions for texture coordinate calculations.
     * These dimensions remain fixed during editing to prevent texture stretching as sprite dimensions change.
     *
     * @param baselineWidth the width to use as the baseline for this editing session
     * @param baselineHeight the height to use as the baseline for this editing session
     */
    public void setTextureEditingBaseline(float baselineWidth, float baselineHeight) {
        this.textureEditingBaselineWidth = baselineWidth;
        this.textureEditingBaselineHeight = baselineHeight;
        Log.d(TAG, "Texture editing baseline set to: " + baselineWidth + " x " + baselineHeight);
    }

    public String getName() {
        return name;
    }

    /**
     * Set the sprite name.
     * Use this to rename a sprite.
     *
     * @param name the new name for the sprite
     */
    public void setName(String name) {
        this.name = name;
        Log.d(TAG, "Sprite name changed to: " + name);
    }

    public String getTextureResource() {
        return textureResource;
    }
    /**
     * Set the texture resource name.
     * Use this when changing the sprite's texture to a different resource.
     *
     * @param textureResource the name of the texture resource (e.g., "background", "player")
     */
    public void setTextureResource(String textureResource) {
        this.textureResource = textureResource;
    }
    /**
     * Get the texture resource id (original drawable id).
     */
    public int getTextureResourceId() {
        return textureResourceId;
    }
    /**
     * Set the texture resource ID.
     * Use this when changing the sprite's texture to a different drawable resource ID.
     *
     * @param textureResourceId the drawable resource ID
     */
    public void setTextureResourceId(int textureResourceId) {
        this.textureResourceId = textureResourceId;
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
     * Get the original texture coordinates as a float array (8 floats).
     * These are the unmodified base coordinates before any texture editing transformations.
     * Use this when you need the baseline coordinates for texture calculations.
     *
     * @return an array of 8 floats representing the original texture coordinates
     */
    public float[] getOriginalTextureCoordinates() {
        if (originalTexCoordinates == null) {
            return null;
        }
        // Return a clone to prevent external modification
        return originalTexCoordinates.clone();
    }

    /**
     * Get the current texture coordinates as a float array (8 floats).
     * Returns the current state of the texture coordinate buffer.
     *
     * @return an array of 8 floats representing the texture coordinates
     */
    public float[] getTextureCoordinates() {
        if (texCoordBuffer == null) {
            return null;
        }
        float[] texCoords = new float[8];
        texCoordBuffer.get(texCoords);
        texCoordBuffer.position(0);  // Reset position for rendering
        return texCoords;
    }

    /**
     * Update texture coordinates based on texture editing state.
     * This method is called from EditTextureActivity when texture scale, offset, or sprite dimensions change.
     *
     * @param textureEditState the current texture editing state (scale and offsets)
     */
    public void updateTextureCoordinates(TextureEditState textureEditState) {
        // Store the texture edit state for accurate retrieval later
        this.currentTextureEditState = new TextureEditState(
                textureEditState.getTextureScale(),
                textureEditState.getTextureOffsetU(),
                textureEditState.getTextureOffsetV()
        );

        TextureCoordinateCalculator.updateTextureCoordinates(
                texCoordBuffer,
                originalTexCoordinates,
                textureEditState.getTextureScale(),
                textureEditState.getOffsets(),
                width,
                height,
                textureEditingBaselineWidth,
                textureEditingBaselineHeight,
                textureScaleFactor
        );
    }

    /**
     * Accessors for renderer to use the geometry buffers.
     */
    public FloatBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public FloatBuffer getTexCoordBuffer() {
        return texCoordBuffer;
    }

    public FloatBuffer getNormalizedPositionBuffer() {
        return normalizedPositionBuffer;
    }

    public FloatBuffer getParallaxMultiplierBuffer() {
        return parallaxMultiplierBuffer;
    }

    public FloatBuffer getEdgeLineBuffer() {
        return edgeLineBuffer;
    }

    public FloatBuffer getEdgeLineParallaxMultiplierBuffer() {
        return edgeLineParallaxMultiplierBuffer;
    }

    public int getVertexCount() {
        return VERTEX_COUNT;
    }

    public int getEdgeLineVertexCount() {
        return 5;
    }

    /**
     * Get the parallax multiplier value for this sprite.
     * Used for draw order sorting (lower values = further back, drawn first).
     */
    public float getParallaxMultiplier() {
        return parallaxMultiplier;
    }

    /**
     * Set whether to show the edge highlight for this sprite.
     *
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
     * Set whether the sprite should render at position (0, 0) instead of its actual position.
     * Use this when editing the sprite's texture to center it on screen.
     *
     * @param positionAtZero true to render at (0, 0), false to use actual position
     */
    public void setPositionAtZero(boolean positionAtZero) {
        this.positionAtZero = positionAtZero;
        updateVertexBuffer();
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

    /**
     * Set the texture coordinates directly from a float array.
     * This is the primary way to update texture coordinates - all texture state derives from these values.
     * This method updates BOTH the buffer AND the original coordinates to keep them in sync.
     *
     * @param texCoords an array of 8 floats representing the texture coordinates
     */
    public void setTextureCoordinates(float[] texCoords) {
        if (texCoords == null || texCoords.length != 8 || texCoordBuffer == null) {
            Log.d(TAG, "Cannot set texture coordinates - invalid input");
            return;
        }
        texCoordBuffer.clear();
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);

        // IMPORTANT: Also update originalTexCoordinates to keep them in sync
        // This ensures that when texture coordinates are recalculated (e.g., in fullscreen preview),
        // they use the correct baseline instead of stale coordinates
        this.originalTexCoordinates = texCoords.clone();

        Log.d(TAG, "Texture coordinates updated");
    }

    // ==================== Parcelable Implementation ====================

    protected Sprite(Parcel in) {
        textureResourceId = in.readInt();
        name = in.readString();
        textureResource = in.readString();
        width = in.readFloat();
        height = in.readFloat();
        parallaxMultiplier = in.readFloat();
        positionX = in.readFloat();
        positionY = in.readFloat();
        originalWidth = in.readFloat();
        originalHeight = in.readFloat();
        originalPositionX = in.readFloat();
        originalPositionY = in.readFloat();
        textureEditingBaselineWidth = in.readFloat();
        textureEditingBaselineHeight = in.readFloat();

        // Read texture coordinates
        int texCoordLength = in.readInt();
        if (texCoordLength > 0) {
            originalTexCoordinates = new float[texCoordLength];
            in.readFloatArray(originalTexCoordinates);
        }

        // Initialize textureScaleFactor (final field) to 1.0f (default value)
        // This matches the behavior of regular constructors
        this.textureScaleFactor = 1.0f;

        // Initialize geometry with the deserialized texture coordinates
        initializeGeometry(originalTexCoordinates);

        // Restore texture edit state
        float scale = in.readFloat();
        float offsetU = in.readFloat();
        float offsetV = in.readFloat();
        currentTextureEditState = new TextureEditState(scale, offsetU, offsetV);

        // Use readInt instead of readBoolean for API 24 compatibility (readBoolean requires API 29)
        isGyroScaled = in.readInt() != 0;
        showEdgeHighlight = in.readInt() != 0;
        positionAtZero = in.readInt() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(textureResourceId);
        dest.writeString(name);
        dest.writeString(textureResource);
        dest.writeFloat(width);
        dest.writeFloat(height);
        dest.writeFloat(parallaxMultiplier);
        dest.writeFloat(positionX);
        dest.writeFloat(positionY);
        dest.writeFloat(originalWidth);
        dest.writeFloat(originalHeight);
        dest.writeFloat(originalPositionX);
        dest.writeFloat(originalPositionY);
        dest.writeFloat(textureEditingBaselineWidth);
        dest.writeFloat(textureEditingBaselineHeight);

        // Write texture coordinates
        if (originalTexCoordinates != null) {
            dest.writeInt(originalTexCoordinates.length);
            dest.writeFloatArray(originalTexCoordinates);
        } else {
            dest.writeInt(0);
        }

        // Write texture edit state
        dest.writeFloat(currentTextureEditState.getTextureScale());
        dest.writeFloat(currentTextureEditState.getTextureOffsetU());
        dest.writeFloat(currentTextureEditState.getTextureOffsetV());

        // Use writeInt instead of writeBoolean for API 24 compatibility (writeBoolean requires API 29)
        dest.writeInt(isGyroScaled ? 1 : 0);
        dest.writeInt(showEdgeHighlight ? 1 : 0);
        dest.writeInt(positionAtZero ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Sprite> CREATOR = new Creator<Sprite>() {
        @Override
        public Sprite createFromParcel(Parcel in) {
            return new Sprite(in);
        }

        @Override
        public Sprite[] newArray(int size) {
            return new Sprite[size];
        }
    };
}
