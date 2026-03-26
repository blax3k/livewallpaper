package com.example.livewallpaper.scene.managers;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import com.example.livewallpaper.logging.TimberLog;

import com.example.livewallpaper.gl.GLWallpaperRenderer;
import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.ui.editor.managers.SceneFileManager;

/**
 * Scene manager for wallpaper mode using GLWallpaperRenderer.
 * Handles live wallpaper rendering with scene switching, cycling, and gyro-based parallax.
 */
public class LiveWallpaperSceneManager extends BaseSceneManager implements GLWallpaperRenderer {
    private SceneSwitchManager sceneSwitchManager;
    private volatile boolean sceneSwitchRequested = false;
    private static final long SCENE_CYCLE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes in milliseconds
    private long lastSceneChangeTimeMs = System.currentTimeMillis();

    /**
     * Constructor for wallpaper mode that enables scene switching and cycling.
     * Loads the initial scene and sets up automatic scene cycling.
     *
     * @param context the application context
     */
    public LiveWallpaperSceneManager(Context context) {
        super(context, (String) null);

        // Initialize scene manager for wallpaper mode
        SceneFileManager sceneFileManager = new SceneFileManager(context, null);
        this.sceneSwitchManager = new SceneSwitchManager(context, sceneFileManager);

        // Load the initial scene
        try {
            this.currentScene = sceneSwitchManager.loadInitialScene();
            TimberLog.d(TAG, "Loaded initial scene: " + currentScene.getSceneName());
        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to load initial scene, using empty scene", e);
            this.currentScene = new Scene("DefaultScene");
        }

        // Initialize the scene manager with the loaded scene
        this.sceneSwitchManager.initialize(currentScene);
    }

    @Override
    public void onSurfaceCreated() {
        TimberLog.d(TAG, "onSurfaceCreated called");

        // Initialize common GL resources (shader, handles, sprite renderer)
        initializeGLResources();

        // Set up gyro scaling callback
        if (sceneSwitchManager != null) {
            sceneSwitchManager.setGyroScalingCallback((newScene) -> {
                if (spritesScaledForGyro) {
                    gyroProcessor.applyGyroScalingToNewScene(newScene);
                }
            });
        }

        // Initialize common scene resources (load scene, reload textures, etc.)
        initializeSceneResources();

        // Reset the scene change timer
        lastSceneChangeTimeMs = System.currentTimeMillis();

        TimberLog.d(TAG, "Surface created and scene initialized for wallpaper");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        TimberLog.d(TAG, "onSurfaceChanged: " + width + "x" + height);

        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / (float) height;

        // Compute projection so that vertical span == WORLD_HEIGHT units
        float halfWorldH = WORLD_HEIGHT * 0.5f;
        float halfWorldW = halfWorldH * aspectRatio;

        // left, right, bottom, top using world-space extents
        Matrix.orthoM(projectionMatrix, 0, -halfWorldW, halfWorldW, halfWorldH, -halfWorldH, -1f, 1f);

        TimberLog.d(TAG, "Surface changed for wallpaper: " + width + "x" + height);
    }

    /**
     * Called on every frame for wallpaper rendering. Handles scene switching and drawing.
     */
    @Override
    public void onDrawFrame() {
        if (currentScene == null) {
            TimberLog.w(TAG, "onDrawFrame() called but scene is null");
            return;
        }

        if (textureManager != null) {
            textureManager.processPendingUploads();
        }

        if(sceneSwitchManager == null)
        {
            return;
        }
        // Check if scene switch was requested (from UI thread) and perform it here on GL thread
        // Only proceed if no transition is already in progress
        if (sceneSwitchRequested && !sceneSwitchManager.isTransitioning()) {
            sceneSwitchRequested = false;
            sceneSwitchManager.cycleToNextScene(currentScene);
            lastSceneChangeTimeMs = System.currentTimeMillis();
        }

        // Update scene transition (handles texture preload, crossfade, and scene switching)
        if ( sceneSwitchManager.isTransitioning()) {
            currentScene = sceneSwitchManager.updateTransition(textureManager);
        }

        // Apply xFocus offset when scroll motion is disabled
        if (!MotionConfig.isScrollMotionEnabled()) {
            // If we're in a transition, smoothly transition to the next scene's xFocus
            // Otherwise, use the current scene's xFocus
            Scene transitioningScene =  sceneSwitchManager.getTransitioningScene();
            if (transitioningScene != null) {
                scrollOffsetProcessor.setScrollTargetFromXFocus(transitioningScene.getXFocus());
            } else {
                scrollOffsetProcessor.setScrollTargetFromXFocus(currentScene.getXFocus());
            }
        }

        // Common rendering logic
        performRenderFrame();
    }

    @Override
    public void onDestroy() {
        TimberLog.d(TAG, "onDestroy called");

        // Destroy all sprites
        if (currentScene != null) {
            currentScene.destroy();
        }

        if (shaderProgram != null) {
            shaderProgram.delete();
        }

        if (textureManager != null) {
            textureManager.destroyAll();
        }
        TimberLog.d(TAG, "Renderer destroyed for wallpaper");
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
        TimberLog.d(TAG, "onRendererSuspendResume called");

        // Resume gyro tracking after suspension
        gyroProcessor.resume();
        scrollOffsetProcessor.onRendererResume();

        // Check if it's time for automatic scene cycling (every 5 minutes)
        long currentTimeMs = System.currentTimeMillis();
        if (currentTimeMs - lastSceneChangeTimeMs >= SCENE_CYCLE_INTERVAL_MS && sceneSwitchManager != null) {
            sceneSwitchManager.cycleToNextScene(currentScene);
            lastSceneChangeTimeMs = currentTimeMs;
            TimberLog.d(TAG, "Auto-cycling to next scene (5 minutes elapsed)");
        }

        TimberLog.d(TAG, "Renderer resumed after suspension - gyro tracking resumed");
    }

    @Override
    public void onRendererPause() {
        TimberLog.d(TAG, "onRendererPause called");

        // Pause gyro tracking (stop processing sensor data)
        gyroProcessor.pause();
        scrollOffsetProcessor.onRendererPause();
    }

    @Override
    public void onRendererSuspend() {
        TimberLog.d(TAG, "onRendererSuspend called");

        // Pause gyro tracking during suspension
        gyroProcessor.pause();
        scrollOffsetProcessor.onRendererPause();
        TimberLog.d(TAG, "Renderer suspended - gyro tracking paused");
    }

    @Override
    public void onRendererSuspendResume() {
        this.onRendererResume(System.nanoTime());
    }

    @Override
    public void onDoubleTap(float x, float y) {
        TimberLog.d(TAG, "onDoubleTap received at screen coordinates (" + x + ", " + y + ")");

        // Prevent interrupting an ongoing transition
        if (sceneSwitchManager != null && sceneSwitchManager.isTransitioning()) {
            TimberLog.d(TAG, "Double tap ignored: transition already in progress");
            return;
        }

        // Request scene switch - will be performed on GL thread during next onDrawFrame
        sceneSwitchRequested = true;
    }

    /**
     * Refresh the available scene list from disk.
     * This should be called when the scene list has changed (e.g., after delete, reset, or add operations).
     *
     * @param sceneFileManager the SceneFileManager to reload scenes from
     */
    public void refreshSceneList(SceneFileManager sceneFileManager) {
        if (sceneSwitchManager != null) {
            sceneSwitchManager.reloadAvailableScenes(sceneFileManager);
            TimberLog.d(TAG, "Scene list refreshed in renderer");
        }
    }
}





