package com.example.livewallpaper.gl;

import android.opengl.GLES20;
import com.example.livewallpaper.Sprite;

/**
 * Responsible for issuing draw calls for sprites using a compiled shader program.
 */
public class SpriteRenderer {
    private final ShaderHandles handles;

    public SpriteRenderer(ShaderHandles handles) {
        this.handles = handles;
    }

    public void drawSprite(Sprite sprite) {
        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sprite.getTextureId());
        GLES20.glUniform1i(handles.sampler, 0);

        // Enable vertex attribute array
        GLES20.glEnableVertexAttribArray(handles.position);
        GLES20.glVertexAttribPointer(handles.position, 3, GLES20.GL_FLOAT, false, 12, sprite.getVertexBuffer());

        // Enable texture coordinate attribute array
        GLES20.glEnableVertexAttribArray(handles.texCoord);
        GLES20.glVertexAttribPointer(handles.texCoord, 2, GLES20.GL_FLOAT, false, 8, sprite.getTexCoordBuffer());

        // Enable parallax multiplier attribute array
        GLES20.glEnableVertexAttribArray(handles.parallaxMultiplier);
        GLES20.glVertexAttribPointer(handles.parallaxMultiplier, 1, GLES20.GL_FLOAT, false, 4, sprite.getParallaxMultiplierBuffer());

        // Draw the sprite
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, sprite.getVertexCount());

        // Disable attribute arrays
        GLES20.glDisableVertexAttribArray(handles.position);
        GLES20.glDisableVertexAttribArray(handles.texCoord);
        GLES20.glDisableVertexAttribArray(handles.parallaxMultiplier);
    }
}
