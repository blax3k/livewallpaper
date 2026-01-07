package com.example.livewallpaper;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.example.livewallpaper.gl.ShaderProgram;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.gl.SpriteRenderer;
import com.example.livewallpaper.gl.ShaderHandles;
import com.example.livewallpaper.sensors.GyroSensorProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple example renderer that displays a blue square with a texture (knight.png) in the center of the view.
 */
public class SimpleRenderer implements GLWallpaperRenderer {
    private static final String TAG = "SimpleRenderer";

    private Context context;
    private ShaderProgram shaderProgram;
    private ShaderHandles handles;
    private List<Sprite> sprites;
    private float[] projectionMatrix = new float[16];
    private float currentScrollOffset = 0f;
    private GyroSensorProcessor gyroProcessor;
    private TextureManager textureManager;
    private SpriteRenderer spriteRenderer;

    public SimpleRenderer(Context context) {
        this.context = context;
        this.sprites = new ArrayList<>();
        this.gyroProcessor = new GyroSensorProcessor();

        // Create shader program using helper
        shaderProgram = new ShaderProgram();
        shaderProgram.use();

        int prog = shaderProgram.getProgram();
        handles = new ShaderHandles(prog);

        // Create texture manager and sprite renderer
        textureManager = new TextureManager();
        spriteRenderer = new SpriteRenderer(handles);
    }

    @Override
    public void onSurfaceCreated() {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        // Enable blending for transparency
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        addSprites();

        // Resolve textures for each sprite through TextureManager
        for (Sprite sprite : sprites) {
            int texId = textureManager.getTexture(context, sprite.getTextureResourceId());
            sprite.setTextureId(texId);
        }

        Log.d(TAG, "Surface created");
    }

    private void addSprites()
    {
        // Create sprites with position and size
        Sprite landscapeSprite = new Sprite(context, R.drawable.landscape, 1.5f, 1.5f);
        landscapeSprite.setParallaxMultiplier(0.5f);  // Background moves slower
        sprites.add(landscapeSprite);

        Sprite knightSprite = new Sprite(context, R.drawable.knight, 1f, 1.5f);
        knightSprite.setParallaxMultiplier(1.0f);  // Foreground moves with scroll
        sprites.add(knightSprite);

    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / height;

        // Set up orthographic projection that accounts for aspect ratio
        // Keep vertical range at -1 to 1, scale horizontal by aspect ratio
        if (aspectRatio > 1) {
            // Landscape: wider than tall

            Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, 1f, -1f, -1f, 1f);
        } else {
            // Portrait: taller than wide
            Matrix.orthoM(projectionMatrix, 0, -1f, 1f, 1f / aspectRatio, -1f / aspectRatio, -1f, 1f);
        }

        Log.d(TAG, "Surface changed: " + width + "x" + height);
    }

    @Override
    public void onDrawFrame() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        shaderProgram.use();

        // Set projection matrix
        GLES20.glUniformMatrix4fv(handles.projectionMatrix, 1, false, projectionMatrix, 0);

        // Set scroll offset uniform (applied by all sprites with their own multiplier)
        GLES20.glUniform1f(handles.scrollOffset, currentScrollOffset);

        // Set gyroscope offsets for device tilt movement
        GLES20.glUniform1f(handles.gyroOffsetX, gyroProcessor.getOffsetX());
        GLES20.glUniform1f(handles.gyroOffsetY, gyroProcessor.getOffsetY());

        // Draw all sprites
        for (Sprite sprite : sprites) {
            spriteRenderer.drawSprite(sprite);
        }
    }

    @Override
    public void onDestroy() {
        // Destroy all sprites
        for (Sprite sprite : sprites) {
            sprite.destroy();
        }
        sprites.clear();

        if (shaderProgram != null) {
            shaderProgram.delete();
        }
        if (textureManager != null) {
            textureManager.destroyAll();
        }
        Log.d(TAG, "Renderer destroyed");
    }

    @Override
    public void onScrollOffsetChanged(float offsetX) {
        // Treat offset=0.5 as the centered position. We want positive offset to move sprites left when user scrolls right,
        // so compute (0.5 - offsetX). At offsetX == 0.5 this becomes 0 (no displacement).
        this.currentScrollOffset = 0.5f - offsetX;
    }

    @Override
    public void onGyroscopeChanged(float rotationX, float rotationY, float rotationZ) {
        // Forward raw sensor data to the processor which handles filtering, integration and limits
        gyroProcessor.onGyroscopeChanged(rotationX, rotationY, rotationZ);
    }

}
