package com.example.livewallpaper.scene.managers;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.SceneLoader;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.SpriteData;
import com.example.livewallpaper.sensors.MotionConfig;

import java.io.IOException;

/**
 * Scene manager for displaying a single scene.
 * Specifically configured to load 'test.json' from the 'avatarScenes' assets folder by default.
 */
public class AvatarSceneManager extends BaseSceneManager {
    private static final String AVATAR_SCENES_FOLDER = "avatarScenes";
    private static final String DEFAULT_TEST_SCENE = "test.json";

    /**
     * Default constructor that loads 'test.json' from 'avatarScenes'.
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
            } catch (IOException e) {
                TimberLog.e(TAG, "Error loading avatar scene: " + e.getMessage(), e);
                currentScene = new Scene("Error");
            }
        }

        if (currentScene != null) {
            currentScene.initialize(context, textureManager);
            currentScene.setEdgeHighlighted(false);
        }
    }

    /**
     * Called when the GL surface is created.
     */
    public void onSurfaceCreated() {
        TimberLog.d(TAG, "onSurfaceCreated called");
        initializeGLResources();
        initializeSceneResources();
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
        if (currentScene == null) {
            return;
        }

        if (textureManager != null) {
            textureManager.processPendingUploads();
        }

        // Apply xFocus offset when scroll motion is disabled
        if (!MotionConfig.isScrollMotionEnabled()) {
            scrollOffsetProcessor.setScrollTargetFromXFocus(currentScene.getXFocus());
        }

        performRenderFrame();
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
        if (MotionConfig.isScrollMotionEnabled()) {
            scrollOffsetProcessor.setScrollTarget(offsetX);
        }
    }
}
