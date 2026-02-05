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

        float textureOffsetU = textureOffsets[0];
        float textureOffsetV = textureOffsets[1];

        // Calculate the growth scale: how much has the sprite grown from its original size?
        // We use the maximum of width and height growth to ensure uniform scaling
        float widthGrowth = width / originalWidth;
        float heightGrowth = height / originalHeight;
        float growthScale = Math.max(widthGrowth, heightGrowth);

        // The texture's effective size in world space grows with the sprite
        // This ensures the texture maintains its aspect ratio while scaling uniformly
        float textureWidthInWorld = originalWidth * textureScaleFactor * growthScale;
        float textureHeightInWorld = originalHeight * textureScaleFactor * growthScale;

        // Get the texture's aspect ratio (from original coordinates)
        float textureAspectRatio = 1.0f;  // Default for square textures
        if (originalTexCoordinates != null && originalTexCoordinates.length >= 4) {
            float texWidth = Math.abs(originalTexCoordinates[4] - originalTexCoordinates[0]);  // uMax - uMin
            float texHeight = Math.abs(originalTexCoordinates[3] - originalTexCoordinates[1]);  // vMax - vMin
            if (texHeight > 0) {
                textureAspectRatio = texWidth / texHeight;
            }
        }

        // Calculate the sprite's aspect ratio
        float spriteAspectRatio = width / height;

        // To maintain texture aspect ratio while fitting within sprite:
        // We need to use a uniform scale (same for U and V) that maintains the texture's aspect ratio
        // The visible window size should be scaled uniformly to preserve texture aspect ratio,
        // then clipped to the sprite bounds

        // Calculate how much of the texture is visible based on uniform scaling
        // The constraint is that the texture must fit within the sprite bounds while maintaining aspect ratio
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

        Log.d(TAG, "TEXDEBUG_WINDOW_SIZE: textureAspectRatio=" + textureAspectRatio + ", spriteAspectRatio=" + spriteAspectRatio + ", uniformScale=" + uniformScale);
        Log.d(TAG, "TEXDEBUG_WINDOW_SIZE: baseWindowSize=" + baseWindowSize + " * uniformScale=" + uniformScale + " = windowSize=" + windowSize);
        Log.d(TAG, "TEXDEBUG_WINDOW_SIZE: windowSizeU=" + windowSizeU + ", windowSizeV=" + windowSizeV);
        Log.d(TAG, "TEXDEBUG_WINDOW_SIZE: windowAspectRatio=" + (windowSizeU / windowSizeV));

        // Calculate the center point for texture scaling
        // Use 0.5 in the full [0,1] space as center
        float centerU = 0.5f;
        float centerV = 0.5f;

        // Calculate the base window position (centered)
        float halfWindowU = windowSizeU * 0.5f;
        float halfWindowV = windowSizeV * 0.5f;
        float uMin = centerU - halfWindowU;
        float uMax = centerU + halfWindowU;
        float vMin = centerV - halfWindowV;
        float vMax = centerV + halfWindowV;

        Log.d(TAG, "TEXDEBUG_WINDOW_INITIAL: uMin=" + uMin + ", uMax=" + uMax + ", vMin=" + vMin + ", vMax=" + vMax);

        // Apply offset to move the visible window
        uMin += textureOffsetU;
        uMax += textureOffsetU;
        vMin += textureOffsetV;
        vMax += textureOffsetV;

        Log.d(TAG, "TEXDEBUG_WINDOW_AFTER_OFFSET: uMin=" + uMin + ", uMax=" + uMax + ", vMin=" + vMin + ", vMax=" + vMax);

        // Clamp the window to [0, 1], adjusting offset to keep the window within bounds
        if (uMin < 0f) {
            float overshoot = -uMin;
            textureOffsetU += overshoot;
            uMin = 0f;
            uMax = uMax + overshoot;
            Log.d(TAG, "TEXDEBUG_CLAMP: U negative overshoot=" + overshoot + ", adjusted uMin=" + uMin + ", uMax=" + uMax);
        } else if (uMax > 1f) {
            float overshoot = uMax - 1f;
            textureOffsetU -= overshoot;
            uMax = 1f;
            uMin = uMin - overshoot;
            Log.d(TAG, "TEXDEBUG_CLAMP: U positive overshoot=" + overshoot + ", adjusted uMin=" + uMin + ", uMax=" + uMax);
        }

        if (vMin < 0f) {
            float overshoot = -vMin;
            textureOffsetV += overshoot;
            vMin = 0f;
            vMax = vMax + overshoot;
            Log.d(TAG, "TEXDEBUG_CLAMP: V negative overshoot=" + overshoot + ", adjusted vMin=" + vMin + ", vMax=" + vMax);
        } else if (vMax > 1f) {
            float overshoot = vMax - 1f;
            textureOffsetV -= overshoot;
            vMax = 1f;
            vMin = vMin - overshoot;
            Log.d(TAG, "TEXDEBUG_CLAMP: V positive overshoot=" + overshoot + ", adjusted vMin=" + vMin + ", vMax=" + vMax);
        }

        // Final clamp to ensure we stay in [0, 1]
        uMin = Math.max(0f, Math.min(1f, uMin));
        uMax = Math.max(0f, Math.min(1f, uMax));
        vMin = Math.max(0f, Math.min(1f, vMin));
        vMax = Math.max(0f, Math.min(1f, vMax));

        Log.d(TAG, "TEXDEBUG_WINDOW_FINAL: uMin=" + uMin + ", uMax=" + uMax + ", vMin=" + vMin + ", vMax=" + vMax);

        // Build final texture coordinates by mapping sprite corners to the visible window
        float[] texCoords = new float[] {
                uMin, vMax,  // top left
                uMin, vMin,  // bottom left
                uMax, vMax,  // top right
                uMax, vMin   // bottom right
        };

        Log.d(TAG, "TEXDEBUG_FINAL_COORDS: topLeft=(" + uMin + "," + vMax + "), " +
                "bottomLeft=(" + uMin + "," + vMin + "), topRight=(" + uMax + "," + vMax + "), " +
                "bottomRight=(" + uMax + "," + vMin + ")");

        // Update the texture coordinate buffer
        texCoordBuffer.clear();
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);

        Log.d(TAG, "Texture coordinates updated - scale: " + textureScale +
                ", offsetU: " + textureOffsetU + ", offsetV: " + textureOffsetV);
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

