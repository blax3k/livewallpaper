package com.example.livewallpaper.gl;

import android.opengl.GLES20;
import com.example.livewallpaper.Sprite;

/**
 * Responsible for issuing draw calls for sprites using a compiled shader program.
 */
public class SpriteRenderer {
    private final int positionHandle;
    private final int texCoordHandle;
    private final int samplerHandle;
    private final int parallaxMultiplierHandle;

    public SpriteRenderer(Handles handles) {
        this.positionHandle = handles.positionHandle;
        this.texCoordHandle = handles.texCoordHandle;
        this.samplerHandle = handles.samplerHandle;
        this.parallaxMultiplierHandle = handles.parallaxMultiplierHandle;
    }

    public void drawSprite(Sprite sprite) {
        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sprite.getTextureId());
        GLES20.glUniform1i(samplerHandle, 0);

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
    }
}

