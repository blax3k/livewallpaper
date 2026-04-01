package com.example.livewallpaper.scene.managers;

import android.content.Context;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import com.example.livewallpaper.logging.TimberLog;

import com.example.livewallpaper.gl.Handles;
import com.example.livewallpaper.gl.ShaderProgram;
import com.example.livewallpaper.gl.SpriteRenderer;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.SceneLoader;
import com.example.livewallpaper.scene.models.Sprite;
import com.example.livewallpaper.scene.models.SpriteData;
import com.example.livewallpaper.sensors.GyroSensorProcessor;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.sensors.ScrollOffsetProcessor;
import com.example.livewallpaper.managers.SceneFileManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for scene rendering in both edit and wallpaper modes.
 * Contains common rendering logic, scene management, and sprite editing functionality.
 */
public abstract class BaseSceneManager {
    protected static final String TAG = "BaseSceneManager";
    protected static final float WORLD_HEIGHT = 10f;

    protected final Context context;
    protected final String sceneFileName;
    protected final Scene preloadedScene;
    protected Scene currentScene;
    protected ShaderProgram shaderProgram;
    protected Handles handles;
    protected SpriteRenderer spriteRenderer;
    protected TextureManager textureManager;
    protected final GyroSensorProcessor gyroProcessor;
    protected final ScrollOffsetProcessor scrollOffsetProcessor = new ScrollOffsetProcessor();
    protected final float[] projectionMatrix = new float[16];
    protected boolean spritesScaledForGyro = false;
    protected volatile boolean shouldResortSprites = false;
    protected Sprite selectedSprite = null;
    protected String selectedSpriteName = null;

    public BaseSceneManager(Context context, String sceneFileName) {
        this.context = context;
        this.textureManager = new TextureManager();
        this.sceneFileName = sceneFileName;
        this.preloadedScene = null;
        this.gyroProcessor = new GyroSensorProcessor();
    }

    /**
     * Constructor for editing a sprite's texture with preloaded sprite data.
     * Creates a minimal scene containing only the provided sprite for texture editing.
     *
     * @param context the application context
     * @param spriteData the sprite data containing all sprite properties
     */
    protected BaseSceneManager(Context context, SpriteData spriteData) {
        this.context = context;
        this.textureManager = new TextureManager();
        this.sceneFileName = null;
        this.gyroProcessor = new GyroSensorProcessor();

        // Create a minimal scene with just the provided sprite
        Scene scene = new Scene("TextureEditScene");
        Sprite sprite = new Sprite(spriteData);
        scene.addSprite(sprite);
        this.preloadedScene = scene;
        this.currentScene = scene;

        // Set the sprite as selected so texture editing can access it
        this.selectedSprite = sprite;

        TimberLog.d(TAG, "SceneManager created for texture editing with sprite: " + spriteData.name);
    }

    /**
     * Constructor for previewing a scene with preloaded Scene data.
     * Used when passing the current scene from EditSceneActivity to FullscreenPreviewActivity.
     * The scene is loaded with all current edits, not reloaded from disk.
     *
     * @param context the application context
     * @param scene the preloaded Scene object with all current data
     */
    protected BaseSceneManager(Context context, Scene scene) {
        this.context = context;
        this.textureManager = new TextureManager();
        this.sceneFileName = null;
        this.preloadedScene = scene;
        this.currentScene = scene;
        this.gyroProcessor = new GyroSensorProcessor();

        TimberLog.d(TAG, "SceneManager created with preloaded scene: " + scene.getSceneName());
    }

    /**
     * Common GL resource initialization shared by both edit and wallpaper modes.
     * Sets up GL state, creates shader program, handles, and sprite renderer.
     * Subclasses should call this from their onSurfaceCreated methods.
     */
    protected void initializeGLResources() {
        // Set GL clear color and blending
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Create shader program
        shaderProgram = new ShaderProgram(ShaderProgram.getVertexShaderCode(), ShaderProgram.getFragmentShaderCode());
        shaderProgram.use();
        TimberLog.d(TAG, "Shader program created and in use");

        // Create handles from shader program
        int prog = shaderProgram.getProgram();
        handles = new Handles(prog);
        TimberLog.d(TAG, "Handles created");

        // Create sprite renderer
        spriteRenderer = new SpriteRenderer(handles);
        TimberLog.d(TAG, "SpriteRenderer created");

        // Ensure GPU limits are queried on GL thread BEFORE loading any textures
        textureManager.ensureGPULimitsQueried();
    }

    /**
     * Common scene initialization shared by both edit and wallpaper modes.
     * Handles scene loading, texture initialization, and sprite highlights.
     * Called from onSurfaceCreated after GL resources are initialized.
     */
    protected void initializeSceneResources() {
        // Load the scene if not already loaded
        if (currentScene == null) {
            loadScene();
        } else {
            TimberLog.d(TAG, "Scene already loaded, reloading textures only");
            currentScene.reloadTextures(context, textureManager);
            // Disable edge highlights after surface recreation and reset selected sprite
            // EXCEPT: don't reset if this is a preloaded scene for texture editing (selectedSprite was already set)
            for (Sprite sprite : currentScene.getSprites()) {
                sprite.setShowEdgeHighlight(false);
            }
            // Only clear selectedSprite if this is NOT a preloaded texture editing scene
            // (In texture editing mode, selectedSprite was set in the constructor and should be preserved)
            if (preloadedScene == null) {
                selectedSprite = null;
            } else {
                // For preloaded scenes, re-enable highlight on the selected sprite
                if (selectedSprite != null) {
                    selectedSprite.setShowEdgeHighlight(true);
                }
            }
        }

        // Initialize the scene with texture manager if available
        if (currentScene != null) {
            currentScene.initialize(context, textureManager);
        }
    }

    /**
     * Common rendering logic shared between edit mode and wallpaper mode.
     * Handles projection setup, sprite rendering, and uniform updates.
     */
    protected void performRenderFrame() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (shaderProgram != null) {
            shaderProgram.use();
        }

        // Set projection matrix
        GLES20.glUniformMatrix4fv(handles.projectionMatrixHandle, 1, false, projectionMatrix, 0);

        // Update scroll offset interpolation and get the current value for this frame
        float currentScrollOffset = scrollOffsetProcessor.updateAndGetCurrentOffset();

        // Set scroll offset uniform (applied by all sprites with their own multiplier)
        GLES20.glUniform1f(handles.scrollOffsetHandle, currentScrollOffset);

        // Update gyro offsets and apply uniforms
        spritesScaledForGyro = gyroProcessor.updateAndApplyGyroUniforms(handles, currentScene, spritesScaledForGyro);

        // Draw all sprites in the scene
        for (Sprite sprite : currentScene.getSprites()) {
            if (spriteRenderer != null) {
                spriteRenderer.drawSprite(sprite);
            }
        }
    }

    protected void loadScene() {
        try {
            // If a preloaded scene was provided, use it directly
            if (preloadedScene != null) {
                TimberLog.d(TAG, "Using preloaded scene: " + preloadedScene.getSceneName());
                currentScene = preloadedScene;
            } else {
                // Otherwise load from file
                TimberLog.d(TAG, "Loading scene from file: " + sceneFileName);
                SceneLoader sceneLoader = new SceneLoader(context);

                // Set the persistent scenes path so it loads from persistent storage first
                SceneFileManager sceneFileManager = new SceneFileManager(context, null);
                String persistentPath = sceneFileManager.getPersistentScenesDirectoryPath();
                sceneLoader.setPersistentScenesPath(persistentPath);
                TimberLog.d(TAG, "SceneLoader configured to load from persistent path: " + persistentPath);

                currentScene = sceneLoader.loadScene(sceneFileName);
            }

            // Initialize the scene with TextureManager
            if (currentScene == null) {
                TimberLog.e(TAG, "Failed to load scene");
                currentScene = new Scene("Error");
                return;
            }

            TimberLog.d(TAG, "Scene loaded, initializing with " + currentScene.getSprites().size() + " sprites");
            currentScene.initialize(context, textureManager);
            // Disable edge highlights by default (they will be enabled when a sprite is selected)
            currentScene.setEdgeHighlighted(false);

            TimberLog.d(TAG, "Successfully loaded and initialized scene: " + currentScene.getSceneName());
        } catch (Exception e) {
            TimberLog.e(TAG, "Error loading scene: " + e.getMessage(), e);
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
     * Resume gyro tracking and scroll offset interpolation when activity resumes.
     */
    public void resume() {
        gyroProcessor.resume();
        scrollOffsetProcessor.onRendererResume();
    }

    /**
     * Pause gyro tracking and scroll offset interpolation when activity pauses.
     */
    public void pause() {
        gyroProcessor.pause();
        scrollOffsetProcessor.onRendererPause();
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
     * Update the scroll offset processor based on the xFocus value.
     * This mimics the motion of the live wallpaper when the user changes the focus slider.
     * Uses the same SCROLL_SCALE constant as ScrollOffsetProcessor to ensure consistent behavior.
     *
     * @param xFocus the focus point value (0.0 = left, 0.5 = center, 1.0 = right)
     */
    public void updateScrollOffsetFromXFocus(float xFocus) {
        // Use the same calculation as ScrollOffsetProcessor.calculateScrollOffset()
        // This ensures EditSceneActivity matches the live wallpaper's scroll behavior
        float SCROLL_SCALE = 5.0f;  // Same constant used in ScrollOffsetProcessor
        float scrollOffset = (0.5f - xFocus) * SCROLL_SCALE;

        // Update the scroll offset processor with immediate value
        scrollOffsetProcessor.setScrollOffsetImmediate(scrollOffset);
        TimberLog.d(TAG, "Scroll offset updated immediately from xFocus: " + xFocus +
              " (scroll scale: " + SCROLL_SCALE + ", offset: " + scrollOffset + ")");
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
            // Use the new methods that update both current and original position
            // This ensures user edits are saved to JSON correctly
            sprite.setPositionXAndUpdateOriginal(x);
            sprite.setPositionYAndUpdateOriginal(y);
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
            // Update both current and original values so edits are saved to JSON
            sprite.setWidthAndUpdateOriginal(newWidth);
            sprite.setHeightAndUpdateOriginal(newWidth / aspectRatio);
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
            // Update both current and original values so edits are saved to JSON
            sprite.setHeightAndUpdateOriginal(newHeight);
            sprite.setWidthAndUpdateOriginal(newHeight * aspectRatio);
        }
    }

    /**
     * Update a sprite's dimensions (width and height).
     * Also updates the original dimensions so the changes are saved and persisted.
     *
     * @param sprite    the sprite to update
     * @param newWidth  the new width
     * @param newHeight the new height
     */
    public void updateSpriteDimensions(Sprite sprite, float newWidth, float newHeight) {
        if (sprite != null) {
            // Update both current and original dimensions
            // This ensures the dimensions are properly saved when the scene is persisted
            sprite.setWidthAndUpdateOriginal(newWidth);
            sprite.setHeightAndUpdateOriginal(newHeight);

            TimberLog.d("BaseSceneManager", "Updated sprite dimensions to: " + newWidth + " x " + newHeight);
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
            TimberLog.w(TAG, "Cannot add sprite: scene not loaded");
            return;
        }

        currentScene.addSprite(sprite);

        // Load the texture on the GL thread
        if (textureManager != null) {
            TimberLog.d(TAG, "Adding sprite to scene: " + sprite.getName());
            // Schedule texture loading on next GL frame
            new Handler(Looper.getMainLooper()).post(() -> {
                if (currentScene != null && textureManager != null) {
                    currentScene.loadSpriteTexture(context, textureManager, sprite);
                    TimberLog.d(TAG, "Texture loaded for sprite: " + sprite.getName());
                }
            });
        }
    }

    public void deleteSpriteFromScene(Sprite sprite) {
        if (currentScene == null || sprite == null) {
            TimberLog.w(TAG, "Cannot delete sprite: scene or sprite is null");
            return;
        }
        //TODO: we need to figure out a way to make sprite names unique
        String spriteName = sprite.getName();
        currentScene.getSprites().removeIf(s -> s.getName().equals(spriteName));

        // If the deleted sprite was selected, deselect it
        if (selectedSprite != null && selectedSprite.getName().equals(spriteName)) {
            selectedSprite = null;
            selectedSpriteName = null;
        }

        TimberLog.d(TAG, "Deleted sprite: " + spriteName);
    }

    protected void highlightSelectedSprite() {
        if (selectedSpriteName != null) for (Sprite sprite : currentScene.getSprites()) {
            sprite.setShowEdgeHighlight(false);
            if (sprite.getName().equals(selectedSpriteName)) {
                sprite.setShowEdgeHighlight(true);
                selectedSprite = sprite;
                TimberLog.d(TAG, "Restored highlight on GL thread for sprite: " + selectedSpriteName);
            }
        }
    }
}
