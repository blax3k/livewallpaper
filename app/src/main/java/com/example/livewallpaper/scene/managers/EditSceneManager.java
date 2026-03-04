package com.example.livewallpaper.scene.managers;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.example.livewallpaper.gl.Handles;
import com.example.livewallpaper.gl.PhoneGuideRenderer;
import com.example.livewallpaper.gl.ShaderProgram;
import com.example.livewallpaper.gl.SpriteRenderer;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.scene.models.PhoneGuide;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.Sprite;
import com.example.livewallpaper.scene.models.SpriteData;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Scene manager for edit mode using GLSurfaceView.Renderer.
 * Handles rendering in edit/preview activities with phone guide overlay and sprite editing.
 */
public class EditSceneManager extends BaseSceneManager implements GLSurfaceView.Renderer {
    private PhoneGuideRenderer phoneGuideRenderer;
    private PhoneGuide phoneGuide = null;
    private String spriteNameToDisplay;

    public EditSceneManager(Context context, String sceneFileName) {
        super(context, sceneFileName);
        this.spriteNameToDisplay = null;
    }

    /**
     * Constructor for editing a sprite's texture with preloaded sprite data.
     */
    public EditSceneManager(Context context, SpriteData spriteData) {
        super(context, spriteData);
        this.spriteNameToDisplay = spriteData.name;
    }

    /**
     * Constructor for previewing a scene with preloaded Scene data.
     */
    public EditSceneManager(Context context, Scene scene) {
        super(context, scene);
        this.spriteNameToDisplay = null;
    }

    /**
     * Override loadScene to handle edit-specific sprite filtering.
     * If a specific sprite name is set, keeps only that sprite and centers it.
     */
    @Override
    protected void loadScene() {
        // Call parent implementation first
        super.loadScene();

        // Apply edit-specific filtering if a sprite name is specified
        if (spriteNameToDisplay != null && currentScene != null) {
            currentScene.keepOnlySprite(spriteNameToDisplay);
            // Center the sprite at (0, 0) when viewing it alone
            if (!currentScene.getSprites().isEmpty()) {
                Sprite sprite = currentScene.getSprites().get(0);
                sprite.setPosition(0f, 0f);
                // Enable edge highlight for the single sprite being edited
                sprite.setShowEdgeHighlight(true);
                selectedSprite = sprite;
                Log.d(TAG, "Sprite positioned at (0, 0) for single sprite preview: " + spriteNameToDisplay);
            }
        }
    }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated called");

        // Create texture manager
        textureManager = new TextureManager();
        Log.d(TAG, "TextureManager created");

        // Initialize common GL resources (shader, handles, sprite renderer)
        initializeGLResources();

        // Create phone guide renderer (PhoneGuide will be initialized after scene is loaded)
        phoneGuideRenderer = new PhoneGuideRenderer(handles);
        Log.d(TAG, "PhoneGuideRenderer created");

        // Initialize common scene resources (load scene, reload textures, etc.)
        initializeSceneResources();

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
}

