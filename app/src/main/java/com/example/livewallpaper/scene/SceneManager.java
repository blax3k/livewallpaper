package com.example.livewallpaper.scene;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.livewallpaper.gl.GLWallpaperRenderer;
import com.example.livewallpaper.gl.Handles;
import com.example.livewallpaper.gl.PhoneGuideRenderer;
import com.example.livewallpaper.gl.ShaderProgram;
import com.example.livewallpaper.gl.SpriteRenderer;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.sensors.GyroSensorProcessor;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.sensors.ScrollOffsetProcessor;
import com.example.livewallpaper.ui.managers.SceneFileManager;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Unified renderer for both editing a specific scene (via GLSurfaceView.Renderer)
 * and rendering as a live wallpaper (via GLWallpaperRenderer).
 * Supports gyroscope-based motion, scene switching, and interactive scene preview.
 */
public class SceneManager implements GLSurfaceView.Renderer, GLWallpaperRenderer {
    private static final String TAG = "SceneManager";
    private static final float WORLD_HEIGHT = 10f;

    private final Context context;
    private final String sceneFileName;
    private final Scene preloadedScene;
    private Scene currentScene;
    private String spriteNameToDisplay;
    private ShaderProgram shaderProgram;
    private Handles handles;
    private SpriteRenderer spriteRenderer;
    private PhoneGuideRenderer phoneGuideRenderer;
    private TextureManager textureManager;
    private final GyroSensorProcessor gyroProcessor;
    private final ScrollOffsetProcessor scrollOffsetProcessor = new ScrollOffsetProcessor();
    private final float[] projectionMatrix = new float[16];
    private boolean spritesScaledForGyro = false;
    private volatile boolean shouldResortSprites = false;
    private Sprite selectedSprite = null;
    private String selectedSpriteName = null;
    private PhoneGuide phoneGuide = null;

    // Wallpaper-specific fields for scene switching and cycling
    private SceneSwitchManager sceneSwitchManager;
    private volatile boolean sceneSwitchRequested = false;
    private static final long SCENE_CYCLE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes in milliseconds
    private long lastSceneChangeTimeMs = System.currentTimeMillis();
    private boolean isWallpaperMode = false;

    public SceneManager(Context context, String sceneFileName) {
        this.context = context;
        this.sceneFileName = sceneFileName;
        this.preloadedScene = null;
        this.spriteNameToDisplay = null;
        this.gyroProcessor = new GyroSensorProcessor();
    }

    public SceneManager(Context context, String sceneFileName, String spriteName) {
        this.context = context;
        this.sceneFileName = sceneFileName;
        this.preloadedScene = null;
        this.spriteNameToDisplay = spriteName;
        this.gyroProcessor = new GyroSensorProcessor();
    }

    /**
     * Constructor for editing a sprite's texture with preloaded sprite data.
     * Creates a minimal scene containing only the provided sprite for texture editing.
     *
     * @param context the application context
     * @param spriteData the sprite data containing all sprite properties
     */
    public SceneManager(Context context, SpriteData spriteData) {
        this.context = context;
        this.sceneFileName = null;
        this.spriteNameToDisplay = spriteData.name;
        this.gyroProcessor = new GyroSensorProcessor();

        // Create a minimal scene with just the provided sprite
        Scene scene = new Scene("TextureEditScene");
        Sprite sprite = new Sprite(spriteData);
        scene.addSprite(sprite);
        this.preloadedScene = scene;
        this.currentScene = scene;

        // Set the sprite as selected so texture editing can access it
        this.selectedSprite = sprite;

        Log.d(TAG, "SceneManager created for texture editing with sprite: " + spriteData.name);
    }

    /**
     * Constructor for wallpaper mode that enables scene switching and cycling.
     * Loads the initial scene and sets up automatic scene cycling.
     *
     * @param context the application context
     */
    public SceneManager(Context context) {
        this.context = context;
        this.sceneFileName = null;
        this.preloadedScene = null;
        this.spriteNameToDisplay = null;
        this.gyroProcessor = new GyroSensorProcessor();
        this.isWallpaperMode = true;

        // Initialize scene manager for wallpaper mode
        SceneFileManager sceneFileManager = new SceneFileManager(context, null);
        this.sceneSwitchManager = new SceneSwitchManager(context, sceneFileManager);

        // Load the initial scene
        try {
            this.currentScene = sceneSwitchManager.loadInitialScene();
            Log.d(TAG, "Loaded initial scene: " + currentScene.getSceneName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load initial scene, using empty scene", e);
            this.currentScene = new Scene("DefaultScene");
        }

        // Initialize the scene manager with the loaded scene
        this.sceneSwitchManager.initialize(currentScene);
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

        // CRITICAL: Query GPU limits on GL thread BEFORE loading any textures
        // This ensures proper downscaling decisions for large textures
        textureManager.ensureGPULimitsQueried();

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

        // Create phone guide renderer (PhoneGuide will be initialized after scene is loaded)
        phoneGuideRenderer = new PhoneGuideRenderer(handles);
        Log.d(TAG, "PhoneGuideRenderer created");

        // Load the scene from the JSON file only if not already loaded
        // If the scene already exists, just reload textures and edge highlights
        if (currentScene == null) {
            loadScene();
        } else {
            Log.d(TAG, "Scene already loaded, reloading textures only");
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

        // Initialize PhoneGuide after scene is loaded
        phoneGuide = new PhoneGuide();
        if (currentScene != null) {
            // Calculate xOffset based on the scene's xFocus (0.0 to 1.0)
            // The phone guide's rectangle has width of about 4.76 units (9.99 * 9/21)
            // For a 1:1 viewport with world height of 10, the visible world is -5 to +5
            // We need to position the center line based on xFocus:
            // xFocus 0.0 (left) -> center line at -5
            // xFocus 0.5 (center) -> center line at 0
            // xFocus 1.0 (right) -> center line at +5
            float xFocus = currentScene.getXFocus();
            float guideWidth = 9.99f * (9f / 21f);  // width = height * aspect ratio
            float xOffset = -guideWidth/2f + (xFocus * guideWidth);
            phoneGuide.setXOffset(xOffset);

            // Also initialize the scroll offset processor with the same xFocus value
            // This ensures the scene renders with the correct scroll offset immediately
            // rather than starting at the default (0) and jumping when the slider is first moved
            updateScrollOffsetFromXFocus(xFocus);
            Log.d(TAG, "PhoneGuide created and positioned with xOffset: " + xOffset + " (xFocus: " + xFocus + ")");
        } else {
            Log.d(TAG, "PhoneGuide created with default position");
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

    /**
     * Called to draw the current frame in edit mode.
     * @param gl the GL interface. Use <code>instanceof</code> to
     * test if the interface supports GL11 or higher interfaces.
     */
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

        // Common rendering logic
        performRenderFrame();

        // Draw the phone guide (only in edit mode - unaffected by gyro motion)
        // Reset gyro offsets to zero to prevent phone guide from moving with gyro
        GLES20.glUniform1f(handles.gyroOffsetXHandle, 0.0f);
        GLES20.glUniform1f(handles.gyroOffsetYHandle, 0.0f);
        if (phoneGuideRenderer != null && phoneGuide != null) {
            phoneGuideRenderer.drawPhoneGuide(phoneGuide);
        }
    }

    /**
     * Common rendering logic shared between edit mode and wallpaper mode.
     * Handles projection setup, sprite rendering, and uniform updates.
     */
    private void performRenderFrame() {
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
     * Update the scene's scroll offset based on the xFocus value.
     * Called whenever the focus slider changes. The phone guide is no longer affected
     * and remains static and centered at all times.
     *
     * @param xFocus the new focus point value (0.0 to 1.0)
     */
    public void updatePhoneGuidePosition(float xFocus) {
        // Phone guide is now static and always centered, so we no longer update its position
        // Just update the scroll offset to affect the sprites
        updateScrollOffsetFromXFocus(xFocus);
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
        Log.d(TAG, "Scroll offset updated immediately from xFocus: " + xFocus +
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

            Log.d("SceneManager", "Updated sprite dimensions to: " + newWidth + " x " + newHeight);
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

    public void deleteSpriteFromScene(Sprite sprite) {
        if (currentScene == null || sprite == null) {
            Log.w(TAG, "Cannot delete sprite: scene or sprite is null");
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

        Log.d(TAG, "Deleted sprite: " + spriteName);
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

    // ===================== GLWallpaperRenderer Methods =====================

    /**
     * Called when the wallpaper surface is created. Sets up rendering for wallpaper mode.
     * Only applies when SceneManager is used as a wallpaper renderer.
     */
    @Override
    public void onSurfaceCreated() {
        if (!isWallpaperMode) {
            Log.w(TAG, "onSurfaceCreated() called but not in wallpaper mode");
            return;
        }

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
        if (sceneSwitchManager != null) {
            sceneSwitchManager.setGyroScalingCallback((newScene) -> {
                if (spritesScaledForGyro) {
                    gyroProcessor.applyGyroScalingToNewScene(newScene);
                }
            });
        }

        // CRITICAL: Query GPU limits on GL thread BEFORE loading any textures
        // This ensures proper downscaling decisions for large textures
        if (sceneSwitchManager != null) {
            sceneSwitchManager.getTextureManager().ensureGPULimitsQueried();
        }

        // Initialize the scene and load textures
        if (currentScene != null && sceneSwitchManager != null) {
            currentScene.initialize(context, sceneSwitchManager.getTextureManager());
        }

        // Reset the scene change timer
        lastSceneChangeTimeMs = System.currentTimeMillis();

        Log.d(TAG, "Surface created and scene initialized for wallpaper");
    }

    /**
     * Called when the wallpaper surface dimensions change.
     * Only applies when SceneManager is used as a wallpaper renderer.
     */
    @Override
    public void onSurfaceChanged(int width, int height) {
        if (!isWallpaperMode) {
            Log.w(TAG, "onSurfaceChanged() called but not in wallpaper mode");
            return;
        }

        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / (float) height;

        // Compute projection so that vertical span == WORLD_HEIGHT units
        float halfWorldH = WORLD_HEIGHT * 0.5f;
        float halfWorldW = halfWorldH * aspectRatio;

        // left, right, bottom, top using world-space extents
        Matrix.orthoM(projectionMatrix, 0, -halfWorldW, halfWorldW, halfWorldH, -halfWorldH, -1f, 1f);

        Log.d(TAG, "Surface changed for wallpaper: " + width + "x" + height);
    }

    /**
     * Called on every frame for wallpaper rendering. Handles scene switching and drawing.
     * Only applies when SceneManager is used as a wallpaper renderer.
     */
    @Override
    public void onDrawFrame() {
        if (!isWallpaperMode || currentScene == null) {
            Log.w(TAG, "onDrawFrame() called but not in wallpaper mode or scene is null");
            return;
        }

        // Process any pending async texture uploads that are ready (must be on GL thread)
        TextureManager textureManager = sceneSwitchManager != null ? sceneSwitchManager.getTextureManager() : null;
        if (textureManager != null) {
            textureManager.processPendingUploads();
        }

        // Check if scene switch was requested (from UI thread) and perform it here on GL thread
        if (sceneSwitchRequested && sceneSwitchManager != null) {
            sceneSwitchRequested = false;
            sceneSwitchManager.cycleToNextScene(currentScene);
            lastSceneChangeTimeMs = System.currentTimeMillis();
        }

        // Update scene transition (handles texture preload, crossfade, and scene switching)
        if (sceneSwitchManager != null) {
            currentScene = sceneSwitchManager.updateTransition();
        }

        // Apply xFocus offset when scroll motion is disabled
        if (!MotionConfig.isScrollMotionEnabled()) {
            // If we're in a transition, smoothly transition to the next scene's xFocus
            // Otherwise, use the current scene's xFocus
            Scene transitioningScene = sceneSwitchManager != null ? sceneSwitchManager.getTransitioningScene() : null;
            if (transitioningScene != null) {
                scrollOffsetProcessor.setScrollTargetFromXFocus(transitioningScene.getXFocus());
            } else {
                scrollOffsetProcessor.setScrollTargetFromXFocus(currentScene.getXFocus());
            }
        }

        // Common rendering logic
        performRenderFrame();
    }

    /**
     * Called when the wallpaper is destroyed. Cleans up resources.
     * Only applies when SceneManager is used as a wallpaper renderer.
     */
    @Override
    public void onDestroy() {
        if (!isWallpaperMode) {
            Log.w(TAG, "onDestroy() called but not in wallpaper mode");
            return;
        }

        // Destroy all sprites
        if (currentScene != null) {
            currentScene.destroy();
        }

        if (shaderProgram != null) {
            shaderProgram.delete();
        }

        TextureManager textureManager = sceneSwitchManager != null ? sceneSwitchManager.getTextureManager() : null;
        if (textureManager != null) {
            textureManager.destroyAll();
        }
        Log.d(TAG, "Renderer destroyed for wallpaper");
    }

    /**
     * Called when the scroll offset changes (wallpaper parallax scrolling).
     * Only applies when SceneManager is used as a wallpaper renderer.
     */
    @Override
    public void onScrollOffsetChanged(float offsetX) {
        if (!isWallpaperMode) {
            return;
        }

        // Only update scroll target if scroll motion is enabled
        if (MotionConfig.isScrollMotionEnabled()) {
            scrollOffsetProcessor.setScrollTarget(offsetX);
        }
        // When scroll motion is disabled, completely ignore scroll input
        // The xFocus value will be applied in onDrawFrame
    }

    /**
     * Called when the wallpaper renderer resumes.
     * Only applies when SceneManager is used as a wallpaper renderer.
     */
    @Override
    public void onRendererResume(long resumeTimeNs) {
        if (!isWallpaperMode) {
            return;
        }

        // Resume gyro tracking from current position
        gyroProcessor.resume();
        scrollOffsetProcessor.onRendererResume();
    }

    /**
     * Called when the wallpaper renderer pauses.
     * Only applies when SceneManager is used as a wallpaper renderer.
     */
    @Override
    public void onRendererPause() {
        if (!isWallpaperMode) {
            return;
        }

        // Pause gyro tracking (stop processing sensor data)
        gyroProcessor.pause();
        scrollOffsetProcessor.onRendererPause();
    }

    /**
     * Called when the wallpaper renderer is suspended.
     * Only applies when SceneManager is used as a wallpaper renderer.
     */
    @Override
    public void onRendererSuspend() {
        if (!isWallpaperMode) {
            return;
        }

        // Pause gyro tracking during suspension
        gyroProcessor.pause();
        scrollOffsetProcessor.onRendererPause();
        Log.d(TAG, "Renderer suspended - gyro tracking paused");
    }

    /**
     * Called when the wallpaper renderer resumes after suspension.
     * Handles automatic scene cycling based on elapsed time.
     * Only applies when SceneManager is used as a wallpaper renderer.
     */
    @Override
    public void onRendererSuspendResume() {
        if (!isWallpaperMode) {
            return;
        }

        // Resume gyro tracking after suspension
        gyroProcessor.resume();
        scrollOffsetProcessor.onRendererResume();

        // Check if it's time for automatic scene cycling (every 5 minutes)
        long currentTimeMs = System.currentTimeMillis();
        if (currentTimeMs - lastSceneChangeTimeMs >= SCENE_CYCLE_INTERVAL_MS && sceneSwitchManager != null) {
            sceneSwitchManager.cycleToNextScene(currentScene);
            lastSceneChangeTimeMs = currentTimeMs;
            Log.d(TAG, "Auto-cycling to next scene (5 minutes elapsed)");
        }

        Log.d(TAG, "Renderer resumed after suspension - gyro tracking resumed");
    }

    /**
     * Called when a double tap gesture is detected on the wallpaper.
     * Requests a scene switch on the GL thread.
     * Only applies when SceneManager is used as a wallpaper renderer.
     */
    @Override
    public void onDoubleTap(float x, float y) {
        if (!isWallpaperMode) {
            return;
        }

        Log.d(TAG, "Double tap received at screen coordinates (" + x + ", " + y + ")");
        // Request scene switch - will be performed on GL thread during next onDrawFrame
        sceneSwitchRequested = true;
    }

    /**
     * Refresh the available scene list from disk.
     * This should be called when the scene list has changed (e.g., after delete, reset, or add operations).
     * Only applies when SceneManager is used as a wallpaper renderer.
     *
     * @param sceneFileManager the SceneFileManager to reload scenes from
     */
    public void refreshSceneList(SceneFileManager sceneFileManager) {
        if (isWallpaperMode && sceneSwitchManager != null) {
            sceneSwitchManager.reloadAvailableScenes(sceneFileManager);
            Log.d(TAG, "Scene list refreshed in renderer");
        }
    }
}

