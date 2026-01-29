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
import com.example.livewallpaper.scene.SceneManager;
import com.example.livewallpaper.scene.Sprite;
import com.example.livewallpaper.sensors.GyroSensorProcessor;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.sensors.ScrollOffsetProcessor;
import com.example.livewallpaper.ui.SceneFileManager;
import com.example.livewallpaper.ui.ScenePreviewRenderer;

/**
 * Simple example renderer that displays a blue square with a texture (knight.png) in the center of the view.
 */
public class SimpleRenderer implements GLWallpaperRenderer {
    private static final String TAG = "SimpleRenderer";

    private final Context context;
    private ShaderProgram shaderProgram;

    private Scene currentScene;

    private Handles handles;
    private final float[] projectionMatrix = new float[16];
    // Manages smooth scrolling interpolation with time-based easing
    private final ScrollOffsetProcessor scrollOffsetProcessor = new ScrollOffsetProcessor();

    private final GyroSensorProcessor gyroProcessor = new GyroSensorProcessor();
    private SpriteRenderer spriteRenderer;

    // World-space height which maps to the device's vertical view. A sprite with height == worldHeight
    // will fill the vertical screen on any device. Change this to zoom in/out uniformly.
    private final float WORLD_HEIGHT = 10f;

    // Track whether sprites are currently scaled for gyro motion
    private boolean spritesScaledForGyro = false;

    // Manages scene cycling and switching logic
    private final SceneManager sceneManager;

    // Flag to request scene switch on GL thread (set from UI thread, consumed on GL thread)
    private volatile boolean sceneSwitchRequested = false;

    // Automatic scene cycling based on time
    private static final long SCENE_CYCLE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes in milliseconds
    private long lastSceneChangeTimeMs = System.currentTimeMillis();



    public SimpleRenderer(Context context) {
        this.context = context;
        SceneFileManager sceneFileManager = new SceneFileManager(context, null);
        this.sceneManager = new SceneManager(context, sceneFileManager);

        // Load the initial scene
        try {
            this.currentScene = sceneManager.loadInitialScene();
            Log.d(TAG, "Loaded initial scene: " + currentScene.getSceneName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load initial scene, using empty scene", e);
            this.currentScene = new Scene("DefaultScene");
        }

        // Initialize the scene manager with the loaded scene
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

        // Create sprite renderer
        spriteRenderer = new SpriteRenderer(handles);

        // Set up gyro scaling callback
        sceneManager.setGyroScalingCallback((newScene, worldHeight) -> {
            if (spritesScaledForGyro) {
                gyroProcessor.applyGyroScalingToNewScene(newScene, worldHeight);
            }
        });

        // Initialize the scene and load textures
        currentScene.initialize(context, sceneManager.getTextureManager());

        // Reset the scene change timer
        lastSceneChangeTimeMs = System.currentTimeMillis();

        Log.d(TAG, "Surface created and scene initialized");
    }


    @Override
    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / (float) height;

        // Compute projection so that vertical span == worldHeight units
        // half extents in world units
        float halfWorldH = WORLD_HEIGHT * 0.5f;
        float halfWorldW = halfWorldH * aspectRatio;

        // left, right, bottom, top using world-space extents
        Matrix.orthoM(projectionMatrix, 0, -halfWorldW, halfWorldW, halfWorldH, -halfWorldH, -1f, 1f);

        Log.d(TAG, "Surface changed: " + width + "x" + height);
    }

    @Override
    public void onDrawFrame() {
        // Process any pending async texture uploads that are ready (must be on GL thread)
        TextureManager textureManager = sceneManager.getTextureManager();
        if (textureManager != null) {
            textureManager.processPendingUploads();
        }

        // Check if scene switch was requested (from UI thread) and perform it here on GL thread
        if (sceneSwitchRequested) {
            sceneSwitchRequested = false;
            sceneManager.cycleToNextScene(currentScene, WORLD_HEIGHT);
            lastSceneChangeTimeMs = System.currentTimeMillis();
        }

        // Update scene transition (handles texture preload, crossfade, and scene switching)
        currentScene = sceneManager.updateTransition();

        // Apply xFocus offset when scroll motion is disabled
        if (!MotionConfig.isScrollMotionEnabled()) {
            // If we're in a transition, smoothly transition to the next scene's xFocus
            // Otherwise, use the current scene's xFocus
            Scene transitioningScene = sceneManager.getTransitioningScene();
            if (transitioningScene != null) {
                scrollOffsetProcessor.setScrollTargetFromXFocus(transitioningScene.getXFocus());
            } else {
                scrollOffsetProcessor.setScrollTargetFromXFocus(currentScene.getXFocus());
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        shaderProgram.use();

        // Set projection matrix
        GLES20.glUniformMatrix4fv(handles.projectionMatrixHandle, 1, false, projectionMatrix, 0);

        // Update scroll offset interpolation and get the current value for this frame
        float currentScrollOffset = scrollOffsetProcessor.updateAndGetCurrentOffset();

        // Set scroll offset uniform (applied by all sprites with their own multiplier)
        GLES20.glUniform1f(handles.scrollOffsetHandle, currentScrollOffset);

        // Update gyro offsets and apply uniforms; also manage sprite scaling for gyro motion
        spritesScaledForGyro = gyroProcessor.updateAndApplyGyroUniforms(handles, currentScene, WORLD_HEIGHT, spritesScaledForGyro);

        // Draw all sprites from current scene
        // During transitions, the SceneTransitionManager adds new scene sprites to the old scene
        // with proper parallax sorting, so we only need to draw from currentScene
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

        TextureManager textureManager = sceneManager.getTextureManager();
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
        }
        // When scroll motion is disabled, completely ignore scroll input
        // The xFocus value will be applied in onDrawFrame
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

        // Check if it's time for automatic scene cycling (every 5 minutes)
        long currentTimeMs = System.currentTimeMillis();
        if (currentTimeMs - lastSceneChangeTimeMs >= SCENE_CYCLE_INTERVAL_MS) {
            sceneManager.cycleToNextScene(currentScene, WORLD_HEIGHT);
            lastSceneChangeTimeMs = currentTimeMs;
            Log.d(TAG, "Auto-cycling to next scene (5 minutes elapsed)");
        }

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
