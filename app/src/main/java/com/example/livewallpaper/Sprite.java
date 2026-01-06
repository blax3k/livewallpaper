package com.example.livewallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Represents a single sprite that can be rendered with an independent position, size, and texture.
 * Each sprite manages its own vertex buffer, texture coordinates, and texture ID.
 */
public class Sprite {
    private static final String TAG = "Sprite";

    private Context context;
    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;
    private int vertexCount = 4;
    private int textureId = 0;
    private int textureResourceId;

    // Sprite properties for positioning and sizing
    private float positionX = 0f;
    private float positionY = 0f;
    private float width = 0.5f;   // Full width (0.25 on each side of center)
    private float height = 0.5f;  // Full height (0.25 on each side of center)

    public Sprite(Context context, int textureResourceId) {
        this.context = context;
        this.textureResourceId = textureResourceId;
        initializeGeometry();
        loadTexture();
    }

    /**
     * Initialize vertex and texture coordinate buffers with default geometry.
     * Creates a unit square centered at the origin.
     */
    private void initializeGeometry() {
        // Define square vertices (centered at origin, 0.5 units on each side)
        float[] squareCoords = {
            -width / 2f,  height / 2f, 0.0f,  // top left
            -width / 2f, -height / 2f, 0.0f,  // bottom left
             width / 2f,  height / 2f, 0.0f,  // top right
             width / 2f, -height / 2f, 0.0f   // bottom right
        };

        ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);

        // Define texture coordinates (full texture mapping)
        float[] texCoords = {
            0.0f, 1.0f,  // top left
            0.0f, 0.0f,  // bottom left
            1.0f, 1.0f,  // top right
            1.0f, 0.0f   // bottom right
        };

        ByteBuffer tbb = ByteBuffer.allocateDirect(texCoords.length * 4);
        tbb.order(ByteOrder.nativeOrder());
        texCoordBuffer = tbb.asFloatBuffer();
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);
    }

    /**
     * Recalculates vertex coordinates based on current position and size.
     * Call this after changing position or size.
     */
    private void updateVertexBuffer() {
        float halfWidth = width / 2f;
        float halfHeight = height / 2f;

        float[] squareCoords = {
            positionX - halfWidth,  positionY + halfHeight, 0.0f,  // top left
            positionX - halfWidth,  positionY - halfHeight, 0.0f,  // bottom left
            positionX + halfWidth,  positionY + halfHeight, 0.0f,  // top right
            positionX + halfWidth,  positionY - halfHeight, 0.0f   // bottom right
        };

        vertexBuffer.position(0);
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);
    }

    /**
     * Load texture from the drawable resource.
     */
    private void loadTexture() {
        try {
            // Load bitmap from drawable folder
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), textureResourceId);

            // Create texture ID
            int[] textureIds = new int[1];
            GLES20.glGenTextures(1, textureIds, 0);
            textureId = textureIds[0];

            // Bind texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

            // Set texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            // Load bitmap into texture
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

            // Recycle bitmap
            bitmap.recycle();

            Log.d(TAG, "Texture loaded successfully for resource " + textureResourceId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load texture: " + e.getMessage());
        }
    }

    /**
     * Draw this sprite using the provided shader attribute handles.
     *
     * @param positionHandle attribute handle for vertex positions
     * @param texCoordHandle attribute handle for texture coordinates
     * @param samplerHandle uniform handle for texture sampler
     */
    public void draw(int positionHandle, int texCoordHandle, int samplerHandle) {
        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(samplerHandle, 0);

        // Enable vertex attribute array
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);

        // Enable texture coordinate attribute array
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer);

        // Draw the sprite
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount);

        // Disable vertex attribute arrays
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }

    /**
     * Set the position of the sprite.
     */
    public void setPosition(float x, float y) {
        this.positionX = x;
        this.positionY = y;
        updateVertexBuffer();
    }

    /**
     * Set the size of the sprite.
     */
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        updateVertexBuffer();
    }

    /**
     * Set texture coordinates (for texture atlasing or partial texture use).
     *
     * @param texCoords array of 8 floats representing 4 texture coordinates (u, v pairs)
     */
    public void setTextureCoordinates(float[] texCoords) {
        if (texCoords.length != 8) {
            Log.w(TAG, "Texture coordinates must be an array of 8 floats");
            return;
        }

        texCoordBuffer.position(0);
        texCoordBuffer.put(texCoords);
        texCoordBuffer.position(0);
    }

    /**
     * Get the current X position.
     */
    public float getPositionX() {
        return positionX;
    }

    /**
     * Get the current Y position.
     */
    public float getPositionY() {
        return positionY;
    }

    /**
     * Get the current width.
     */
    public float getWidth() {
        return width;
    }

    /**
     * Get the current height.
     */
    public float getHeight() {
        return height;
    }

    /**
     * Get the texture ID.
     */
    public int getTextureId() {
        return textureId;
    }

    /**
     * Destroy this sprite and release its GL resources.
     */
    public void destroy() {
        if (textureId != 0) {
            int[] textures = {textureId};
            GLES20.glDeleteTextures(1, textures, 0);
            textureId = 0;
        }
        Log.d(TAG, "Sprite destroyed");
    }
}

