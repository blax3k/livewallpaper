package com.example.livewallpaper.scene;

import java.nio.FloatBuffer;

/**
 * Calculates texture coordinates for a sprite given:
 *  - A user-controlled texture scale (zoom): textureScale >= 1 means texture appears larger.
 *  - A user-controlled texture offset (pan): UV-space displacement of the texture center from (0.5, 0.5).
 *  - Current and original sprite dimensions.
 *  - A world-space texture scale factor set at sprite construction.
 *
 * Rules enforced:
 *  1. The texture window always stays within [0,1] UV space — texture edges never enter the sprite.
 *  2. Texture aspect ratio is never distorted.
 *  3. If the sprite grows larger than the texture, the effective texture scale is increased
 *     (minimum necessary) so the texture always covers the sprite completely.
 */
public class TextureCoordinateCalculator {

    public static class TextureCoordinateData {
        public float windowSizeU;
        public float windowSizeV;
        public float uMin, uMax, vMin, vMax;
        // Legacy fields kept for compatibility
        public float textureAspectRatio;
        public float spriteAspectRatio;
        public float textureWidthInWorld;
        public float textureHeightInWorld;
        public float uniformScale;
        public float windowSize;
    }

    // -------------------------------------------------------------------------
    // Core calculation
    // -------------------------------------------------------------------------

    /**
     * Calculate texture coordinate data.
     *
     * @param originalTexCoordinates  original UV coords — used only to derive textureAspectRatio
     * @param textureScale            user zoom (>=1 → texture appears larger / shows less texture)
     * @param width                   current sprite width
     * @param height                  current sprite height
     * @param originalWidth           original (baseline) sprite width
     * @param originalHeight          original (baseline) sprite height
     * @param textureScaleFactor      world-space scale set at construction
     * @param textureOffsetU          UV-space horizontal offset (pan)
     * @param textureOffsetV          UV-space vertical offset (pan)
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

        // Derive the natural texture size from the original UV window + baseline sprite dimensions.
        // This ensures textureScale=1 / offset=0 exactly reproduces originalTexCoordinates,
        // regardless of whether the sprite is square, rectangular, or had a pre-existing zoom/offset.
        //
        // Formula: naturalTexW = baselineWidth / windowSizeU_original
        //   where windowSizeU_original = uMax - uMin from originalTexCoordinates.
        //
        // Falls back to originalWidth * textureScaleFactor for full-coverage (default) coordinates.
        float naturalTexW, naturalTexH;
        if (originalTexCoordinates != null && originalTexCoordinates.length >= 8) {
            float initWindowU = Math.abs(originalTexCoordinates[4] - originalTexCoordinates[0]);
            float initWindowV = Math.abs(originalTexCoordinates[1] - originalTexCoordinates[3]);
            naturalTexW = (initWindowU > 0.001f) ? originalWidth  / initWindowU : originalWidth  * textureScaleFactor;
            naturalTexH = (initWindowV > 0.001f) ? originalHeight / initWindowV : originalHeight * textureScaleFactor;
        } else {
            naturalTexW = originalWidth  * textureScaleFactor;
            naturalTexH = originalHeight * textureScaleFactor;
        }

        // The effective scale must be large enough so the texture always covers the sprite.
        // textureScale is a multiplier relative to the minimum-coverage scale, so the slider always
        // has a meaningful effect regardless of how large the sprite has grown.
        // textureScale=1.0 → texture edge just meets sprite edge on the closest axis.
        float minScaleForCoverage = Math.max(width / naturalTexW, height / naturalTexH);
        float effectiveScale = minScaleForCoverage * textureScale;

        // Fraction of the texture that is visible along each axis.
        // Both values are in (0, 1] by construction.
        data.windowSizeU = width  / (naturalTexW * effectiveScale);
        data.windowSizeV = height / (naturalTexH * effectiveScale);

        // Clamp to [0,1] for safety against floating-point edge cases
        data.windowSizeU = Math.min(1.0f, data.windowSizeU);
        data.windowSizeV = Math.min(1.0f, data.windowSizeV);

        // Base center comes from the original UV window (preserves pre-existing pan/atlas offset).
        // User offset is applied on top of that.
        float halfU = data.windowSizeU * 0.5f;
        float halfV = data.windowSizeV * 0.5f;

        float baseCenterU = 0.5f;
        float baseCenterV = 0.5f;
        if (originalTexCoordinates != null && originalTexCoordinates.length >= 8) {
            baseCenterU = (originalTexCoordinates[0] + originalTexCoordinates[4]) * 0.5f;
            baseCenterV = (originalTexCoordinates[3] + originalTexCoordinates[1]) * 0.5f;
        }

        float centerU = clampCenter(baseCenterU + textureOffsetU, halfU);
        float centerV = clampCenter(baseCenterV + textureOffsetV, halfV);

        data.uMin = centerU - halfU;
        data.uMax = centerU + halfU;
        data.vMin = centerV - halfV;
        data.vMax = centerV + halfV;

        // Legacy compatibility fields
        data.textureAspectRatio = calculateTextureAspectRatio(originalTexCoordinates);
        data.spriteAspectRatio  = width / height;
        data.textureWidthInWorld  = naturalTexW;
        data.textureHeightInWorld = naturalTexH;
        data.uniformScale = effectiveScale;
        data.windowSize   = data.windowSizeV;

        return data;
    }

    /** Clamp center so that [center-half, center+half] stays within [0,1]. */
    private static float clampCenter(float center, float half) {
        if (center - half < 0f) return half;
        if (center + half > 1f) return 1f - half;
        return center;
    }

    // -------------------------------------------------------------------------
    // Public helpers
    // -------------------------------------------------------------------------

    public static void updateTextureCoordinates(
            FloatBuffer texCoordBuffer,
            float[] originalTexCoordinates,
            float textureScale,
            float[] textureOffsets,
            float width,
            float height,
            float originalWidth,
            float originalHeight,
            float textureScaleFactor) {

        if (originalTexCoordinates == null || texCoordBuffer == null) return;

        TextureCoordinateData data = calculateTextureCoordinates(
                originalTexCoordinates,
                textureScale,
                width, height,
                originalWidth, originalHeight,
                textureScaleFactor,
                textureOffsets[0], textureOffsets[1]);

        applyTextureCoordinatesToBuffer(texCoordBuffer, buildTextureCoordinateArray(data));
    }

    /**
     * Clamp a proposed offset delta so the texture window stays within [0,1].
     * Returns the new [offsetU, offsetV] after applying and clamping the delta.
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

        float naturalTexW, naturalTexH;
        if (originalTexCoordinates != null && originalTexCoordinates.length >= 8) {
            float initWindowU = Math.abs(originalTexCoordinates[4] - originalTexCoordinates[0]);
            float initWindowV = Math.abs(originalTexCoordinates[1] - originalTexCoordinates[3]);
            naturalTexW = (initWindowU > 0.001f) ? originalWidth  / initWindowU : originalWidth  * textureScaleFactor;
            naturalTexH = (initWindowV > 0.001f) ? originalHeight / initWindowV : originalHeight * textureScaleFactor;
        } else {
            naturalTexW = originalWidth  * textureScaleFactor;
            naturalTexH = originalHeight * textureScaleFactor;
        }

        float minScaleForCoverage = Math.max(width / naturalTexW, height / naturalTexH);
        float effectiveScale = minScaleForCoverage * textureScale;

        float windowSizeU = Math.min(1.0f, width  / (naturalTexW * effectiveScale));
        float windowSizeV = Math.min(1.0f, height / (naturalTexH * effectiveScale));

        float halfU = windowSizeU * 0.5f;
        float halfV = windowSizeV * 0.5f;

        float baseCenterU = 0.5f;
        float baseCenterV = 0.5f;
        if (originalTexCoordinates != null && originalTexCoordinates.length >= 8) {
            baseCenterU = (originalTexCoordinates[0] + originalTexCoordinates[4]) * 0.5f;
            baseCenterV = (originalTexCoordinates[3] + originalTexCoordinates[1]) * 0.5f;
        }

        float newCenterU = Math.max(halfU, Math.min(1f - halfU, baseCenterU + currentOffsetU + deltaU));
        float newCenterV = Math.max(halfV, Math.min(1f - halfV, baseCenterV + currentOffsetV + deltaV));

        return new float[]{ newCenterU - baseCenterU, newCenterV - baseCenterV };
    }

    public static float calculateTextureAspectRatio(float[] originalTexCoordinates) {
        if (originalTexCoordinates == null || originalTexCoordinates.length < 8) return 1.0f;
        float texWidth  = Math.abs(originalTexCoordinates[4] - originalTexCoordinates[0]);
        float texHeight = Math.abs(originalTexCoordinates[3] - originalTexCoordinates[1]);
        return (texHeight > 0) ? texWidth / texHeight : 1.0f;
    }

    public static float[] buildTextureCoordinateArray(TextureCoordinateData data) {
        return new float[]{
                data.uMin, data.vMax,   // top-left
                data.uMin, data.vMin,   // bottom-left
                data.uMax, data.vMax,   // top-right
                data.uMax, data.vMin    // bottom-right
        };
    }

    public static void applyTextureCoordinatesToBuffer(FloatBuffer texCoordBuffer, float[] texCoords) {
        texCoordBuffer.clear();
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);
    }

    // -------------------------------------------------------------------------
    // Extract helpers (reverse-engineering scale / offsets from stored UV coords)
    // -------------------------------------------------------------------------

    /**
     * Extract the user texture scale from UV coords and original sprite dimensions.
     * Uses the V window size: windowSizeV = spriteHeight / (naturalTexH * effectiveScale).
     */
    public static float extractScaleFromCoordinates(
            float[] texCoords,
            float spriteOriginalWidth,
            float spriteOriginalHeight,
            float[] originalTexCoordinates) {

        if (texCoords == null || texCoords.length < 8) return 1.0f;
        if (originalTexCoordinates == null || originalTexCoordinates.length < 8) return 1.0f;

        float initWindowU = Math.abs(originalTexCoordinates[4] - originalTexCoordinates[0]);
        float initWindowV = Math.abs(originalTexCoordinates[1] - originalTexCoordinates[3]);
        if (initWindowU <= 0f && initWindowV <= 0f) return 1.0f;

        // minScaleForCoverage when current dimensions equal baseline (the case when extracting during editing)
        float minScaleForCoverage = Math.max(initWindowU, initWindowV);

        // Use the constraining axis (largest initWindow) to derive effectiveScale.
        // effectiveScale = initWindow / observedWindowSize for that axis.
        float effectiveScale;
        if (initWindowV >= initWindowU) {
            float windowSizeV = Math.abs(texCoords[1] - texCoords[3]);
            if (windowSizeV <= 0f) return 1.0f;
            effectiveScale = initWindowV / windowSizeV;
        } else {
            float windowSizeU = Math.abs(texCoords[4] - texCoords[0]);
            if (windowSizeU <= 0f) return 1.0f;
            effectiveScale = initWindowU / windowSizeU;
        }

        // textureScale is the user-facing relative multiplier: effectiveScale = minScaleForCoverage * textureScale
        float scale = (minScaleForCoverage > 0f) ? effectiveScale / minScaleForCoverage : effectiveScale;
        return Math.max(1.0f, Math.min(8.0f, scale));
    }

    /** Legacy overload — assumes square sprite / textureScaleFactor == 1. */
    public static float extractScaleFromCoordinates(float[] texCoords) {
        if (texCoords == null || texCoords.length < 8) return 1.0f;
        float windowV = Math.abs(texCoords[1] - texCoords[3]);
        float windowU = Math.abs(texCoords[4] - texCoords[0]);
        float scale = 1.0f / Math.min(windowU, windowV);
        return Math.max(1.0f, Math.min(8.0f, scale));
    }

    public static float extractOffsetUFromCoordinates(float[] texCoords) {
        if (texCoords == null || texCoords.length < 8) return 0.0f;
        return (texCoords[0] + texCoords[4]) / 2.0f - 0.5f;
    }

    public static float extractOffsetVFromCoordinates(float[] texCoords) {
        if (texCoords == null || texCoords.length < 8) return 0.0f;
        return (texCoords[3] + texCoords[1]) / 2.0f - 0.5f;
    }

    // -------------------------------------------------------------------------
    // Legacy methods kept for callers that use them directly
    // -------------------------------------------------------------------------

    public static float calculateUniformScale(
            float width, float height,
            float textureWidthInWorld, float textureHeightInWorld) {
        return Math.min(width / textureWidthInWorld, height / textureHeightInWorld);
    }

    public static float[] calculateInitialWindowBounds(float windowSizeU, float windowSizeV) {
        float halfU = windowSizeU * 0.5f;
        float halfV = windowSizeV * 0.5f;
        return new float[]{ 0.5f - halfU, 0.5f + halfU, 0.5f - halfV, 0.5f + halfV };
    }

    public static void clampWindowBounds(TextureCoordinateData data) {
        float halfU = (data.uMax - data.uMin) * 0.5f;
        float halfV = (data.vMax - data.vMin) * 0.5f;
        float centerU = clampCenter((data.uMin + data.uMax) * 0.5f, halfU);
        float centerV = clampCenter((data.vMin + data.vMax) * 0.5f, halfV);
        data.uMin = centerU - halfU;  data.uMax = centerU + halfU;
        data.vMin = centerV - halfV;  data.vMax = centerV + halfV;
    }
}
