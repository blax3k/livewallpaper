package com.example.livewallpaper;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.example.livewallpaper.gl.ShaderProgram;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.gl.SpriteRenderer;
import com.example.livewallpaper.gl.Handles;
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
    private List<Sprite> sprites;

    private Handles handles;
    private float[] projectionMatrix = new float[16];
    // Manages smooth scrolling interpolation with time-based easing
    private ScrollOffsetInterpolator scrollOffsetInterpolator = new ScrollOffsetInterpolator();

    private GyroSensorProcessor gyroProcessor = new GyroSensorProcessor();
    private TextureManager textureManager;
    private SpriteRenderer spriteRenderer;

    // World-space height which maps to the device's vertical view. A sprite with height == worldHeight
    // will fill the vertical screen on any device. Change this to zoom in/out uniformly.
    private float worldHeight = 10f;


    public SimpleRenderer(Context context) {
        this.context = context;
        this.sprites = new ArrayList<>();
    }

    @Override
    public void onSurfaceCreated() {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        // Enable blending for transparency
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Create shader program using helper
        shaderProgram = new ShaderProgram(getVertexShaderCode(), getFragmentShaderCode());
        shaderProgram.use();

        int prog = shaderProgram.getProgram();
        handles = new Handles(prog);

        // Create texture manager and sprite renderer
        textureManager = new TextureManager();
        spriteRenderer = new SpriteRenderer(handles);

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
        Sprite landscapeSprite = new Sprite(context, R.drawable.testscape, 10.0f, 10.0f);
        landscapeSprite.setParallaxMultiplier(0.5f);  // Background moves slower
        sprites.add(landscapeSprite);

        Sprite towerSprite = new Sprite(context, R.drawable.tower, 10f, 10f);
        towerSprite.setParallaxMultiplier(1.0f);
        sprites.add(towerSprite);

        Sprite knightSprite = new Sprite(context, R.drawable.knight, 2.5f, 5f);
        knightSprite.setParallaxMultiplier(1.5f);  // Foreground moves with scroll
        sprites.add(knightSprite);
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / (float) height;

        // Compute projection so that vertical span == worldHeight units
        // half extents in world units
        float halfWorldH = worldHeight * 0.5f;
        float halfWorldW = halfWorldH * aspectRatio;

        // left, right, bottom, top using world-space extents
        Matrix.orthoM(projectionMatrix, 0, -halfWorldW, halfWorldW, halfWorldH, -halfWorldH, -1f, 1f);

        Log.d(TAG, "Surface changed: " + width + "x" + height);
    }

    @Override
    public void onDrawFrame() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        shaderProgram.use();

        // Set projection matrix
        GLES20.glUniformMatrix4fv(handles.projectionMatrixHandle, 1, false, projectionMatrix, 0);

        // Update scroll offset interpolation and get the current value for this frame
        float currentScrollOffset = scrollOffsetInterpolator.updateAndGetCurrentOffset();

        // Set scroll offset uniform (applied by all sprites with their own multiplier)
        GLES20.glUniform1f(handles.scrollOffsetHandle, currentScrollOffset);

        // Set gyroscope offsets for device tilt movement
        GLES20.glUniform1f(handles.gyroOffsetXHandle, gyroProcessor.getOffsetX());
        GLES20.glUniform1f(handles.gyroOffsetYHandle, gyroProcessor.getOffsetY());

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
        // Delegate to the interpolator to set the scroll target
        scrollOffsetInterpolator.setScrollTarget(offsetX);
    }

    @Override
    public void onRendererResume(long resumeTimeNs) {
        // Delegate to the interpolator to invalidate its frame timer
        scrollOffsetInterpolator.onRendererResume();
    }

    @Override
    public void onRendererPause() {
        // Delegate to the interpolator to invalidate its frame timer
        scrollOffsetInterpolator.onRendererPause();
    }

    @Override
    public void onGyroscopeChanged(float rotationX, float rotationY, float rotationZ) {
        // Forward raw sensor data to the processor which handles filtering, integration and limits
        gyroProcessor.onGyroscopeChanged(rotationX, rotationY, rotationZ);
    }

    private String getVertexShaderCode() {
        return "uniform mat4 projectionMatrix;"
                + "uniform float scrollOffset;"
                + "uniform float gyroOffsetX;"
                + "uniform float gyroOffsetY;"
                + "attribute vec4 vPosition;"
                + "attribute vec2 vTexCoord;"
                + "attribute float parallaxMultiplier;"
                + "varying vec2 texCoord;"

                + "void main() {"
                + "  vec4 position = vPosition;"
                + "  position.x += scrollOffset * parallaxMultiplier + gyroOffsetX * parallaxMultiplier;"
                + "  position.y += gyroOffsetY * parallaxMultiplier;"
                + "  gl_Position = projectionMatrix * position;"
                + "  texCoord = vTexCoord;"
                + "}";
    }

    private String getFragmentShaderCode() {
        return "precision mediump float;"
                + "uniform sampler2D samplerTexture;"
                + "varying vec2 texCoord;"
                + "void main() {"
                + "  vec4 texColor = texture2D(samplerTexture, texCoord);"
                + "  gl_FragColor = texColor;"
                + "}";
    }
}
