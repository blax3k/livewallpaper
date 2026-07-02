package com.example.livewallpaper.scene.managers;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import com.example.livewallpaper.logging.TimberLog;

import com.example.livewallpaper.gl.GLWallpaperRenderer;
import com.example.livewallpaper.scene.models.FlagData;
import com.example.livewallpaper.scene.models.RuleData;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.sensors.ConfigManager;
import com.example.livewallpaper.managers.SceneFileManager;
import com.example.livewallpaper.prefs.WallpaperPreferences;
import com.example.livewallpaper.world.PackLoader;
import com.example.livewallpaper.world.RulesEvaluator;
import com.example.livewallpaper.world.SpriteConditionApplicator;
import com.example.livewallpaper.world.WorldStateManager;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Scene manager for wallpaper mode using GLWallpaperRenderer.
 * Handles live wallpaper rendering with scene switching, cycling, and gyro-based parallax.
 */
public class LiveWallpaperSceneManager extends BaseSceneManager implements GLWallpaperRenderer {
    private SceneSwitchManager sceneSwitchManager;
    private volatile boolean sceneSwitchRequested = false;
    private volatile boolean projectReloadRequested = false;
    private static final long SCENE_CYCLE_INTERVAL_MS = 5 * 60 * 1000;
    // Historical design max for xFocus/yFocus pan (world units), calibrated against a typical
    // narrow phone. Never exceeded even when a screen's aspect ratio would otherwise allow more.
    private static final float MAX_FOCUS_PAN_OFFSET = 2.5f;
    // Reserved slack (world units) held back from panning so simultaneous gyro-driven parallax
    // doesn't reveal a sprite's edge. Matches the ~0.25-unit buffer already implied by the
    // original SCROLL_SCALE calibration (portrait slack 2.75 - pan 2.5 = 0.25).
    private static final float GYRO_BUFFER = 0.25f;
    private long lastSceneChangeTimeMs = System.currentTimeMillis();
    private int debugFrameCounter = 0;

    // Pending surface dimensions to apply on the GL thread (glViewport must be called there).
    // Written by surfaceChanged (main thread), consumed by onDrawFrame (GL thread).
    private volatile int pendingViewportWidth = 0;
    private volatile int pendingViewportHeight = 0;

    private final WorldStateManager worldState;
    private final RulesEvaluator rulesEvaluator;
    private final SpriteConditionApplicator spriteConditionApplicator;
    private final List<RuleData> loadedRules;
    // Set to true whenever flags change so conditions are re-applied on the next draw
    private volatile boolean conditionsDirty = false;
    private String lastAppliedConditionsScene = null;

    /**
     * Constructor for wallpaper mode that enables scene switching and cycling.
     * Loads the initial scene and sets up automatic scene cycling.
     *
     * @param context the application context
     */
    public LiveWallpaperSceneManager(Context context) {
        super(context, (String) null);

        this.worldState = WorldStateManager.get(context);

        // Resolve pack directory: use the parent of the active project's scenes dir when available.
        File packDir = null;
        String activeScenesDir = WallpaperPreferences.getActiveScenesDir(context);
        if (activeScenesDir != null) {
            File scenesDir = new File(activeScenesDir);
            if (scenesDir.isDirectory()) packDir = scenesDir.getParentFile();
        }

        // Load pack-level flag and rule definitions; prefer downloaded project files over assets.
        PackLoader packLoader = new PackLoader(context, packDir);
        List<FlagData> loadedFlags = packLoader.loadFlags();
        this.loadedRules = packLoader.loadRules();

        // Initialize any flags that don't yet have state (new install or pack update).
        worldState.initializeFlags(loadedFlags);

        // Run an immediate evaluation on startup so flags reflect the current time
        // before the first scene is selected. Bypasses the 5-minute debounce here
        // since the app was just launched — we want correct flags right away.
        rulesEvaluator = new RulesEvaluator(worldState);
        spriteConditionApplicator = new SpriteConditionApplicator(worldState);
        rulesEvaluator.evaluate(loadedRules);
        worldState.recordEvaluation();
        conditionsDirty = true; // apply conditions once the first scene is loaded

        // Initialize scene manager for wallpaper mode
        try {
            SceneFileManager sceneFileManager = new SceneFileManager(context, null);
            this.sceneSwitchManager = new SceneSwitchManager(context, sceneFileManager);

            // Load the initial scene
            try {
                this.currentScene = sceneSwitchManager.loadInitialScene();
                if (this.currentScene != null) {
                    TimberLog.d(TAG, "Loaded initial scene: " + currentScene.getSceneName());
                } else {
                    TimberLog.e(TAG, "loadInitialScene returned null");
                    this.currentScene = new Scene("DefaultScene");
                }
            } catch (Exception e) {
                TimberLog.e(TAG, "Failed to load initial scene, using fallback empty scene", e);
                this.currentScene = new Scene("DefaultScene");
            }

            // Initialize the scene manager with the loaded scene
            try {
                this.sceneSwitchManager.initialize(currentScene);
            } catch (Exception e) {
                TimberLog.e(TAG, "Failed to initialize scene manager", e);
            }
        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to initialize LiveWallpaperSceneManager", e);
            if (this.currentScene == null) {
                this.currentScene = new Scene("DefaultScene");
            }
        }
    }

    @Override
    public void onSurfaceCreated() {
        TimberLog.d(TAG, "onSurfaceCreated called");

        try {
            // Initialize common GL resources (shader, handles, sprite renderer)
            initializeGLResources();

            // Set up gyro scaling callback
            if (sceneSwitchManager != null) {
                sceneSwitchManager.setGyroScalingCallback((newScene) -> {
                    // Always attempt to scale — applyGyroScalingToNewScene is a no-op when
                    // gyro is disabled, so no guard on spritesScaledForGyro is needed here.
                    // isLandscape is read at callback-invoke time (GL thread).
                    gyroProcessor.applyGyroScalingToNewScene(newScene, isLandscape);
                });
            }

            // Initialize common scene resources (load scene, reload textures, etc.)
            initializeSceneResources();

            if (currentScene == null) {
                TimberLog.e(TAG, "Scene is null after initialization, using fallback");
                currentScene = new Scene("FallbackScene");
            }

            lastSceneChangeTimeMs = System.currentTimeMillis();
            TimberLog.d(TAG, "Surface created and scene initialized for wallpaper");
        } catch (Exception e) {
            TimberLog.e(TAG, "Error in onSurfaceCreated: " + e.getMessage(), e);
            if (currentScene == null) {
                currentScene = new Scene("FallbackScene");
            }
        }
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        // glViewport MUST be called on the GL thread (EGL context owner).
        // Store the dimensions here and apply them at the top of onDrawFrame.
        pendingViewportWidth = width;
        pendingViewportHeight = height;
    }

    private void applyPendingViewport(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / (float) height;

        boolean wasLandscape = isLandscape;
        isLandscape = width > height;

        if (isLandscape != wasLandscape) {
            // Orientation changed: zero X scroll and force gyro scaling re-application with the
            // correct orientation coefficient (portrait=0.1, landscape=0.15).
            scrollOffsetProcessor.setScrollOffsetImmediate(0f);
            spritesScaledForGyro = false;
        }

        float halfWorldW, halfWorldH;
        if (isLandscape) {
            // Landscape: fix world WIDTH = WORLD_HEIGHT (mirrors portrait fixing world HEIGHT).
            // The full 10-unit horizontal extent is always visible; yFocus scrolls vertically.
            halfWorldW = WORLD_HEIGHT * 0.5f;
            halfWorldH = halfWorldW / aspectRatio;
        } else {
            // Portrait: fix world HEIGHT so the full vertical extent is always visible.
            halfWorldH = WORLD_HEIGHT * 0.5f;
            halfWorldW = halfWorldH * aspectRatio;
        }

        Matrix.orthoM(projectionMatrix, 0, -halfWorldW, halfWorldW, halfWorldH, -halfWorldH, -1f, 1f);

        // Sprites are authored to fill the full WORLD_HEIGHT square (+/-5 world units). Panning
        // via xFocus/yFocus shifts sprite positions under the fixed viewport window, so the pan
        // magnitude must never exceed the slack between that window and the sprite's edge — the
        // "slack" on the axis whose extent is derived from the device's aspect ratio rather than
        // clamped to WORLD_HEIGHT. A perfectly square screen has zero slack on both axes (the
        // viewport exactly fills the sprite bounds), so no panning is possible; narrower/wider
        // screens get proportionally more room, capped at the original design max of 2.5 units
        // (with GYRO_BUFFER held back so simultaneous gyro parallax doesn't reveal sprite edges).
        float halfWorldContent = WORLD_HEIGHT * 0.5f;
        float slackX = Math.max(0f, halfWorldContent - halfWorldW - GYRO_BUFFER);
        float slackY = Math.max(0f, halfWorldContent - halfWorldH - GYRO_BUFFER);
        scrollOffsetProcessor.setMaxScrollOffset(Math.min(MAX_FOCUS_PAN_OFFSET, slackX));
        verticalScrollOffsetProcessor.setMaxScrollOffset(Math.min(MAX_FOCUS_PAN_OFFSET, slackY));

        TimberLog.d(TAG, "LANDSCAPE_DEBUG: applied viewport " + width + "x" + height
                + " aspect=" + aspectRatio
                + " isLandscape=" + isLandscape
                + " halfWorldW=" + halfWorldW
                + " halfWorldH=" + halfWorldH);
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

        try {
            // Apply any pending surface-size change on the GL thread (glViewport must be here).
            int pw = pendingViewportWidth;
            int ph = pendingViewportHeight;
            if (pw > 0 && ph > 0) {
                pendingViewportWidth = 0;
                pendingViewportHeight = 0;
                applyPendingViewport(pw, ph);
            }

            if (textureManager != null) {
                textureManager.processPendingUploads();
            }

            if (sceneSwitchManager == null) {
                return;
            }

            // If a project switch was requested, reload the scene list on the GL thread
            // so it doesn't race with the render loop, then immediately request a switch.
            if (projectReloadRequested) {
                projectReloadRequested = false;
                SceneFileManager sfm = new SceneFileManager(context, null);
                sceneSwitchManager.reloadAvailableScenes(sfm);
                sceneSwitchRequested = true;
            }

            // Check if scene switch was requested (from UI thread) and perform it here on GL thread
            // Only proceed if no transition is already in progress
            if (sceneSwitchRequested && !sceneSwitchManager.isTransitioning()) {
                sceneSwitchRequested = false;
                sceneSwitchManager.cycleToNextScene(currentScene);
                lastSceneChangeTimeMs = System.currentTimeMillis();
                // Pre-apply conditions to the incoming scene so its sprites already hold their
                // final values when beginFade() copies positions for the wipe animation.
                // Without this, sprites transition from default values and snap to their
                // condition-modified state only after the transition completes.
                Scene incomingScene = sceneSwitchManager.getTransitioningScene();
                if (incomingScene != null) {
                    spriteConditionApplicator.applyToScene(incomingScene);
                }
            }

            // Update scene transition (handles texture preload, crossfade, and scene switching)
            if (sceneSwitchManager.isTransitioning()) {
                Scene previousScene = currentScene;
                currentScene = sceneSwitchManager.updateTransition(textureManager);
                if (currentScene != previousScene) {
                    // New scene is now active — force gyro scaling re-application so the new
                    // scene gets the correct orientation coefficient on this very frame.
                    spritesScaledForGyro = false;
                }
            }

            if (isLandscape) {
                // Landscape: full width is always visible — lock horizontal scroll to center
                // and drive vertical position from yFocus.
                scrollOffsetProcessor.setScrollTargetFromXFocus(0.5f);

                float yFocus = sceneSwitchManager.getYFocus();
                verticalScrollOffsetProcessor.setScrollTargetFromYFocus(yFocus);
            } else {
                // Portrait: apply xFocus offset when scroll motion is disabled.
                if (!ConfigManager.isScrollMotionEnabled()) {
                    float xFocus = sceneSwitchManager.getXFocus();
                    scrollOffsetProcessor.setScrollTargetFromXFocus(xFocus);
                }
            }

            // Apply sprite conditions when the scene changes or flags have been updated
            String currentSceneName = currentScene != null ? currentScene.getSceneName() : null;
            if (conditionsDirty || !java.util.Objects.equals(currentSceneName, lastAppliedConditionsScene)) {
                spriteConditionApplicator.applyToScene(currentScene);
                lastAppliedConditionsScene = currentSceneName;
                conditionsDirty = false;
            }

            // Common rendering logic
            performRenderFrame();
        } catch (Exception e) {
            TimberLog.e(TAG, "Error in onDrawFrame: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroy() {
        TimberLog.d(TAG, "onDestroy called");

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
        if (isLandscape) {
            return;
        }
        if (ConfigManager.isScrollMotionEnabled()) {
            scrollOffsetProcessor.setScrollTarget(offsetX);
        }
    }

    @Override
    public void onRendererResume(long resumeTimeNs) {
        TimberLog.d(TAG, "onRendererResume called");

        gyroProcessor.resume();
        scrollOffsetProcessor.onRendererResume();
        verticalScrollOffsetProcessor.onRendererResume();

        // Evaluate rules before cycling scenes so updated flags influence scene selection.
        // The 5-minute debounce prevents redundant evaluations on rapid screen on/off cycles.
        if (worldState.shouldEvaluateNow()) {
            TimberLog.d(TAG, "Running rules evaluation");
            rulesEvaluator.evaluate(loadedRules);
            worldState.recordEvaluation();
            conditionsDirty = true;
        }

        // Cycle to the next scene if enough time has elapsed
        long currentTimeMs = System.currentTimeMillis();
        if (currentTimeMs - lastSceneChangeTimeMs >= SCENE_CYCLE_INTERVAL_MS && sceneSwitchManager != null) {
            sceneSwitchManager.cycleToNextScene(currentScene);
            lastSceneChangeTimeMs = currentTimeMs;
            TimberLog.d(TAG, "Auto-cycling to next scene (5 minutes elapsed)");
        }

        TimberLog.d(TAG, "Renderer resumed — gyro tracking resumed");
    }

    @Override
    public void onRendererPause() {
        TimberLog.d(TAG, "onRendererPause called");
        gyroProcessor.pause();
        scrollOffsetProcessor.onRendererPause();
        verticalScrollOffsetProcessor.onRendererPause();
    }

    @Override
    public void onRendererSuspend() {
        TimberLog.d(TAG, "onRendererSuspend called");
        gyroProcessor.pause();
        scrollOffsetProcessor.onRendererPause();
        verticalScrollOffsetProcessor.onRendererPause();
        TimberLog.d(TAG, "Renderer suspended — gyro tracking paused");
    }

    @Override
    public void onRendererSuspendResume() {
        this.onRendererResume(System.nanoTime());
    }

    @Override
    public void onDoubleTap(float x, float y) {
        TimberLog.d(TAG, "onDoubleTap received at screen coordinates (" + x + ", " + y + ")");

        boolean isProgrammatic = (x == 0 && y == 0);
        if (!isProgrammatic && !context.getResources().getBoolean(com.example.livewallpaper.R.bool.double_tap_to_change_scene_enabled)) {
            TimberLog.d(TAG, "Double tap ignored: disabled in configuration for this flavor");
            return;
        }

        if (sceneSwitchManager != null && sceneSwitchManager.isTransitioning()) {
            TimberLog.d(TAG, "Double tap ignored: transition already in progress");
            return;
        }

        sceneSwitchRequested = true;
    }

    /**
     * Refresh the available scene list from disk.
     * Called when the scene list has changed (e.g., after delete, reset, or add operations).
     */
    public void refreshSceneList(SceneFileManager sceneFileManager) {
        if (sceneSwitchManager != null) {
            sceneSwitchManager.reloadAvailableScenes(sceneFileManager);
            sceneSwitchRequested = true;
            TimberLog.d(TAG, "Scene list refreshed in renderer, scene switch requested");
        }
    }

    /**
     * Request that the scene list be reloaded from WallpaperPreferences paths and a scene
     * switch be triggered — all on the GL thread, avoiding races with the render loop.
     * Use this when switching between downloaded projects.
     */
    public void requestProjectReload() {
        projectReloadRequested = true;
        TimberLog.d(TAG, "Project reload requested — will execute on GL thread");
    }
}
