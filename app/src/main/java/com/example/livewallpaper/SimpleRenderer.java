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
import com.example.livewallpaper.sensors.GyroScaleCalculator;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.sensors.ScrollOffsetProcessor;

/**
 * Simple example renderer that displays a blue square with a texture (knight.png) in the center of the view.
 */
public class SimpleRenderer implements GLWallpaperRenderer {
    private static final String TAG = "SimpleRenderer";

    private Context context;
    private ShaderProgram shaderProgram;

    private Scene currentScene;
    private SceneLoader sceneLoader;

    private Handles handles;
    private float[] projectionMatrix = new float[16];
    // Manages smooth scrolling interpolation with time-based easing
    private ScrollOffsetProcessor scrollOffsetProcessor = new ScrollOffsetProcessor();

    private GyroSensorProcessor gyroProcessor = new GyroSensorProcessor();
    private TextureManager textureManager;
    private SpriteRenderer spriteRenderer;

    // World-space height which maps to the device's vertical view. A sprite with height == worldHeight
    // will fill the vertical screen on any device. Change this to zoom in/out uniformly.
    private float worldHeight = 10f;

    // Track whether sprites are currently scaled for gyro motion
    private boolean spritesScaledForGyro = false;


    public SimpleRenderer(Context context) {
        this.context = context;
        this.sceneLoader = new SceneLoader(context);

        // Try to load scene from JSON, fall back to empty scene if loading fails
        try {
            this.currentScene = sceneLoader.loadScene("default_scene.json");
            Log.d(TAG, "Loaded scene from JSON: " + currentScene.getSceneName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load scene from JSON, using empty scene", e);
            this.currentScene = new Scene("DefaultScene");
        }
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

        // Reload scene if it was destroyed (sprite count is 0)
        if (currentScene.getSpriteCount() == 0) {
            try {
                currentScene = sceneLoader.loadScene("default_scene.json");
                Log.d(TAG, "Reloaded scene from JSON after destruction: " + currentScene.getSceneName());
            } catch (Exception e) {
                Log.e(TAG, "Failed to reload scene from JSON, using empty scene", e);
                currentScene = new Scene("DefaultScene");
            }
        }

        // Resolve textures for each sprite through TextureManager
        currentScene.initialize(context, textureManager);

        // Reapply gyro scaling if it was previously enabled
        if (spritesScaledForGyro && MotionConfig.isGyroMotionEnabled()) {
            float scaleFactor = GyroScaleCalculator.calculateScaleFactor(
                gyroProcessor.getMotionOffsetLimit(),
                worldHeight
            );
            currentScene.applyGyroScaling(scaleFactor);
            Log.d(TAG, "Reapplied gyro scaling after surface recreation");
        }

        Log.d(TAG, "Surface created");
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
        float currentScrollOffset = scrollOffsetProcessor.updateAndGetCurrentOffset();

        // Set scroll offset uniform (applied by all sprites with their own multiplier)
        GLES20.glUniform1f(handles.scrollOffsetHandle, currentScrollOffset);

        // Update gyro offset interpolation and set gyroscope offsets for device tilt movement
        boolean gyroEnabled = MotionConfig.isGyroMotionEnabled();
        if (gyroEnabled) {
            float gyroOffsetX = gyroProcessor.updateAndGetCurrentOffsetX();
            float gyroOffsetY = gyroProcessor.updateAndGetCurrentOffsetY();
            GLES20.glUniform1f(handles.gyroOffsetXHandle, gyroOffsetX);
            GLES20.glUniform1f(handles.gyroOffsetYHandle, gyroOffsetY);

            // Apply sprite scaling for gyro motion if not already scaled
            if (!spritesScaledForGyro) {
                float scaleFactor = GyroScaleCalculator.calculateScaleFactor(
                    gyroProcessor.getMotionOffsetLimit(),
                    worldHeight
                );
                currentScene.applyGyroScaling(scaleFactor);
                spritesScaledForGyro = true;
            }
        } else {
            // When gyro is disabled, set offsets to zero
            GLES20.glUniform1f(handles.gyroOffsetXHandle, 0f);
            GLES20.glUniform1f(handles.gyroOffsetYHandle, 0f);

            // Reset sprite scaling if previously scaled
            if (spritesScaledForGyro) {
                currentScene.resetGyroScaling();
                spritesScaledForGyro = false;
            }
        }

        // Draw all sprites
        for (Sprite sprite : currentScene.getSprites()) {
            spriteRenderer.drawSprite(sprite);
        }
    }

    @Override
    public void onDestroy() {
        // Destroy all sprites
        currentScene.destroy();

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
        // Only update scroll target if scroll motion is enabled
        if (MotionConfig.isScrollMotionEnabled()) {
            scrollOffsetProcessor.setScrollTarget(offsetX);
        } else {
            // If scroll motion is disabled, reset to neutral position
            scrollOffsetProcessor.disableScrollMotion();
        }
    }

    @Override
    public void onRendererResume(long resumeTimeNs) {
        // Delegate to the interpolator to invalidate its frame timer
        scrollOffsetProcessor.onRendererResume();
    }

    @Override
    public void onRendererPause() {
        // Delegate to the interpolator to invalidate its frame timer
        scrollOffsetProcessor.onRendererPause();
    }

    @Override
    public void onGyroscopeChanged(float rotationX, float rotationY, float rotationZ) {
        // Only process gyroscope data if gyro motion is enabled
        if (MotionConfig.isGyroMotionEnabled()) {
            // Forward raw sensor data to the processor which handles filtering, integration and limits
            gyroProcessor.onGyroscopeChanged(rotationX, rotationY, rotationZ);
        } else {
            // When gyro is disabled, reset the processor to avoid stale offsets
            gyroProcessor.reset();
        }
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
