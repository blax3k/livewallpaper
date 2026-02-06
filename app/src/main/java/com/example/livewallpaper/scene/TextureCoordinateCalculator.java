package com.example.livewallpaper.scene;

import android.util.Log;

import java.nio.FloatBuffer;

/**
 * Responsible for calculating and applying texture coordinates to a sprite based on:
 * - Original texture coordinates
 * - Texture scale (zoom in/out on the texture)
 * - Texture offsets (pan the texture)
 * - Sprite dimensions and growth
 *
 * This class encapsulates all texture coordinate calculation logic, keeping the Sprite class focused
 * on geometric properties and the SpriteData class free of texture editing parameters.
 */
public class TextureCoordinateCalculator {
    private static final String TAG = "TextureCoordinateCalculator";

    /**
     * Holds intermediate calculations for texture coordinate transformation.
     * This allows unit testing of individual calculation steps.
     */
    public static class TextureCoordinateData {
        public float textureAspectRatio;
        public float spriteAspectRatio;
        public float growthScale;
        public float textureWidthInWorld;
        public float textureHeightInWorld;
        public float uniformScale;
        public float windowSize;
        public float windowSizeU;
        public float windowSizeV;
        public float uMin;
        public float uMax;
        public float vMin;
        public float vMax;
    }

    /**
     * Calculate and apply texture coordinates to a sprite's texture coordinate buffer.
     * This accounts for texture scale, offset, sprite dimensions, and aspect ratio changes.
     *
     * @param texCoordBuffer the texture coordinate buffer to update
     * @param originalTexCoordinates the original texture coordinates before any transformations
     * @param textureScale the texture scale factor (1.0 = original, >1.0 = zoomed in, <1.0 = zoomed out)
     * @param textureOffsets array containing [U offset, V offset]
     * @param width the current sprite width
     * @param height the current sprite height
     * @param originalWidth the original sprite width
     * @param originalHeight the original sprite height
     * @param textureScaleFactor the texture scale factor in world space (set at sprite construction)
     */
    public static void updateTextureCoordinates(
            FloatBuffer texCoordBuffer,
            float[] originalTexCoordinates,
            float textureScale,
            float[] textureOffsets,  // [0] = U, [1] = V
            float width,
            float height,
            float originalWidth,
            float originalHeight,
            float textureScaleFactor) {

        if (originalTexCoordinates == null || texCoordBuffer == null) {
            return;
        }

        // Calculate all intermediate values
        TextureCoordinateData data = calculateTextureCoordinates(
                originalTexCoordinates,
                textureScale,
                width, height,
                originalWidth, originalHeight,
                textureScaleFactor,
                textureOffsets[0], textureOffsets[1]
        );

        // Build and apply final texture coordinates
        float[] texCoords = buildTextureCoordinateArray(data);
        applyTextureCoordinatesToBuffer(texCoordBuffer, texCoords);

        Log.d(TAG, "Texture coordinates updated - scale: " + textureScale +
                ", offsetU: " + textureOffsets[0] + ", offsetV: " + textureOffsets[1]);
    }

    /**
     * Calculate all intermediate texture coordinate values.
     * This is the core calculation logic, broken into steps for testability.
     *
     * @return TextureCoordinateData with all calculated values
     */
    public static TextureCoordinateData calculateTextureCoordinates(
            float[] originalTexCoordinates,
            float textureScale,
            float width,
            float height,
            float originalWidth,
            float originalHeight,
            float textureScaleFactor,
            float textureOffsetU,
            float textureOffsetV) {

        TextureCoordinateData data = new TextureCoordinateData();

        // Step 1: Calculate growth and aspect ratios
        data.growthScale = calculateGrowthScale(width, height, originalWidth, originalHeight);
        data.textureAspectRatio = calculateTextureAspectRatio(originalTexCoordinates);
        data.spriteAspectRatio = width / height;

        // Step 2: Calculate texture dimensions in world space
        data.textureWidthInWorld = originalWidth * textureScaleFactor * data.growthScale;
        data.textureHeightInWorld = originalHeight * textureScaleFactor * data.growthScale;

        // Step 3: Calculate uniform scale to fit texture within sprite bounds
        data.uniformScale = calculateUniformScale(
                data.textureAspectRatio,
                data.spriteAspectRatio,
                width, height,
                data.textureWidthInWorld,
                data.textureHeightInWorld
        );

        // Step 4: Calculate window size accounting for zoom
        data.windowSize = calculateWindowSize(textureScale, data.uniformScale);
        data.windowSizeU = data.windowSize * data.textureAspectRatio;
        data.windowSizeV = data.windowSize;

        logWindowSizeDebug(data);

        // Step 5: Calculate initial window bounds (centered)
        float[] bounds = calculateInitialWindowBounds(data.windowSizeU, data.windowSizeV);
        data.uMin = bounds[0];
        data.uMax = bounds[1];
        data.vMin = bounds[2];
        data.vMax = bounds[3];

        logInitialWindowDebug(data);

        // Step 6: Apply offset
        data.uMin += textureOffsetU;
        data.uMax += textureOffsetU;
        data.vMin += textureOffsetV;
        data.vMax += textureOffsetV;

        logAfterOffsetDebug(data);

        // Step 7: Clamp window to [0, 1] range
        clampWindowBounds(data);

        logFinalWindowDebug(data);

        return data;
    }

    /**
     * Calculate growth scale from sprite dimension changes.
     * Uses the maximum of width and height growth for uniform scaling.
     */
    public static float calculateGrowthScale(
            float width, float height,
            float originalWidth, float originalHeight) {
        float widthGrowth = width / originalWidth;
        float heightGrowth = height / originalHeight;
        return Math.max(widthGrowth, heightGrowth);
    }

    /**
     * Calculate texture aspect ratio from original texture coordinates.
     * Defaults to 1.0 (square) if coordinates are not available.
     */
    public static float calculateTextureAspectRatio(float[] originalTexCoordinates) {
        if (originalTexCoordinates == null || originalTexCoordinates.length < 4) {
            return 1.0f;
        }
        float texWidth = Math.abs(originalTexCoordinates[4] - originalTexCoordinates[0]);  // uMax - uMin
        float texHeight = Math.abs(originalTexCoordinates[3] - originalTexCoordinates[1]);  // vMax - vMin
        if (texHeight > 0) {
            return texWidth / texHeight;
        }
        return 1.0f;
    }

    /**
     * Calculate uniform scale to maintain texture aspect ratio while fitting within sprite bounds.
     */
    public static float calculateUniformScale(
            float textureAspectRatio,
            float spriteAspectRatio,
            float width,
            float height,
            float textureWidthInWorld,
            float textureHeightInWorld) {
        if (textureAspectRatio > spriteAspectRatio) {
            // Texture is wider than sprite - constrain by width
            return width / textureWidthInWorld;
        } else {
            // Texture is taller than sprite - constrain by height
            return height / textureHeightInWorld;
        }
    }

    /**
     * Calculate the window size (amount of texture visible) accounting for zoom.
     */
    public static float calculateWindowSize(float textureScale, float uniformScale) {
        float baseWindowSize = 1.0f / textureScale;
        return baseWindowSize * uniformScale;
    }

    /**
     * Calculate initial window bounds centered at (0.5, 0.5) in texture space.
     * Returns array: [uMin, uMax, vMin, vMax]
     */
    public static float[] calculateInitialWindowBounds(float windowSizeU, float windowSizeV) {
        float centerU = 0.5f;
        float centerV = 0.5f;
        float halfWindowU = windowSizeU * 0.5f;
        float halfWindowV = windowSizeV * 0.5f;

        return new float[] {
                centerU - halfWindowU,  // uMin
                centerU + halfWindowU,  // uMax
                centerV - halfWindowV,  // vMin
                centerV + halfWindowV   // vMax
        };
    }

    /**
     * Clamp window bounds to [0, 1] range, adjusting them to stay within bounds.
     */
    public static void clampWindowBounds(TextureCoordinateData data) {
        // Clamp U bounds
        if (data.uMin < 0f) {
            float overshoot = -data.uMin;
            data.uMin = 0f;
            data.uMax = data.uMax + overshoot;
            Log.d(TAG, "TEXDEBUG_CLAMP: U negative overshoot=" + overshoot);
        } else if (data.uMax > 1f) {
            float overshoot = data.uMax - 1f;
            data.uMax = 1f;
            data.uMin = data.uMin - overshoot;
            Log.d(TAG, "TEXDEBUG_CLAMP: U positive overshoot=" + overshoot);
        }

        // Clamp V bounds
        if (data.vMin < 0f) {
            float overshoot = -data.vMin;
            data.vMin = 0f;
            data.vMax = data.vMax + overshoot;
            Log.d(TAG, "TEXDEBUG_CLAMP: V negative overshoot=" + overshoot);
        } else if (data.vMax > 1f) {
            float overshoot = data.vMax - 1f;
            data.vMax = 1f;
            data.vMin = data.vMin - overshoot;
            Log.d(TAG, "TEXDEBUG_CLAMP: V positive overshoot=" + overshoot);
        }

        // Final clamp to ensure we stay in [0, 1]
        data.uMin = Math.max(0f, Math.min(1f, data.uMin));
        data.uMax = Math.max(0f, Math.min(1f, data.uMax));
        data.vMin = Math.max(0f, Math.min(1f, data.vMin));
        data.vMax = Math.max(0f, Math.min(1f, data.vMax));
    }

    /**
     * Build texture coordinate array from calculated bounds.
     * Maps sprite corners to the visible texture window.
     * Format: [uMin, vMax, uMin, vMin, uMax, vMax, uMax, vMin]
     */
    public static float[] buildTextureCoordinateArray(TextureCoordinateData data) {
        float[] texCoords = new float[] {
                data.uMin, data.vMax,  // top left
                data.uMin, data.vMin,  // bottom left
                data.uMax, data.vMax,  // top right
                data.uMax, data.vMin   // bottom right
        };

        Log.d(TAG, "TEXDEBUG_FINAL_COORDS: topLeft=(" + data.uMin + "," + data.vMax + "), " +
                "bottomLeft=(" + data.uMin + "," + data.vMin + "), topRight=(" + data.uMax + "," + data.vMax + "), " +
                "bottomRight=(" + data.uMax + "," + data.vMin + ")");

        return texCoords;
    }

    /**
     * Apply texture coordinates to the buffer.
     */
    public static void applyTextureCoordinatesToBuffer(FloatBuffer texCoordBuffer, float[] texCoords) {
        texCoordBuffer.clear();
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);
    }

    // Debug logging methods
    private static void logWindowSizeDebug(TextureCoordinateData data) {
        Log.d(TAG, "TEXDEBUG_WINDOW_SIZE: textureAspectRatio=" + data.textureAspectRatio +
                ", spriteAspectRatio=" + data.spriteAspectRatio + ", uniformScale=" + data.uniformScale);
        Log.d(TAG, "TEXDEBUG_WINDOW_SIZE: windowSizeU=" + data.windowSizeU + ", windowSizeV=" + data.windowSizeV);
        Log.d(TAG, "TEXDEBUG_WINDOW_SIZE: windowAspectRatio=" + (data.windowSizeU / data.windowSizeV));
    }

    private static void logInitialWindowDebug(TextureCoordinateData data) {
        Log.d(TAG, "TEXDEBUG_WINDOW_INITIAL: uMin=" + data.uMin + ", uMax=" + data.uMax +
                ", vMin=" + data.vMin + ", vMax=" + data.vMax);
    }

    private static void logAfterOffsetDebug(TextureCoordinateData data) {
        Log.d(TAG, "TEXDEBUG_WINDOW_AFTER_OFFSET: uMin=" + data.uMin + ", uMax=" + data.uMax +
                ", vMin=" + data.vMin + ", vMax=" + data.vMax);
    }

    private static void logFinalWindowDebug(TextureCoordinateData data) {
        Log.d(TAG, "TEXDEBUG_WINDOW_FINAL: uMin=" + data.uMin + ", uMax=" + data.uMax +
                ", vMin=" + data.vMin + ", vMax=" + data.vMax);
    }

    /**
     * Offset texture coordinates and clamp to valid bounds.
     * Returns the clamped offset values.
     *
     * @param currentOffsetU current U offset
     * @param currentOffsetV current V offset
     * @param deltaU delta to apply to U
     * @param deltaV delta to apply to V
     * @param width current sprite width
     * @param height current sprite height
     * @param originalWidth original sprite width
     * @param originalHeight original sprite height
     * @param textureScale current texture scale
     * @param textureScaleFactor texture scale factor in world space
     * @param originalTexCoordinates original texture coordinates for aspect ratio calculation
     * @return array of [clampedOffsetU, clampedOffsetV]
     */
    public static float[] clampTextureOffset(
            float currentOffsetU,
            float currentOffsetV,
            float deltaU,
            float deltaV,
            float width,
            float height,
            float originalWidth,
            float originalHeight,
            float textureScale,
            float textureScaleFactor,
            float[] originalTexCoordinates) {

        // Calculate the growth scale
        float widthGrowth = width / originalWidth;
        float heightGrowth = height / originalHeight;
        float growthScale = Math.max(widthGrowth, heightGrowth);

        // Calculate texture dimensions in world space
        float textureWidthInWorld = originalWidth * textureScaleFactor * growthScale;
        float textureHeightInWorld = originalHeight * textureScaleFactor * growthScale;

        // Get the texture's aspect ratio
        float textureAspectRatio = 1.0f;
        if (originalTexCoordinates != null && originalTexCoordinates.length >= 4) {
            float texWidth = Math.abs(originalTexCoordinates[4] - originalTexCoordinates[0]);
            float texHeight = Math.abs(originalTexCoordinates[3] - originalTexCoordinates[1]);
            if (texHeight > 0) {
                textureAspectRatio = texWidth / texHeight;
            }
        }

        // Calculate the sprite's aspect ratio
        float spriteAspectRatio = width / height;

        // Use uniform scaling to maintain texture aspect ratio
        float uniformScale;
        if (textureAspectRatio > spriteAspectRatio) {
            // Texture is wider than sprite - constrain by width
            uniformScale = width / textureWidthInWorld;
        } else {
            // Texture is taller than sprite - constrain by height
            uniformScale = height / textureHeightInWorld;
        }

        // Apply texture scale (zoom) to the uniform scale
        float baseWindowSize = 1.0f / textureScale;
        float windowSize = baseWindowSize * uniformScale;

        // Calculate window dimensions maintaining aspect ratio
        float windowSizeU = windowSize * textureAspectRatio;
        float windowSizeV = windowSize;

        // Apply deltas
        float newOffsetU = currentOffsetU + deltaU;
        float newOffsetV = currentOffsetV + deltaV;

        // Clamp U: ensure the visible window stays within [0, 1] texture space
        // The window is centered at (0.5 + offset) with size windowSizeU
        // Valid range: window must fit entirely in [0, 1]
        float halfWindowU = windowSizeU * 0.5f;
        float minOffsetU = halfWindowU - 0.5f;  // Ensures window left edge >= 0
        float maxOffsetU = 0.5f - halfWindowU;  // Ensures window right edge <= 1

        if (newOffsetU < minOffsetU) {
            newOffsetU = minOffsetU;
        } else if (newOffsetU > maxOffsetU) {
            newOffsetU = maxOffsetU;
        }

        // Clamp V: same logic as U but for vertical axis
        float halfWindowV = windowSizeV * 0.5f;
        float minOffsetV = halfWindowV - 0.5f;  // Ensures window top edge >= 0
        float maxOffsetV = 0.5f - halfWindowV;  // Ensures window bottom edge <= 1

        if (newOffsetV < minOffsetV) {
            newOffsetV = minOffsetV;
        } else if (newOffsetV > maxOffsetV) {
            newOffsetV = maxOffsetV;
        }

        return new float[] { newOffsetU, newOffsetV };
    }

    /**
     * Offset texture coordinates and clamp to valid bounds (legacy version without texture coordinates).
     * For backwards compatibility when texture aspect ratio is not available.
     *
     * @param currentOffsetU current U offset
     * @param currentOffsetV current V offset
     * @param deltaU delta to apply to U
     * @param deltaV delta to apply to V
     * @param width current sprite width
     * @param height current sprite height
     * @param originalWidth original sprite width
     * @param originalHeight original sprite height
     * @param textureScale current texture scale
     * @param textureScaleFactor texture scale factor in world space
     * @return array of [clampedOffsetU, clampedOffsetV]
     */
    public static float[] clampTextureOffset(
            float currentOffsetU,
            float currentOffsetV,
            float deltaU,
            float deltaV,
            float width,
            float height,
            float originalWidth,
            float originalHeight,
            float textureScale,
            float textureScaleFactor) {
        // Call the overloaded version with null texture coordinates
        // This will assume a square texture (1:1 aspect ratio)
        return clampTextureOffset(currentOffsetU, currentOffsetV, deltaU, deltaV, width, height,
                originalWidth, originalHeight, textureScale, textureScaleFactor, null);
    }
}

