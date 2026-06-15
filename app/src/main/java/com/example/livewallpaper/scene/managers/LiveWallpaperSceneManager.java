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
import com.example.livewallpaper.world.PackLoader;
import com.example.livewallpaper.world.RulesEvaluator;
import com.example.livewallpaper.world.SpriteConditionApplicator;
import com.example.livewallpaper.world.WorldStateManager;

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
    private long lastSceneChangeTimeMs = System.currentTimeMillis();

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

        // Load pack-level flag and rule definitions from assets.
        PackLoader packLoader = new PackLoader(context);
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
                    if (spritesScaledForGyro) {
                        gyroProcessor.applyGyroScalingToNewScene(newScene);
                    }
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
        TimberLog.d(TAG, "onSurfaceChanged: " + width + "x" + height);

        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / (float) height;

        float halfWorldH = WORLD_HEIGHT * 0.5f;
        float halfWorldW = halfWorldH * aspectRatio;

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

        try {
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
            }

            // Update scene transition (handles texture preload, crossfade, and scene switching)
            if (sceneSwitchManager.isTransitioning()) {
                currentScene = sceneSwitchManager.updateTransition(textureManager);
            }

            // Apply xFocus offset when scroll motion is disabled
            if (!ConfigManager.isScrollMotionEnabled()) {
                Scene transitioningScene = sceneSwitchManager.getTransitioningScene();
                if (transitioningScene != null) {
                    scrollOffsetProcessor.setScrollTargetFromXFocus(transitioningScene.getXFocus());
                } else if (currentScene != null) {
                    scrollOffsetProcessor.setScrollTargetFromXFocus(currentScene.getXFocus());
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
        if (ConfigManager.isScrollMotionEnabled()) {
            scrollOffsetProcessor.setScrollTarget(offsetX);
        }
    }

    @Override
    public void onRendererResume(long resumeTimeNs) {
        TimberLog.d(TAG, "onRendererResume called");

        gyroProcessor.resume();
        scrollOffsetProcessor.onRendererResume();

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
    }

    @Override
    public void onRendererSuspend() {
        TimberLog.d(TAG, "onRendererSuspend called");
        gyroProcessor.pause();
        scrollOffsetProcessor.onRendererPause();
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
