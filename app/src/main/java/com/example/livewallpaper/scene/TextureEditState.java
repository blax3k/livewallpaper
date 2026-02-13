package com.example.livewallpaper.scene;

import android.util.Log;

/**
 * Manages the state of texture editing parameters (scale and offsets).
 * These parameters are ephemeral and only used during texture editing in EditTextureActivity,
 * not persisted to the scene file.
 *
 * This class separates texture editing concerns from the Sprite class,
 * allowing clean separation of concerns and making it clear that these parameters
 * are not part of the sprite's permanent state.
 */
public class TextureEditState {
    private static final String TAG = "TextureEditState";

    private float textureScale = 1.0f;
    private float textureOffsetU = 0.0f;
    private float textureOffsetV = 0.0f;

    public TextureEditState() {
        this.textureScale = 1.0f;
        this.textureOffsetU = 0.0f;
        this.textureOffsetV = 0.0f;
    }

    public TextureEditState(float scale, float offsetU, float offsetV) {
        this.textureScale = Math.max(0.1f, scale); // Clamp to minimum of 0.1x
        this.textureOffsetU = offsetU;
        this.textureOffsetV = offsetV;
    }

    /**
     * Set the texture scale factor.
     * Scale is clamped to a minimum of 0.1x.
     *
     * @param scale the scale factor (1.0 = original, >1.0 = zoomed in, <1.0 = zoomed out)
     */
    public void setTextureScale(float scale) {
        this.textureScale = Math.max(0.1f, scale);
        Log.d(TAG, "Texture scale set to: " + this.textureScale);
    }

    /**
     * Get the current texture scale.
     */
    public float getTextureScale() {
        return textureScale;
    }

    /**
     * Set the texture U (horizontal) offset to an absolute value.
     *
     * @param offsetU the absolute U offset value
     */
    public void setTextureOffsetU(float offsetU) {
        this.textureOffsetU = offsetU;
        Log.d(TAG, "Texture offset U set to: " + offsetU);
    }

    /**
     * Get the current texture U offset.
     */
    public float getTextureOffsetU() {
        return textureOffsetU;
    }

    /**
     * Set the texture V (vertical) offset to an absolute value.
     *
     * @param offsetV the absolute V offset value
     */
    public void setTextureOffsetV(float offsetV) {
        this.textureOffsetV = offsetV;
        Log.d(TAG, "Texture offset V set to: " + offsetV);
    }

    /**
     * Get the current texture V offset.
     */
    public float getTextureOffsetV() {
        return textureOffsetV;
    }

    /**
     * Offset the texture coordinates by the given amounts, with clamping.
     * This version accepts original texture coordinates for proper aspect ratio handling.
     *
     * @param deltaU delta to apply to U offset
     * @param deltaV delta to apply to V offset
     * @param width current sprite width
     * @param height current sprite height
     * @param originalWidth original sprite width
     * @param originalHeight original sprite height
     * @param textureScaleFactor texture scale factor in world space
     * @param originalTexCoordinates original texture coordinates for aspect ratio calculation
     */
    public void offsetTextureCoordinates(float deltaU, float deltaV, float width, float height,
                                         float originalWidth, float originalHeight, float textureScaleFactor,
                                         float[] originalTexCoordinates) {
        float[] clamped = TextureCoordinateCalculator.clampTextureOffset(
                textureOffsetU, textureOffsetV,
                deltaU, deltaV,
                width, height,
                originalWidth, originalHeight,
                textureScale, textureScaleFactor,
                originalTexCoordinates
        );
        this.textureOffsetU = clamped[0];
        this.textureOffsetV = clamped[1];
        Log.d(TAG, "Texture offset adjusted - U: " + this.textureOffsetU + ", V: " + this.textureOffsetV);
    }

    /**
     * Get the offset array for use with TextureCoordinateCalculator.
     *
     * @return array of [offsetU, offsetV]
     */
    public float[] getOffsets() {
        return new float[] { textureOffsetU, textureOffsetV };
    }
}
