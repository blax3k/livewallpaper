package com.example.livewallpaper;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.example.livewallpaper.gl.ShaderProgram;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.gl.SpriteRenderer;
import com.example.livewallpaper.gl.Handles;
import com.example.livewallpaper.scene.Scene;
import com.example.livewallpaper.scene.SceneLoader;
import com.example.livewallpaper.scene.SceneManager;
import com.example.livewallpaper.scene.Sprite;
import com.example.livewallpaper.sensors.GyroSensorProcessor;
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

    // Manages scene cycling and switching logic
    private SceneManager sceneManager;

    // Flag to request scene switch on GL thread (set from UI thread, consumed on GL thread)
    private volatile boolean sceneSwitchRequested = false;



    public SimpleRenderer(Context context) {
        this.context = context;
        this.sceneLoader = new SceneLoader(context);

        // Try to load scene from JSON, fall back to empty scene if loading fails
        try {
            this.currentScene = sceneLoader.loadScene("girl_back.json");
            Log.d(TAG, "Loaded scene from JSON: " + currentScene.getSceneName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load scene from JSON, using empty scene", e);
            this.currentScene = new Scene("DefaultScene");
        }

        // Initialize the scene manager for handling scene transitions
        this.sceneManager = new SceneManager(context, sceneLoader, textureManager);
        this.sceneManager.initialize(currentScene);
    }

    @Override
    public void onSurfaceCreated() {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        // Enable blending for transparency
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Create shader program using helper
        shaderProgram = new ShaderProgram(ShaderProgram.getVertexShaderCode(), ShaderProgram.getFragmentShaderCode());
        shaderProgram.use();

        int prog = shaderProgram.getProgram();
        handles = new Handles(prog);

        // Create texture manager and sprite renderer
        textureManager = new TextureManager();
        spriteRenderer = new SpriteRenderer(handles);

        // Initialize scene manager with texture manager for scene transitions
        sceneManager = new SceneManager(context, sceneLoader, textureManager);
        sceneManager.initialize(currentScene);

        // Set up gyro scaling callback
        sceneManager.setGyroScalingCallback((newScene, worldHeight) -> {
            if (spritesScaledForGyro) {
                gyroProcessor.applyGyroScalingToNewScene(newScene, worldHeight);
            }
        });

        // Initialize the scene and load textures
        currentScene.initialize(context, textureManager);

        Log.d(TAG, "Surface created and scene initialized");
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
        // Check if scene switch was requested (from UI thread) and perform it here on GL thread
        if (sceneSwitchRequested) {
            sceneSwitchRequested = false;
            sceneManager.cycleToNextScene(currentScene, worldHeight);
        }

        // Update scene transition (handles texture preload, crossfade, and scene switching)
        currentScene = sceneManager.updateTransition();

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        shaderProgram.use();

        // Set projection matrix
        GLES20.glUniformMatrix4fv(handles.projectionMatrixHandle, 1, false, projectionMatrix, 0);

        // Update scroll offset interpolation and get the current value for this frame
        float currentScrollOffset = scrollOffsetProcessor.updateAndGetCurrentOffset();

        // Set scroll offset uniform (applied by all sprites with their own multiplier)
        GLES20.glUniform1f(handles.scrollOffsetHandle, currentScrollOffset);

        // Update gyro offsets and apply uniforms; also manage sprite scaling for gyro motion
        spritesScaledForGyro = gyroProcessor.updateAndApplyGyroUniforms(handles, currentScene, worldHeight, spritesScaledForGyro);

        // Draw all sprites from current scene
        for (Sprite sprite : currentScene.getSprites()) {
            spriteRenderer.drawSprite(sprite);
        }

        // Draw transition overlay sprites (old and new scene sprites during crossfade)
        for (Sprite sprite : sceneManager.getTransitionSprites()) {
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
        // Resume gyro tracking from current position
        gyroProcessor.resume();
        scrollOffsetProcessor.onRendererResume();
    }

    @Override
    public void onRendererPause() {
        // Pause gyro tracking (stop processing sensor data)
        gyroProcessor.pause();
        scrollOffsetProcessor.onRendererPause();
    }

    @Override
    public void onRendererSuspend() {
        // Pause gyro tracking during suspension
        gyroProcessor.pause();
        scrollOffsetProcessor.onRendererPause();
        Log.d(TAG, "Renderer suspended - gyro tracking paused");
    }

    @Override
    public void onRendererSuspendResume() {
        // Resume gyro tracking after suspension
        gyroProcessor.resume();
        scrollOffsetProcessor.onRendererResume();
        Log.d(TAG, "Renderer resumed after suspension - gyro tracking resumed");
    }

    @Override
    public void onGyroscopeChanged(float rotationX, float rotationY, float rotationZ) {
        // Only process gyroscope data if gyro motion is enabled and not paused
        if (MotionConfig.isGyroMotionEnabled() && !gyroProcessor.isPaused()) {
            // Forward raw sensor data to the processor which handles filtering, integration and limits
            gyroProcessor.onGyroscopeChanged(rotationX, rotationY, rotationZ);
        } else if (!MotionConfig.isGyroMotionEnabled()) {
            // When gyro is disabled, reset the processor to avoid stale offsets
            gyroProcessor.reset();
        }
    }

    @Override
    public void onDoubleTap(float x, float y) {
        Log.d(TAG, "Double tap received at screen coordinates (" + x + ", " + y + ")");
        // Request scene switch - will be performed on GL thread during next onDrawFrame
        sceneSwitchRequested = true;
    }


}
