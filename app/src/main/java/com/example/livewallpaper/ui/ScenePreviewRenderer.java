package com.example.livewallpaper.ui;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.example.livewallpaper.gl.Handles;
import com.example.livewallpaper.gl.ShaderProgram;
import com.example.livewallpaper.gl.SpriteRenderer;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.scene.Scene;
import com.example.livewallpaper.scene.SceneLoader;
import com.example.livewallpaper.scene.Sprite;
import com.example.livewallpaper.sensors.GyroSensorProcessor;
import com.example.livewallpaper.sensors.MotionConfig;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Custom GL renderer for rendering a specific scene in the EditSceneActivity.
 * Supports gyroscope-based motion for interactive scene preview.
 */
public class ScenePreviewRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "ScenePreviewRenderer";
    private static final float WORLD_HEIGHT = 10f;

    private final Context context;
    private final String sceneFileName;
    private final Scene preloadedScene;
    private Scene currentScene;
    private String spriteNameToDisplay;
    private ShaderProgram shaderProgram;
    private Handles handles;
    private SpriteRenderer spriteRenderer;
    private TextureManager textureManager;
    private final GyroSensorProcessor gyroProcessor;
    private final float[] projectionMatrix = new float[16];
    private boolean spritesScaledForGyro = false;
    private volatile boolean shouldResortSprites = false;
    private Sprite selectedSprite = null;

    public ScenePreviewRenderer(Context context, String sceneFileName) {
        this.context = context;
        this.sceneFileName = sceneFileName;
        this.preloadedScene = null;
        this.spriteNameToDisplay = null;
        this.gyroProcessor = new GyroSensorProcessor();
    }

    public ScenePreviewRenderer(Context context, String sceneFileName, String spriteName) {
        this.context = context;
        this.sceneFileName = sceneFileName;
        this.preloadedScene = null;
        this.spriteNameToDisplay = spriteName;
        this.gyroProcessor = new GyroSensorProcessor();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated called");

        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Create texture manager
        textureManager = new TextureManager();
        Log.d(TAG, "TextureManager created");

        // Create shader program
        shaderProgram = new ShaderProgram(
            ShaderProgram.getVertexShaderCode(),
            ShaderProgram.getFragmentShaderCode()
        );
        shaderProgram.use();
        Log.d(TAG, "Shader program created and in use");

        int prog = shaderProgram.getProgram();
        handles = new Handles(prog);
        Log.d(TAG, "Handles created");

        // Create sprite renderer
        spriteRenderer = new SpriteRenderer(handles);
        Log.d(TAG, "SpriteRenderer created");

        // Load the scene from the JSON file only if not already loaded
        // If the scene already exists, just reload textures and edge highlights
        if (currentScene == null) {
            loadScene();
        } else {
            Log.d(TAG, "Scene already loaded, reloading textures only");
            currentScene.reloadTextures(context, textureManager);
            // Disable edge highlights after surface recreation and reset selected sprite
            for (Sprite sprite : currentScene.getSprites()) {
                sprite.setShowEdgeHighlight(false);
            }
            selectedSprite = null;
        }

        Log.d(TAG, "Surface created and scene loaded");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG, "onSurfaceChanged: " + width + "x" + height);

        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / (float) height;

        // Compute projection matrix
        float halfWorldH = WORLD_HEIGHT * 0.5f;
        float halfWorldW = halfWorldH * aspectRatio;

        Matrix.orthoM(projectionMatrix, 0, -halfWorldW, halfWorldW, halfWorldH, -halfWorldH, -1f, 1f);
        Log.d(TAG, "Projection matrix set");
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (currentScene == null) {
            Log.w(TAG, "Current scene is null, skipping draw");
            return;
        }

        // Process any pending texture uploads from async loading
        if (textureManager != null) {
            textureManager.processPendingUploads();
        }

        // Check if we need to re-sort sprites (from main thread request)
        if (shouldResortSprites) {
            currentScene.sortSpritesByParallax();
            shouldResortSprites = false;
            Log.d(TAG, "Sprites re-sorted on GL thread");
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        shaderProgram.use();

        // Set projection matrix
        GLES20.glUniformMatrix4fv(handles.projectionMatrixHandle, 1, false, projectionMatrix, 0);

        // Set scroll offset uniform (no scroll movement for preview)
        GLES20.glUniform1f(handles.scrollOffsetHandle, 0f);

        // Update gyro offsets and apply uniforms
        spritesScaledForGyro = gyroProcessor.updateAndApplyGyroUniforms(handles, currentScene, WORLD_HEIGHT, spritesScaledForGyro);

        // Draw all sprites in the scene
        // (If a specific sprite was requested, the scene was already filtered to contain only that sprite)
        for (Sprite sprite : currentScene.getSprites()) {
            spriteRenderer.drawSprite(sprite);
        }
    }

    private void loadScene() {
        try {
            // If a preloaded scene was provided, use it directly
            if (preloadedScene != null) {
                Log.d(TAG, "Using preloaded scene: " + preloadedScene.getSceneName());
                currentScene = preloadedScene;
            } else {
                // Otherwise load from file
                Log.d(TAG, "Loading scene from file: " + sceneFileName);
                SceneLoader sceneLoader = new SceneLoader(context);
                currentScene = sceneLoader.loadScene(sceneFileName);
            }

            // Initialize the scene with TextureManager
            if (currentScene != null) {
                Log.d(TAG, "Scene loaded, initializing with " + currentScene.getSprites().size() + " sprites");
                currentScene.initialize(context, textureManager);

                // If a specific sprite name is set, remove all other sprites and center it
                if (spriteNameToDisplay != null) {
                    currentScene.keepOnlySprite(spriteNameToDisplay);
                    // Center the sprite at (0, 0) when viewing it alone
                    if (!currentScene.getSprites().isEmpty()) {
                        Sprite sprite = currentScene.getSprites().get(0);
                        sprite.setPosition(0f, 0f);
                        // Enable edge highlight for the single sprite being edited
                        sprite.setShowEdgeHighlight(true);
                        selectedSprite = sprite;
                        Log.d(TAG, "Sprite positioned at (0, 0) for single sprite preview");
                    }
                } else {
                    // Disable edge highlights by default (they will be enabled when a sprite is selected)
                    for (Sprite sprite : currentScene.getSprites()) {
                        sprite.setShowEdgeHighlight(false);
                    }
                }

                Log.d(TAG, "Successfully loaded and initialized scene: " + currentScene.getSceneName());
            } else {
                Log.e(TAG, "Failed to load scene");
                currentScene = new Scene("Error");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading scene: " + e.getMessage(), e);
            // Create an empty scene on error
            currentScene = new Scene("Error");
        }
    }

    /**
     * Called when gyroscope data is received. Forward to gyro processor.
     */
    public void onGyroscopeChanged(float rotationX, float rotationY, float rotationZ) {
        if (MotionConfig.isGyroMotionEnabled()) {
            gyroProcessor.onGyroscopeChanged(rotationX, rotationY, rotationZ);
        }
    }

    /**
     * Get the currently loaded scene.
     */
    public Scene getCurrentScene() {
        return currentScene;
    }

    /**
     * Request that sprites be re-sorted on the next GL frame.
     * This should be called from the main thread when parallax multiplier changes.
     * The actual sort happens on the GL thread to avoid ConcurrentModificationException.
     */
    public void requestSpriteResort() {
        shouldResortSprites = true;
    }

    /**
     * Toggle edge highlights for all sprites in the current scene.
     * Useful for viewing sprite boundaries in the edit view.
     * This is safe to call from the main thread.
     *
     * @param showEdges true to show green outlines around sprites, false to hide them
     */
    public void toggleAllSpriteEdgeHighlights(boolean showEdges) {
        if (currentScene != null) {
            for (Sprite sprite : currentScene.getSprites()) {
                sprite.setShowEdgeHighlight(showEdges);
            }
        }
    }

    /**
     * Check if edge highlights are currently shown for any sprite.
     * Returns the state of the first sprite (if all are consistent).
     */
    public boolean areEdgeHighlightsShown() {
        if (currentScene != null && !currentScene.getSprites().isEmpty()) {
            return currentScene.getSprites().get(0).isShowEdgeHighlight();
        }
        return false;
    }

    /**
     * Set the selected sprite and highlight only that sprite.
     * All other sprites will have their edge highlights disabled.
     * This is safe to call from the main thread.
     *
     * @param sprite the sprite to select and highlight (can be null to deselect all)
     */
    public void setSelectedSprite(Sprite sprite) {
        if (currentScene != null) {
            // Disable highlights for all sprites
            for (Sprite s : currentScene.getSprites()) {
                s.setShowEdgeHighlight(false);
            }
            // Enable highlight for the selected sprite
            if (sprite != null) {
                sprite.setShowEdgeHighlight(true);
                selectedSprite = sprite;
            } else {
                selectedSprite = null;
            }
        }
    }

    /**
     * Resume gyro tracking when activity resumes.
     */
    public void resume() {
        gyroProcessor.resume();
    }

    /**
     * Pause gyro tracking when activity pauses.
     */
    public void pause() {
        gyroProcessor.pause();
    }
}
