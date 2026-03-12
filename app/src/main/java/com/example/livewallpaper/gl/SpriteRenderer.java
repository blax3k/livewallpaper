package com.example.livewallpaper.gl;

import android.opengl.GLES20;
import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.Sprite;

/**
 * Responsible for issuing draw calls for sprites using a compiled shader program.
 */
public class SpriteRenderer {
    private static final String TAG = "SpriteRenderer";
    private final int positionHandle;
    private final int texCoordHandle;
    private final int samplerHandle;
    private final int parallaxMultiplierHandle;
    private final int wipeProgressHandle;
    private final int wipeDirectionHandle;
    private final int overrideColorHandle;
    private final int useColorOverrideHandle;
    private final int normalizedPositionHandle;

    public SpriteRenderer(Handles handles) {
        this.positionHandle = handles.positionHandle;
        this.texCoordHandle = handles.texCoordHandle;
        this.samplerHandle = handles.samplerHandle;
        this.parallaxMultiplierHandle = handles.parallaxMultiplierHandle;
        this.wipeProgressHandle = handles.wipeProgressHandle;
        this.wipeDirectionHandle = handles.wipeDirectionHandle;
        this.overrideColorHandle = handles.overrideColorHandle;
        this.useColorOverrideHandle = handles.useColorOverrideHandle;
        this.normalizedPositionHandle = handles.normalizedPositionHandle;
    }

    public void drawSprite(Sprite sprite) {
        int textureId = sprite.getTextureId();
        if (textureId == 0) {
            TimberLog.w(TAG, "Attempted to draw sprite with textureId=0. This sprite will not render.");
            return;
        }

        // Disable color override for normal sprite rendering
        GLES20.glUniform1f(useColorOverrideHandle, 0.0f);

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(samplerHandle, 0);

        // Set wipe uniforms
        setWipeUniforms(sprite.getWipeProgress(), sprite.getWipeDirection());

        // Enable vertex attribute array
        enableVertexAttribute(positionHandle, 3, 12, sprite.getVertexBuffer());
        // Enable texture coordinate attribute array
        enableVertexAttribute(texCoordHandle, 2, 8, sprite.getTexCoordBuffer());
        // Enable normalized position attribute array
        enableVertexAttribute(normalizedPositionHandle, 2, 8, sprite.getNormalizedPositionBuffer());
        // Enable parallax multiplier attribute array
        enableVertexAttribute(parallaxMultiplierHandle, 1, 4, sprite.getParallaxMultiplierBuffer());

        // Draw the sprite
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, sprite.getVertexCount());

        // Disable attribute arrays
        disableAttributeArrays(positionHandle, texCoordHandle, normalizedPositionHandle, parallaxMultiplierHandle);

        // Draw edge highlight if enabled
        if (sprite.isShowEdgeHighlight()) {
            drawSpriteEdgeHighlight(sprite);
        }
    }

    /**
     * Draw a bright green outline around the sprite edges using line primitives.
     * Uses thick lines for better visibility in the edit view.
     * Lines move with sprites via shader transforms (parallax and gyro offsets).
     */
    private void drawSpriteEdgeHighlight(Sprite sprite) {
        // Set line width for better visibility
        GLES20.glLineWidth(3.0f);

        // Enable color override for bright green output
        GLES20.glUniform1f(useColorOverrideHandle, 1.0f);
        // Set bright green color (R=0, G=1, B=0, A=1)
        GLES20.glUniform4f(overrideColorHandle, 0.0f, 1.0f, 0.0f, 1.0f);

        // Enable the position attribute for edge line rendering
        enableVertexAttribute(positionHandle, 3, 12, sprite.getEdgeLineBuffer());
        // Enable parallax multiplier attribute using edge line's own buffer to apply gyro/scroll transforms
        enableVertexAttribute(parallaxMultiplierHandle, 1, 4, sprite.getEdgeLineParallaxMultiplierBuffer());

        // Disable blending for solid color
        GLES20.glDisable(GLES20.GL_BLEND);

        // Set wipe uniforms to 0 to avoid any wipe effect on the outline
        setWipeUniforms(0.0f, 0.0f);

        // Draw bright green lines using line strip primitive
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, sprite.getEdgeLineVertexCount());

        // Clean up
        disableAttributeArrays(positionHandle, parallaxMultiplierHandle);
        GLES20.glLineWidth(1.0f);
        GLES20.glEnable(GLES20.GL_BLEND);

        // Disable color override for next sprites
        GLES20.glUniform1f(useColorOverrideHandle, 0.0f);
    }

    /**
     * Helper method to set wipe progress and direction uniforms.
     */
    private void setWipeUniforms(float wipeProgress, float wipeDirection) {
        GLES20.glUniform1f(wipeProgressHandle, wipeProgress);
        GLES20.glUniform1f(wipeDirectionHandle, wipeDirection);
    }

    /**
     * Helper method to disable one or more vertex attribute arrays.
     */
    private void disableAttributeArrays(int... handles) {
        for (int handle : handles) {
            GLES20.glDisableVertexAttribArray(handle);
        }
    }

    /**
     * Helper method to enable a vertex attribute and set its pointer.
     * @param handle the attribute handle
     * @param size number of components per attribute (e.g., 3 for position, 2 for texCoord)
     * @param stride byte offset between consecutive attributes
     * @param buffer the buffer containing the attribute data
     */
    private void enableVertexAttribute(int handle, int size, int stride, java.nio.Buffer buffer) {
        GLES20.glEnableVertexAttribArray(handle);
        GLES20.glVertexAttribPointer(handle, size, GLES20.GL_FLOAT, false, stride, buffer);
    }
}
