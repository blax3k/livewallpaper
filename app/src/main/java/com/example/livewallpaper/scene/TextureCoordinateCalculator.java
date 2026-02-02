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

        // Calculate the texture's size in world space
        // The texture maintains a FIXED size regardless of sprite growth
        // It is set once at the editing baseline dimensions and does not scale with sprite changes
        float textureWidthInWorld = originalWidth * textureScaleFactor;
        float textureHeightInWorld = originalHeight * textureScaleFactor;

        // Calculate the visible portion of the texture in each dimension independently
        // When sprite shrinks: uScale/vScale become larger (we see a larger portion of the texture)
        // When sprite grows: uScale/vScale become smaller (we see a smaller portion of the texture)
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

        // Calculate texture dimensions in world space (fixed, not scaled with sprite growth)
        float textureWidthInWorld = originalWidth * textureScaleFactor;
        float textureHeightInWorld = originalHeight * textureScaleFactor;

        // Calculate visible window
        float uScale = width / textureWidthInWorld;
        float vScale = height / textureHeightInWorld;

        float baseWindowSizeU = 1.0f / textureScale;
        float baseWindowSizeV = 1.0f / textureScale;
        float windowSizeU = baseWindowSizeU * uScale;
        float windowSizeV = baseWindowSizeV * vScale;

        // Apply deltas
        float newOffsetU = currentOffsetU + deltaU;
        float newOffsetV = currentOffsetV + deltaV;

        // Clamp U
        float halfWindowU = windowSizeU * 0.5f;
        if (newOffsetU < -0.5f + halfWindowU) {
            newOffsetU = -0.5f + halfWindowU;
        } else if (newOffsetU > 0.5f - halfWindowU) {
            newOffsetU = 0.5f - halfWindowU;
        }

        // Clamp V
        float halfWindowV = windowSizeV * 0.5f;
        if (newOffsetV < -0.5f + halfWindowV) {
            newOffsetV = -0.5f + halfWindowV;
        } else if (newOffsetV > 0.5f - halfWindowV) {
            newOffsetV = 0.5f - halfWindowV;
        }

        return new float[] { newOffsetU, newOffsetV };
    }
}

