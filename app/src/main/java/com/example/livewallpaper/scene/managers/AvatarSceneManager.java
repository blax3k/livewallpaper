package com.example.livewallpaper.scene.managers;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.SceneLoader;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.SpriteData;
import com.example.livewallpaper.sensors.ConfigManager;

import java.io.IOException;

/**
 * Scene manager for displaying a single scene.
 * Specifically configured to load 'avatar_unset.json' from the 'avatarScenes' assets folder by default.
 */
public class AvatarSceneManager extends BaseSceneManager {
    private static final String AVATAR_SCENES_FOLDER = "avatarScenes";
    private static final String DEFAULT_TEST_SCENE = "avatar_unset.json";

    /**
     * Default constructor that loads 'avatar_unset.json' from 'avatarScenes'.
     */
    public AvatarSceneManager(Context context) {
        super(context, DEFAULT_TEST_SCENE);
    }

    public AvatarSceneManager(Context context, String sceneFileName) {
        super(context, sceneFileName);
    }

    public AvatarSceneManager(Context context, SpriteData spriteData) {
        super(context, spriteData);
    }

    public AvatarSceneManager(Context context, Scene scene) {
        super(context, scene);
    }

    @Override
    protected void loadScene() {
        if (preloadedScene != null) {
            TimberLog.d(TAG, "Using preloaded scene: " + preloadedScene.getSceneName());
            currentScene = preloadedScene;
        } else {
            try {
                TimberLog.d(TAG, "Loading scene '" + sceneFileName + "' from " + AVATAR_SCENES_FOLDER);
                SceneLoader loader = new SceneLoader(context);
                loader.setAssetsFolder(AVATAR_SCENES_FOLDER);
                
                // We intentionally don't set persistent path here for AvatarSceneManager
                // as it's specifically for loading these internal avatar scenes.
                
                currentScene = loader.loadScene(sceneFileName);

                // Verify scene was loaded successfully
                if (currentScene == null) {
                    TimberLog.e(TAG, "Scene loader returned null for: " + sceneFileName);
                    currentScene = new Scene("Error");
                }
            } catch (IOException e) {
                TimberLog.e(TAG, "IOException loading avatar scene '" + sceneFileName + "': " + e.getMessage(), e);
                currentScene = new Scene("Error");
            } catch (Exception e) {
                TimberLog.e(TAG, "Unexpected error loading avatar scene '" + sceneFileName + "': " + e.getMessage(), e);
                currentScene = new Scene("Error");
            }
        }

        // Verify currentScene is not null before initializing
        if (currentScene != null) {
            try {
                currentScene.initialize(context, textureManager);
                currentScene.setEdgeHighlighted(false);
                TimberLog.d(TAG, "Scene loaded and initialized successfully: " + currentScene.getSceneName());
            } catch (Exception e) {
                TimberLog.e(TAG, "Error initializing scene: " + e.getMessage(), e);
                // Keep the scene even if initialization fails
            }
        } else {
            TimberLog.e(TAG, "currentScene is null after loading attempt");
        }
    }

    /**
     * Called when the GL surface is created.
     */
    public void onSurfaceCreated() {
        try {
            TimberLog.d(TAG, "onSurfaceCreated called");
            initializeGLResources();
            initializeSceneResources();

            // Verify scene is valid after initialization
            if (currentScene == null) {
                TimberLog.e(TAG, "Scene is null after initialization in onSurfaceCreated");
                currentScene = new Scene("FallbackScene");
            }

            TimberLog.d(TAG, "onSurfaceCreated completed successfully");
        } catch (Exception e) {
            TimberLog.e(TAG, "Error in onSurfaceCreated: " + e.getMessage(), e);
            // Ensure we have a fallback scene
            if (currentScene == null) {
                currentScene = new Scene("FallbackScene");
            }
        }
    }

    /**
     * Called when the surface dimensions change.
     */
    public void onSurfaceChanged(int width, int height) {
        TimberLog.d(TAG, "onSurfaceChanged: " + width + "x" + height);

        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / (float) height;

        // Compute projection so that vertical span == WORLD_HEIGHT units
        float halfWorldH = WORLD_HEIGHT * 0.5f;
        float halfWorldW = halfWorldH * aspectRatio;

        // left, right, bottom, top using world-space extents
        Matrix.orthoM(projectionMatrix, 0, -halfWorldW, halfWorldW, halfWorldH, -halfWorldH, -1f, 1f);
    }

    /**
     * Called every frame to render the scene.
     */
    public void onDrawFrame() {
        try {
            if (currentScene == null) {
                TimberLog.w(TAG, "onDrawFrame called but currentScene is null");
                return;
            }

            if (textureManager != null) {
                textureManager.processPendingUploads();
            }

            // Apply xFocus offset when scroll motion is disabled
            if (!ConfigManager.isScrollMotionEnabled()) {
                scrollOffsetProcessor.setScrollTargetFromXFocus(currentScene.getXFocus());
            }

            performRenderFrame();
        } catch (Exception e) {
            TimberLog.e(TAG, "Error in onDrawFrame: " + e.getMessage(), e);
        }
    }

    /**
     * Release resources when the renderer is destroyed.
     */
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
    }

    /**
     * Handle renderer resume lifecycle.
     */
    public void onRendererResume(long resumeTimeNs) {
        resume();
    }

    /**
     * Handle renderer pause lifecycle.
     */
    public void onRendererPause() {
        pause();
    }

    /**
     * Handle scroll offset changes.
     */
    public void onScrollOffsetChanged(float offsetX) {
        if (ConfigManager.isScrollMotionEnabled()) {
            scrollOffsetProcessor.setScrollTarget(offsetX);
        }
    }
}
