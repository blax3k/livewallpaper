package com.example.livewallpaper.scene;
import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.example.livewallpaper.gl.GLWallpaperRenderer;
import com.example.livewallpaper.gl.Handles;
import com.example.livewallpaper.gl.ShaderProgram;
import com.example.livewallpaper.gl.SpriteRenderer;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.sensors.GyroSensorProcessor;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.sensors.ScrollOffsetProcessor;
import com.example.livewallpaper.ui.managers.SceneFileManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/**
 * Represents a scene containing a collection of sprites.
 * Scenes can be switched to display different sets of sprites on screen.
 */
public class Scene {
    private static final String TAG = "Scene";
    private final String sceneName;
    private final List<Sprite> sprites;
    private boolean isInitialized = false;
    private float currentGyroScaleFactor = 1.0f;
    private boolean isGyroScaled = false;
    private float xFocus = 0.5f; // Default to center; represents the x-position to focus on (0.0 = left, 0.5 = center, 1.0 = right)
    public Scene(String sceneName) {
        this.sceneName = sceneName;
        this.sprites = new ArrayList<>();
    }
    /**
     * Get the name of this scene.
     */
    public String getSceneName() {
        return sceneName;
    }

    /**
     * Get the x-focus position for this scene (0.0 = left, 0.5 = center, 1.0 = right).
     * This is used to automatically set the scroll offset when scroll motion is disabled.
     */
    public float getXFocus() {
        return xFocus;
    }

    /**
     * Set the x-focus position for this scene.
     */
    public void setXFocus(float xFocus) {
        this.xFocus = xFocus;
    }

    /**
     * Get all sprites in this scene.
     */
    public List<Sprite> getSprites() {
        return sprites;
    }
    /**
     * Add a sprite to this scene.
     */
    public void addSprite(Sprite sprite) {
        sprites.add(sprite);
    }

    /**
     * Remove all sprites except the one with the specified name.
     * Useful for preview rendering when you only want to display a single sprite.
     *
     * @param spriteName the name of the sprite to keep
     */
    public void keepOnlySprite(String spriteName) {
        sprites.removeIf(sprite -> !sprite.getName().equals(spriteName));
        Log.d(TAG, "Filtered scene to keep only sprite: " + spriteName + ". Remaining sprites: " + sprites.size());
    }

    /**
     * Update wipe progress on all transitioning sprites in this scene.
     * @param progress the wipe progress (0.0 to 1.0)
     */
    public void updateWipeProgress(float progress) {
        for (Sprite sprite : sprites) {
            if (sprite.isTransitioning()) {
                sprite.setWipeProgress(progress);
            }
        }
    }

    /**
     * Sort sprites by parallax multiplier to ensure correct draw order.
     * Lower parallax values (further back) are drawn first.
     * At the same parallax level, draw order is:
     * 1. Wiping-out sprites - drawn first (behind)
     * 2. Wiping-in sprites - drawn second
     * 3. Non-transitioning sprites - drawn last (in front)
     */
    public void sortSpritesByParallax() {
        // Log before sorting
        Log.d(TAG, "=== BEFORE SORT ===");
        for (int i = 0; i < sprites.size(); i++) {
            Sprite s = sprites.get(i);
            Log.d(TAG, "  [" + i + "] " + s.getName() + " | parallax=" + s.getParallaxMultiplier() +
                  " | wipingOut=" + s.isWipingOut() + " | wipingIn=" + s.isWipingIn());
        }

        sprites.sort((a, b) -> {
            int parallaxCompare = Float.compare(a.getParallaxMultiplier(), b.getParallaxMultiplier());
            if (parallaxCompare != 0) {
                return parallaxCompare;
            }
            // Secondary sort at same parallax level:
            // 1. Wiping-out sprites (old) drawn first (behind)
            // 2. Wiping-in sprites (new) drawn second (in front)
            if (a.isWipingOut() && !b.isWipingOut()) return -1;  // a is wiping out, b is not
            if (!a.isWipingOut() && b.isWipingOut()) return 1;   // b is wiping out, a is not
            if (a.isWipingIn() && !b.isWipingIn()) return -1;    // a is wiping in, b is not
            if (!a.isWipingIn() && b.isWipingIn()) return 1;     // b is wiping in, a is not
            return 0;
        });

        // Log after sorting
        Log.d(TAG, "=== AFTER SORT ===");
        for (int i = 0; i < sprites.size(); i++) {
            Sprite s = sprites.get(i);
            Log.d(TAG, "  [" + i + "] " + s.getName() + " | parallax=" + s.getParallaxMultiplier() +
                  " | wipingOut=" + s.isWipingOut() + " | wipingIn=" + s.isWipingIn());
        }
    }
    /**
     * Initialize all sprites in this scene by loading their textures.
     * Should be called when the GL context is ready.
     * Will apply stored gyro scaling if it was set.
     *
     * @param context the Android context for loading resources
     * @param textureManager the texture manager for loading textures
     */
    public void initialize(Context context, TextureManager textureManager) {
        if (isInitialized) {
            Log.d(TAG, "Scene '" + sceneName + "' already initialized");
            return;
        }
        Log.d(TAG, "Initializing scene '" + sceneName + "' with " + sprites.size() + " sprites");

        // Load textures and apply gyro scaling in a single pass
        for (Sprite sprite : sprites) {
            loadSpriteTexture(context, textureManager, sprite);
            applyGyroScalingToSprite(sprite);
        }

        isInitialized = true;
        Log.d(TAG, "Scene '" + sceneName + "' initialized successfully");
    }

    /**
     * Reload textures for all sprites after surface recreation (e.g., pause/resume).
     * Preserves all sprite properties and doesn't reset initialization state.
     *
     * @param context the Android context for loading resources
     * @param textureManager the texture manager for loading textures
     */
    public void reloadTextures(Context context, TextureManager textureManager) {
        Log.d(TAG, "Reloading textures for scene '" + sceneName + "' with " + sprites.size() + " sprites");

        for (Sprite sprite : sprites) {
            loadSpriteTexture(context, textureManager, sprite);
        }

        Log.d(TAG, "Textures reloaded for scene '" + sceneName + "'");
    }

    /**
     * Load the texture for a sprite through the TextureManager asynchronously.
     * The texture will be applied to the sprite via callback when ready.
     * Public version for external calls.
     *
     * @param context the Android context for loading resources
     * @param textureManager the texture manager for loading textures
     * @param sprite the sprite to load texture for
     */
    public void loadSpriteTexture(Context context, TextureManager textureManager, Sprite sprite) {
        int resourceId = sprite.getTextureResourceId();

        textureManager.getTextureAsync(context, resourceId, (resId, texId) -> {
            sprite.setTextureId(texId);
            Log.d(TAG, "Sprite textureResourceId=" + resourceId + " loaded with texId=" + texId);
            if (texId == 0) {
                Log.w(TAG, "WARNING: Failed to load texture for resourceId=" + resourceId + ". This sprite may not render.");
            }
        });
    }

    /**
     * Apply stored gyro scaling to a sprite if gyro scaling is enabled.
     *
     * @param sprite the sprite to apply gyro scaling to
     */
    private void applyGyroScalingToSprite(Sprite sprite) {
        if (isGyroScaled && currentGyroScaleFactor > 1.0f && !sprite.isGyroScaled()) {
            // Scale the sprite size
            sprite.scaleFromOriginal(currentGyroScaleFactor);
            // Scale the position away from center (0, 0) to maintain relative spacing
            float currentX = sprite.getPositionX();
            float currentY = sprite.getPositionY();
            sprite.setPosition(currentX * currentGyroScaleFactor, currentY * currentGyroScaleFactor);
            Log.d(TAG, "Applied gyro scaling to sprite. Scale factor: " + currentGyroScaleFactor);
        }
    }

    /**
     * Set the gyro scaling state that will be applied during initialization.
     * Call this before initializing the scene to ensure sprites are created with proper scaling.
     *
     * @param scaleFactor the gyro scale factor to apply (1.0 = no scaling, >1.0 = enlarged)
     */
    public void setGyroScalingForInitialization(float scaleFactor) {
        if (scaleFactor > 1.0f) {
            this.currentGyroScaleFactor = scaleFactor;
            this.isGyroScaled = true;
            Log.d(TAG, "Scene '" + sceneName + "' marked to apply gyro scaling on initialization. Factor: " + scaleFactor);
        }
    }
    
    /**
     * Apply gyro scaling to all sprites in this scene.
     *
     * @param scaleFactor the scale factor to apply (>1.0 to enlarge)
     */
    public void updateGyroScaling(float scaleFactor) {
        for (Sprite sprite : sprites) {
            // Scale the sprite size
            sprite.scaleFromOriginal(scaleFactor);
            // Scale the position away from center (0, 0) to maintain relative spacing
            float currentX = sprite.getPositionX();
            float currentY = sprite.getPositionY();
            sprite.setPosition(currentX * scaleFactor, currentY * scaleFactor);
        }
        Log.d(TAG, "Scene '" + sceneName + "' scaled for gyro motion. Scale factor: " + scaleFactor);
    }
    /**
     * Reset all sprites in this scene to their original sizes and positions.
     */
    public void resetGyroScaling() {
        for (Sprite sprite : sprites) {
            // Reset size to original
            sprite.resetScale();
            // Reset position to original
            sprite.resetPosition();
        }
        this.isGyroScaled = false;
        this.currentGyroScaleFactor = 1.0f;
        Log.d(TAG, "Scene '" + sceneName + "' reset to original size and position");
    }
    /**
     * Destroy all sprites in this scene and release resources.
     */
    public void destroy() {
        for (Sprite sprite : sprites) {
            sprite.destroy();
        }
        sprites.clear();
        isInitialized = false;
        Log.d(TAG, "Scene '" + sceneName + "' destroyed");
    }

    /**
     * Get all unique texture resource IDs used by sprites in this scene.
     * Useful for determining which textures to keep/unload during scene switches.
     *
     * @return set of unique resource IDs
     */
    public Set<Integer> getUsedTextureResourceIds() {
        Set<Integer> usedIds = new HashSet<>();
        for (Sprite sprite : sprites) {
            usedIds.add(sprite.getTextureResourceId());
        }
        return usedIds;
    }

    /**
     * Simple example renderer that displays a blue square with a texture (knight.png) in the center of the view.
     */
    public static class SimpleRenderer implements GLWallpaperRenderer {
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
        private final SceneSwitchManager sceneManager;

        // Flag to request scene switch on GL thread (set from UI thread, consumed on GL thread)
        private volatile boolean sceneSwitchRequested = false;

        // Automatic scene cycling based on time
        private static final long SCENE_CYCLE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes in milliseconds
        private long lastSceneChangeTimeMs = System.currentTimeMillis();



        public SimpleRenderer(Context context) {
            this.context = context;
            SceneFileManager sceneFileManager = new SceneFileManager(context, null);
            this.sceneManager = new SceneSwitchManager(context, sceneFileManager);

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

        /**
         * Refresh the available scene list from disk.
         * This should be called when the scene list has changed (e.g., after delete, reset, or add operations).
         *
         * @param sceneFileManager the SceneFileManager to reload scenes from
         */
        public void refreshSceneList(SceneFileManager sceneFileManager) {
            if (sceneManager != null) {
                sceneManager.reloadAvailableScenes(sceneFileManager);
                Log.d(TAG, "Scene list refreshed in renderer");
            }
        }


    }
}
