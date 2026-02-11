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
            Log.d(TAG, "TEXDEBUG_UPDATE: Skipping - null coordinates or buffer");
            return;
        }

        Log.d(TAG, "TEXDEBUG_UPDATE: Called with width=" + width + ", height=" + height +
                ", textureScale=" + textureScale + ", offsets=[" + textureOffsets[0] + "," + textureOffsets[1] + "]");

        // Log stack trace to identify caller
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length > 4) {
            Log.d(TAG, "TEXDEBUG_CALLER: " + stackTrace[3].getClassName() + "." + stackTrace[3].getMethodName() + ":" + stackTrace[3].getLineNumber());
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

        Log.d(TAG, "TEXDEBUG_UPDATE_COMPLETE: Final texture coordinates applied");
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

        // Log input parameters for debugging
        Log.d(TAG, "TEXDEBUG_INPUT: width=" + width + ", height=" + height +
                ", originalWidth=" + originalWidth + ", originalHeight=" + originalHeight);
        Log.d(TAG, "TEXDEBUG_INPUT: textureScale=" + textureScale +
                ", textureOffsetU=" + textureOffsetU + ", textureOffsetV=" + textureOffsetV);
        Log.d(TAG, "TEXDEBUG_INPUT: textureScaleFactor=" + textureScaleFactor);
        if (originalTexCoordinates != null && originalTexCoordinates.length >= 8) {
            Log.d(TAG, "TEXDEBUG_INPUT: originalTexCoords=[" +
                    originalTexCoordinates[0] + "," + originalTexCoordinates[1] + "," +
                    originalTexCoordinates[2] + "," + originalTexCoordinates[3] + "," +
                    originalTexCoordinates[4] + "," + originalTexCoordinates[5] + "," +
                    originalTexCoordinates[6] + "," + originalTexCoordinates[7] + "]");
        }

        // Step 1: Calculate growth and aspect ratios
        data.growthScale = calculateGrowthScale(width, height, originalWidth, originalHeight);
        data.textureAspectRatio = calculateTextureAspectRatio(originalTexCoordinates);
        data.spriteAspectRatio = width / height;

        // Step 2: Calculate texture dimensions in world space
        // NOTE: Texture dimensions are based on BASELINE (originalWidth/originalHeight), NOT current dimensions.
        // This ensures the texture maintains its size and aspect ratio as the sprite grows.
        // The texture will expand only if the sprite grows large enough to require more texture surface,
        // which is handled by the uniformScale calculation in Step 3.
        data.textureWidthInWorld = originalWidth * textureScaleFactor;
        data.textureHeightInWorld = originalHeight * textureScaleFactor;

        // Step 3: Calculate uniform scale to fit texture within sprite bounds
        // This is used for reference but the actual window calculation happens in Step 4
        data.uniformScale = calculateUniformScale(
                data.textureAspectRatio,
                data.spriteAspectRatio,
                width, height,
                data.textureWidthInWorld,
                data.textureHeightInWorld
        );

        // Step 4: Calculate window size accounting for zoom and sprite dimensions
        // The texture should maintain its aspect ratio while fitting within the sprite bounds.
        // As sprite dimensions change, we reveal more/less texture in each dimension independently.

        // Base window size from zoom (how much of the texture is visible at current zoom level)
        float baseWindowSize = 1.0f / textureScale;

        // Calculate how much the sprite has grown relative to its original size
        float widthGrowthFactor = width / originalWidth;
        float heightGrowthFactor = height / originalHeight;

        Log.d(TAG, "TEXDEBUG_GROWTH: widthGrowthFactor=" + widthGrowthFactor +
                ", heightGrowthFactor=" + heightGrowthFactor);

        // Calculate window size for each dimension independently
        // The texture reveals more as the sprite grows, maintaining texture aspect ratio
        // U dimension: accounts for texture aspect ratio and width growth
        data.windowSizeU = baseWindowSize * widthGrowthFactor * data.textureAspectRatio;
        // V dimension: accounts for height growth
        data.windowSizeV = baseWindowSize * heightGrowthFactor;

        Log.d(TAG, "TEXDEBUG_PRE_CLAMP: windowSizeU=" + data.windowSizeU +
                ", windowSizeV=" + data.windowSizeV);

        // Clamp window size to max 1.0 (can't see more than the full texture)
        data.windowSizeU = Math.min(1.0f, data.windowSizeU);
        data.windowSizeV = Math.min(1.0f, data.windowSizeV);

        Log.d(TAG, "TEXDEBUG_POST_CLAMP: windowSizeU=" + data.windowSizeU +
                ", windowSizeV=" + data.windowSizeV +
                ", uClamped=" + (data.windowSizeU >= 1.0f) +
                ", vClamped=" + (data.windowSizeV >= 1.0f));

        // Handle case where texture needs to maintain aspect ratio as sprite dimensions change
        // When one dimension is clamped and the sprite aspect ratio differs from texture aspect ratio,
        // we need to adjust the window to maintain the texture's original aspect ratio

        boolean uIsClamped = (data.windowSizeU >= 1.0f);
        boolean vIsClamped = (data.windowSizeV >= 1.0f);

        if (uIsClamped && data.spriteAspectRatio > data.textureAspectRatio) {
            // U is at maximum, sprite is wider than texture aspect ratio
            // Adjust V to maintain texture aspect ratio (clip top/bottom)
            data.windowSizeV = 1.0f / data.spriteAspectRatio * data.textureAspectRatio;
            Log.d(TAG, "TEXDEBUG_ASPECT_ADJUST: U clamped, adjusted windowSizeV to " + data.windowSizeV +
                  " (sprite wider than texture, spriteAR=" + data.spriteAspectRatio +
                  ", textureAR=" + data.textureAspectRatio + ")");
        } else if (vIsClamped && data.spriteAspectRatio < data.textureAspectRatio) {
            // V is at maximum, sprite is taller than texture aspect ratio
            // Adjust U to maintain texture aspect ratio (clip left/right)
            data.windowSizeU = 1.0f * data.spriteAspectRatio / data.textureAspectRatio;
            Log.d(TAG, "TEXDEBUG_ASPECT_ADJUST: V clamped, adjusted windowSizeU to " + data.windowSizeU +
                  " (sprite taller than texture, spriteAR=" + data.spriteAspectRatio +
                  ", textureAR=" + data.textureAspectRatio + ")");
        }

        // For compatibility with existing code that uses data.windowSize
        data.windowSize = data.windowSizeV;

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
     * Calculate uniform scale to maintain texture's original aspect ratio.
     * The texture maintains its aspect ratio regardless of sprite aspect ratio.
     * It is scaled uniformly based on the SMALLER of the two constraints:
     * - Fitting within sprite width while maintaining texture aspect ratio
     * - Fitting within sprite height while maintaining texture aspect ratio
     * This ensures the entire texture is visible without distortion.
     */
    public static float calculateUniformScale(
            float textureAspectRatio,
            float spriteAspectRatio,
            float width,
            float height,
            float textureWidthInWorld,
            float textureHeightInWorld) {
        // Calculate how much to scale texture to fit within sprite bounds
        // while maintaining the texture's ORIGINAL aspect ratio
        float scaleByWidth = width / textureWidthInWorld;
        float scaleByHeight = height / textureHeightInWorld;

        // Use the smaller scale to ensure texture fits entirely within sprite bounds
        // This may leave empty space if sprite aspect ratio differs from texture aspect ratio
        return Math.min(scaleByWidth, scaleByHeight);
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

        // Calculate texture dimensions in world space
        // NOTE: Use baseline dimensions (originalWidth/originalHeight), NOT current dimensions.
        // This ensures texture maintains consistent size as sprite grows, revealing more texture
        // as the sprite expands while maintaining the original texture aspect ratio.
        float textureWidthInWorld = originalWidth * textureScaleFactor;
        float textureHeightInWorld = originalHeight * textureScaleFactor;

        // Get the texture's aspect ratio
        float textureAspectRatio = 1.0f;
        if (originalTexCoordinates != null && originalTexCoordinates.length >= 4) {
            float texWidth = Math.abs(originalTexCoordinates[4] - originalTexCoordinates[0]);
            float texHeight = Math.abs(originalTexCoordinates[3] - originalTexCoordinates[1]);
            if (texHeight > 0) {
                textureAspectRatio = texWidth / texHeight;
            }
        }

        // Calculate how sprite dimensions relate to texture dimensions
        float scaleByWidth = width / textureWidthInWorld;
        float scaleByHeight = height / textureHeightInWorld;

        // Base window size from zoom
        float baseWindowSize = 1.0f / textureScale;

        // Calculate window for each dimension independently
        float windowSizeU = baseWindowSize * scaleByWidth * textureAspectRatio;
        float windowSizeV = baseWindowSize * scaleByHeight;

        // Clamp
        windowSizeU = Math.min(1.0f, windowSizeU);
        windowSizeV = Math.min(1.0f, windowSizeV);

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

    /**
     * Extract texture scale from texture coordinates.
     * This is the reverse operation of calculateTextureCoordinates for scale.
     * Requires sprite dimensions to correctly account for non-square sprites.
     *
     * @param texCoords the texture coordinates array [uMin, vMax, uMin, vMin, uMax, vMax, uMax, vMin]
     * @param spriteOriginalWidth the original (baseline) width of the sprite
     * @param spriteOriginalHeight the original (baseline) height of the sprite
     * @param originalTexCoordinates the original texture coordinates to calculate texture aspect ratio
     * @return the extracted texture scale
     */
    public static float extractScaleFromCoordinates(float[] texCoords, float spriteOriginalWidth, float spriteOriginalHeight, float[] originalTexCoordinates) {
        if (texCoords == null || texCoords.length < 8) {
            return 1.0f;
        }

        // texCoords: [uMin, vMax, uMin, vMin, uMax, vMax, uMax, vMin]
        float vMin = texCoords[3];
        float vMax = texCoords[1];

        // Window size in V dimension
        float windowSizeV = Math.abs(vMax - vMin);

        // To extract scale, we need to reverse the forward calculation:
        // data.windowSizeV = baseWindowSize * heightGrowthFactor;
        //
        // Since growth factors are 1.0 when sprite dimensions match original:
        // windowSizeV = baseWindowSize
        //
        // Therefore: baseWindowSize = windowSizeV
        // And: scale = 1.0 / baseWindowSize

        // Use the V dimension to extract the base window size (height is not affected by texture aspect ratio)
        float baseWindowSize = windowSizeV;

        // Clamp to valid range (avoid division by zero and extreme scales)
        if (baseWindowSize <= 0.0f) {
            return 1.0f;
        }

        float scale = 1.0f / baseWindowSize;

        // Clamp to reasonable range
        return Math.max(1.0f, Math.min(8.0f, scale));
    }

    /**
     * Extract texture scale from texture coordinates (legacy version).
     * For backwards compatibility when sprite dimensions are not available.
     * This assumes a square sprite and may give incorrect results for non-square sprites.
     *
     * @param texCoords the texture coordinates array
     * @return the extracted texture scale (may be inaccurate for non-square sprites)
     */
    public static float extractScaleFromCoordinates(float[] texCoords) {
        if (texCoords == null || texCoords.length < 8) {
            return 1.0f;
        }

        // texCoords: [uMin, vMax, uMin, vMin, uMax, vMax, uMax, vMin]
        float uMin = texCoords[0];
        float uMax = texCoords[4];
        float vMin = texCoords[3];
        float vMax = texCoords[1];

        // Window size
        float windowWidth = Math.abs(uMax - uMin);
        float windowHeight = Math.abs(vMax - vMin);

        // If window is full (0-1), scale is 1.0
        // If window is half (0.25-0.75), scale is 2.0
        // Scale = 1.0 / windowSize
        float scale = 1.0f / Math.min(windowWidth, windowHeight);

        // Clamp to reasonable range
        return Math.max(1.0f, Math.min(8.0f, scale));
    }

    /**
     * Extract the U offset from texture coordinates.
     * This is the reverse operation of calculateTextureCoordinates for U offset.
     */
    public static float extractOffsetUFromCoordinates(float[] texCoords) {
        if (texCoords == null || texCoords.length < 8) {
            return 0.0f;
        }

        float uMin = texCoords[0];
        float uMax = texCoords[4];

        // Center of the window
        float center = (uMin + uMax) / 2.0f;

        // Offset from 0.5 (center of full texture)
        return center - 0.5f;
    }

    /**
     * Extract the V offset from texture coordinates.
     * This is the reverse operation of calculateTextureCoordinates for V offset.
     */
    public static float extractOffsetVFromCoordinates(float[] texCoords) {
        if (texCoords == null || texCoords.length < 8) {
            return 0.0f;
        }

        float vMin = texCoords[3];
        float vMax = texCoords[1];

        // Center of the window
        float center = (vMin + vMax) / 2.0f;

        // Offset from 0.5 (center of full texture)
        return center - 0.5f;
    }
}
