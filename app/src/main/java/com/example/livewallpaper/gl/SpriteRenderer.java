package com.example.livewallpaper.gl;

import android.opengl.GLES20;
import android.util.Log;
import com.example.livewallpaper.scene.Sprite;

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

    public SpriteRenderer(Handles handles) {
        this.positionHandle = handles.positionHandle;
        this.texCoordHandle = handles.texCoordHandle;
        this.samplerHandle = handles.samplerHandle;
        this.parallaxMultiplierHandle = handles.parallaxMultiplierHandle;
        this.wipeProgressHandle = handles.wipeProgressHandle;
        this.wipeDirectionHandle = handles.wipeDirectionHandle;
        this.overrideColorHandle = handles.overrideColorHandle;
        this.useColorOverrideHandle = handles.useColorOverrideHandle;
    }

    public void drawSprite(Sprite sprite) {
        int textureId = sprite.getTextureId();
        if (textureId == 0) {
            Log.w(TAG, "Attempted to draw sprite with textureId=0. This sprite will not render.");
            return;
        }

        // Disable color override for normal sprite rendering
        GLES20.glUniform1f(useColorOverrideHandle, 0.0f);

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(samplerHandle, 0);

        // Set wipe uniforms
        GLES20.glUniform1f(wipeProgressHandle, sprite.getWipeProgress());
        GLES20.glUniform1f(wipeDirectionHandle, sprite.getWipeDirection());

        // Enable vertex attribute array
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, sprite.getVertexBuffer());

        // Enable texture coordinate attribute array
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, sprite.getTexCoordBuffer());

        // Enable parallax multiplier attribute array
        GLES20.glEnableVertexAttribArray(parallaxMultiplierHandle);
        GLES20.glVertexAttribPointer(parallaxMultiplierHandle, 1, GLES20.GL_FLOAT, false, 4, sprite.getParallaxMultiplierBuffer());

        // Draw the sprite
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, sprite.getVertexCount());

        // Disable attribute arrays
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
        GLES20.glDisableVertexAttribArray(parallaxMultiplierHandle);

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
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, sprite.getEdgeLineBuffer());

        // Enable parallax multiplier attribute using edge line's own buffer to apply gyro/scroll transforms
        GLES20.glEnableVertexAttribArray(parallaxMultiplierHandle);
        GLES20.glVertexAttribPointer(parallaxMultiplierHandle, 1, GLES20.GL_FLOAT, false, 4, sprite.getEdgeLineParallaxMultiplierBuffer());

        // Disable blending for solid color
        GLES20.glDisable(GLES20.GL_BLEND);

        // Set wipe uniforms to 0 to avoid any wipe effect on the outline
        GLES20.glUniform1f(wipeProgressHandle, 0.0f);
        GLES20.glUniform1f(wipeDirectionHandle, 0.0f);

        // Draw bright green lines using line strip primitive
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, sprite.getEdgeLineVertexCount());

        // Clean up
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(parallaxMultiplierHandle);
        GLES20.glLineWidth(1.0f);
        GLES20.glEnable(GLES20.GL_BLEND);

        // Disable color override for next sprites
        GLES20.glUniform1f(useColorOverrideHandle, 0.0f);
    }
}

