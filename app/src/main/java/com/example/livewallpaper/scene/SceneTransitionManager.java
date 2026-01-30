package com.example.livewallpaper.scene;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.livewallpaper.gl.Handles;
import com.example.livewallpaper.gl.ShaderProgram;
import com.example.livewallpaper.gl.SpriteRenderer;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.sensors.GyroSensorProcessor;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.ui.managers.SceneFileManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Manages smooth scene transitions with diagonal wipe effects.
 * During a transition:
 * - Sprites common to both scenes remain fully visible
 * - Sprites only in the old scene wipe out (become transparent from top-left to bottom-right)
 * - Sprites only in the new scene wipe in (become visible from top-left to bottom-right)
 */
public class SceneTransitionManager {
    private static final String TAG = "SceneTransitionManager";
    private static final long FADE_DURATION_MS = 500;

    private enum TransitionState {
        IDLE,
        WAITING_FOR_TEXTURES,
        FADING
    }

    private TransitionState state = TransitionState.IDLE;
    private Scene oldScene;
    private Scene newScene;
    private long fadeStartTimeMs = 0;
    private TextureManager textureManager;
    private android.content.Context context;

    // Sprites added to oldScene during transition (need to be removed when done)
    private final List<Sprite> addedSprites = new ArrayList<>();

    public SceneTransitionManager(TextureManager textureManager) {
        this.textureManager = textureManager;
    }

    public boolean isTransitioning() {
        return state != TransitionState.IDLE;
    }

    /**
     * Start a transition from oldScene to newScene.
     * @param oldScene the currently active scene
     * @param newScene the scene to transition to
     * @param context Android context for texture loading
     */
    public void startTransition(Scene oldScene, Scene newScene, android.content.Context context) {
        if (isTransitioning()) {
            Log.w(TAG, "Transition already in progress, ignoring new transition request");
            return;
        }

        this.oldScene = oldScene;
        this.newScene = newScene;
        this.context = context;
        this.state = TransitionState.WAITING_FOR_TEXTURES;

        Log.d(TAG, "Started transition: " + oldScene.getSceneName() + " -> " + newScene.getSceneName());
    }

    /**
     * Update the transition. Call every frame.
     * Returns the scene to render.
     */
    public Scene updateTransition() {
        if (state == TransitionState.IDLE) {
            return null;
        }

        switch (state) {
            case WAITING_FOR_TEXTURES:
                // Initialize textures for the new scene on the GL thread
                if (textureManager != null && context != null) {
                    newScene.initialize(context, textureManager);
                    Log.d(TAG, "Textures initialized for new scene: " + newScene.getSceneName());
                }
                // Now that textures are ready, begin the fade
                beginFade();
                state = TransitionState.FADING;
                return oldScene;

            case FADING:
                oldScene.sortSpritesByParallax();
                float progress = calculateProgress();
                oldScene.updateWipeProgress(progress);

                Log.d(TAG, "=== FADING PROGRESS: " + String.format("%.2f", progress * 100) + "% ===");
                for (Sprite s : oldScene.getSprites()) {
                    Log.d(TAG, "  sprite: " + s.getName() + " | parallax=" + s.getParallaxMultiplier() +
                          " | wipeProgress=" + String.format("%.2f", s.getWipeProgress()) +
                          " | wipingOut=" + s.isWipingOut() + " | wipingIn=" + s.isWipingIn());
                }

                if (progress >= 1.0f) {
                    return finishTransition();
                }
                return oldScene;

            default:
                return oldScene;
        }
    }

    /**
     * Begin the fade by marking sprites and setting up the scene.
     */
    private void beginFade() {
        fadeStartTimeMs = System.currentTimeMillis();
        addedSprites.clear();

        // Mark sprites unique to old scene as wiping out
        for (Sprite oldSprite : oldScene.getSprites()) {
            if (!hasDuplicateIn(oldSprite, newScene.getSprites())) {
                oldSprite.setWipingOut(true);
                oldSprite.setWipeProgress(0.0f);
            }
        }

        // Mark and add sprites unique to new scene as wiping in
        for (Sprite newSprite : newScene.getSprites()) {
            if (!hasDuplicateIn(newSprite, oldScene.getSprites())) {
                newSprite.setWipingIn(true);
                newSprite.setWipeProgress(0.0f);
                oldScene.addSprite(newSprite);
                addedSprites.add(newSprite);
            }
        }

        // Sort to maintain proper draw order
        oldScene.sortSpritesByParallax();

        Log.d(TAG, "Beginning fade: added " + addedSprites.size() + " sprites from new scene");
    }

    private float calculateProgress() {
        long elapsed = System.currentTimeMillis() - fadeStartTimeMs;
        return Math.min(1.0f, (float) elapsed / FADE_DURATION_MS);
    }

    private Scene finishTransition() {
        // Reset wipe state on all sprites in the new scene
        for (Sprite sprite : newScene.getSprites()) {
            sprite.resetWipe();
        }

        // Remove the sprites we added to oldScene
        for (Sprite sprite : addedSprites) {
            oldScene.getSprites().remove(sprite);
        }
        addedSprites.clear();

        // Clean up textures from old scene that aren't used in new scene
        if (textureManager != null) {
            Set<Integer> oldSceneTextureIds = oldScene.getUsedTextureResourceIds();
            Set<Integer> newSceneTextureIds = newScene.getUsedTextureResourceIds();
            textureManager.unloadUnusedTextures(oldSceneTextureIds, newSceneTextureIds);
            Log.d(TAG, "Cleaned up unused textures from old scene");
        }

        // Sort new scene to maintain consistent draw order
        newScene.sortSpritesByParallax();

        state = TransitionState.IDLE;

        Log.d(TAG, "Transition finished");
        return newScene;
    }

    private boolean hasDuplicateIn(Sprite sprite, List<Sprite> sprites) {
        for (Sprite other : sprites) {
            if (areDuplicates(sprite, other)) {
                return true;
            }
        }
        return false;
    }

    private boolean areDuplicates(Sprite a, Sprite b) {
        return a.getTextureResourceId() == b.getTextureResourceId() &&
               Math.abs(a.getPositionX() - b.getPositionX()) < 0.001f &&
               Math.abs(a.getPositionY() - b.getPositionY()) < 0.001f &&
               a.getTextureId() == b.getTextureId();
    }

    public Scene getNewScene() {
        return newScene;
    }

    /**
     * Custom GL renderer for rendering a specific scene in the EditSceneActivity.
     * Supports gyroscope-based motion for interactive scene preview.
     */
    public static class ScenePreviewRenderer implements GLSurfaceView.Renderer {
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
        private String selectedSpriteName = null;

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
            shaderProgram = new ShaderProgram(ShaderProgram.getVertexShaderCode(), ShaderProgram.getFragmentShaderCode());
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

            highlightSelectedSprite();

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

                    // Set the persistent scenes path so it loads from persistent storage first
                    SceneFileManager sceneFileManager = new SceneFileManager(context, null);
                    String persistentPath = sceneFileManager.getPersistentScenesDirectoryPath();
                    sceneLoader.setPersistentScenesPath(persistentPath);
                    Log.d(TAG, "SceneLoader configured to load from persistent path: " + persistentPath);

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
                    selectedSpriteName = sprite.getName();
                } else {
                    selectedSprite = null;
                    selectedSpriteName = null;
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

        /**
         * Get a list of all sprites in the current scene.
         *
         * @return a list of sprites, or empty list if no scene is loaded
         */
        public List<Sprite> getAllSprites() {
            if (currentScene == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(currentScene.getSprites());
        }

        /**
         * Get the currently selected sprite for editing.
         *
         * @return the selected sprite, or null if none is selected
         */
        public Sprite getSelectedSprite() {
            return selectedSprite;
        }

        /**
         * Select a sprite by its index in the scene's sprite list.
         * Updates the visual highlight and returns the selected sprite.
         *
         * @param index the zero-based index of the sprite to select
         * @return the selected sprite, or null if index is invalid
         */
        public Sprite selectSpriteByIndex(int index) {
            if (currentScene == null) {
                return null;
            }

            List<Sprite> sprites = currentScene.getSprites();
            if (index >= 0 && index < sprites.size()) {
                Sprite sprite = sprites.get(index);
                setSelectedSprite(sprite);
                return sprite;
            }
            return null;
        }

        /**
         * Calculate the current aspect ratio for a sprite.
         * Returns the ratio of width to height.
         *
         * @param sprite the sprite to calculate the aspect ratio for
         * @return the aspect ratio (width / height), or 1.0f if height is 0
         */
        public float calculateAspectRatio(Sprite sprite) {
            if (sprite == null) {
                return 1.0f;
            }
            float height = sprite.getHeight();
            if (height == 0) {
                return 1.0f;
            }
            return sprite.getWidth() / height;
        }

        /**
         * Update a sprite's position.
         *
         * @param sprite the sprite to update
         * @param x      the new X position
         * @param y      the new Y position
         */
        public void updateSpritePosition(Sprite sprite, float x, float y) {
            if (sprite != null) {
                sprite.setPositionX(x);
                sprite.setPositionY(y);
            }
        }

        /**
         * Update a sprite's parallax multiplier.
         *
         * @param sprite             the sprite to update
         * @param parallaxMultiplier the new parallax multiplier
         */
        public void updateSpriteParallax(Sprite sprite, float parallaxMultiplier) {
            if (sprite != null) {
                sprite.setParallaxMultiplier(parallaxMultiplier);
            }
        }

        /**
         * Update a sprite's width while maintaining aspect ratio.
         *
         * @param sprite      the sprite to update
         * @param newWidth    the new width
         * @param aspectRatio the aspect ratio (width / height) to maintain
         */
        public void updateSpriteWidth(Sprite sprite, float newWidth, float aspectRatio) {
            if (sprite != null) {
                sprite.setWidth(newWidth);
                sprite.setHeight(newWidth / aspectRatio);
            }
        }

        /**
         * Update a sprite's height while maintaining aspect ratio.
         *
         * @param sprite      the sprite to update
         * @param newHeight   the new height
         * @param aspectRatio the aspect ratio (width / height) to maintain
         */
        public void updateSpriteHeight(Sprite sprite, float newHeight, float aspectRatio) {
            if (sprite != null) {
                sprite.setHeight(newHeight);
                sprite.setWidth(newHeight * aspectRatio);
            }
        }

        /**
         * Update a sprite's dimensions (width and height).
         *
         * @param sprite    the sprite to update
         * @param newWidth  the new width
         * @param newHeight the new height
         */
        public void updateSpriteDimensions(Sprite sprite, float newWidth, float newHeight) {
            if (sprite != null) {
                sprite.setWidth(newWidth);
                sprite.setHeight(newHeight);
            }
        }

        /**
         * Update a sprite's texture coordinates.
         *
         * @param sprite         the sprite to update
         * @param textureScale   the scale factor for the texture
         * @param textureOffsetU the U-axis offset
         * @param textureOffsetV the V-axis offset
         */
        public void updateSpriteTexture(Sprite sprite, float textureScale, float textureOffsetU, float textureOffsetV) {
            if (sprite != null) {
                TextureEditState textureState = new TextureEditState(textureScale, textureOffsetU, textureOffsetV);
                sprite.updateTextureCoordinates(textureState);
            }
        }

        /**
         * Get the TextureManager instance for loading textures.
         * This allows external classes to load textures on demand.
         *
         * @return the TextureManager, or null if not yet initialized
         */
        public TextureManager getTextureManager() {
            return textureManager;
        }

        /**
         * Add a new sprite to the current scene and load its texture.
         * This should be called from the main thread.
         *
         * @param sprite the sprite to add to the scene
         */
        public void addSpriteToScene(Sprite sprite) {
            if (currentScene == null) {
                Log.w(TAG, "Cannot add sprite: scene not loaded");
                return;
            }

            currentScene.addSprite(sprite);

            // Load the texture on the GL thread
            if (textureManager != null) {
                Log.d(TAG, "Adding sprite to scene: " + sprite.getName());
                // Schedule texture loading on next GL frame
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (currentScene != null && textureManager != null) {
                        currentScene.loadSpriteTexture(context, textureManager, sprite);
                        Log.d(TAG, "Texture loaded for sprite: " + sprite.getName());
                    }
                });
            }
        }

        private void highlightSelectedSprite() {
            if (selectedSpriteName != null) for (Sprite sprite : currentScene.getSprites()) {
                sprite.setShowEdgeHighlight(false);
                if (sprite.getName().equals(selectedSpriteName)) {
                    sprite.setShowEdgeHighlight(true);
                    selectedSprite = sprite;
                    Log.d(TAG, "Restored highlight on GL thread for sprite: " + selectedSpriteName);
                }
            }
        }

    }
}
