package com.example.livewallpaper.scene.managers;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import com.example.livewallpaper.logging.TimberLog;

import com.example.livewallpaper.gl.Handles;
import com.example.livewallpaper.gl.PhoneGuideRenderer;
import com.example.livewallpaper.gl.ShaderProgram;
import com.example.livewallpaper.gl.SpriteRenderer;
import com.example.livewallpaper.gl.TextureManager;
import com.example.livewallpaper.logging.TimberLog;
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
                TimberLog.d(TAG, "Sprite positioned at (0, 0) for single sprite preview: " + spriteNameToDisplay);
            }
        }
    }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        TimberLog.d(TAG, "onSurfaceCreated called");

        // Create texture manager
        textureManager = new TextureManager();
        TimberLog.d(TAG, "TextureManager created");

        // Initialize common GL resources (shader, handles, sprite renderer)
        initializeGLResources();

        // Create phone guide renderer (PhoneGuide will be initialized after scene is loaded)
        phoneGuideRenderer = new PhoneGuideRenderer(handles);
        TimberLog.d(TAG, "PhoneGuideRenderer created");

        // Initialize common scene resources (load scene, reload textures, etc.)
        initializeSceneResources();

        // Initialize PhoneGuide after scene is loaded
        phoneGuide = new PhoneGuide();
        if (currentScene != null) {
            float xFocus = currentScene.getXFocus();
            float yFocus = currentScene.getYFocus();

            float guideWidth = 9.99f * (9f / 21f);
            float xOffset = -guideWidth/2f + (xFocus * guideWidth);
            phoneGuide.setXOffset(xOffset);

            // Initialise both focus processors so the scene renders at the correct position
            // immediately rather than snapping on the first slider interaction.
            updateScrollOffsetFromXFocus(xFocus);
            updateScrollOffsetFromYFocus(yFocus);
            TimberLog.d(TAG, "PhoneGuide positioned with xOffset: " + xOffset
                    + " (xFocus: " + xFocus + ", yFocus: " + yFocus + ")");
        } else {
            TimberLog.d(TAG, "PhoneGuide created with default position");
        }

        highlightSelectedSprite();

        TimberLog.d(TAG, "Surface created and scene loaded");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        TimberLog.d(TAG, "onSurfaceChanged: " + width + "x" + height);

        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / (float) height;

        isLandscape = width > height;

        float halfWorldW, halfWorldH;
        if (isLandscape) {
            halfWorldW = WORLD_HEIGHT * 0.5f;
            halfWorldH = halfWorldW / aspectRatio;
        } else {
            halfWorldH = WORLD_HEIGHT * 0.5f;
            halfWorldW = halfWorldH * aspectRatio;
        }

        Matrix.orthoM(projectionMatrix, 0, -halfWorldW, halfWorldW, halfWorldH, -halfWorldH, -1f, 1f);
        TimberLog.d(TAG, "Projection matrix set (landscape=" + isLandscape + ")");
    }

    /**
     * Called to draw the current frame in edit mode.
     * @param gl the GL interface. Use <code>instanceof</code> to
     * test if the interface supports GL11 or higher interfaces.
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        if (currentScene == null) {
            TimberLog.w(TAG, "Current scene is null, skipping draw");
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
            TimberLog.d(TAG, "Sprites re-sorted on GL thread");
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
     * Update the scene's horizontal scroll offset based on the xFocus value.
     * Called whenever the X-focus slider changes.
     *
     * @param xFocus the new focus point value (0.0 to 1.0)
     */
    public void updatePhoneGuidePosition(float xFocus) {
        updateScrollOffsetFromXFocus(xFocus);
    }

    /**
     * Update the scene's vertical scroll offset based on the yFocus value.
     * Called whenever the Y-focus slider changes (landscape mode).
     *
     * @param yFocus the new focus point value (0.0 = top, 0.5 = center, 1.0 = bottom)
     */
    public void updateYFocusPosition(float yFocus) {
        updateScrollOffsetFromYFocus(yFocus);
    }
}

