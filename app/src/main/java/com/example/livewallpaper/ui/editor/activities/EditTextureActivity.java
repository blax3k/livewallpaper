package com.example.livewallpaper.ui.editor.activities;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.managers.EditSceneManager;
import com.example.livewallpaper.scene.models.Sprite;
import com.example.livewallpaper.scene.models.SpriteData;
import com.example.livewallpaper.scene.TextureCoordinateCalculator;
import com.example.livewallpaper.scene.TextureEditState;
import com.example.livewallpaper.ui.editor.views.SquareGLSurfaceView;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.ui.editor.controllers.DrawableImagePickerDialog;
import com.example.livewallpaper.ui.editor.controllers.TextureSliderController;
import com.example.livewallpaper.ui.editor.utils.ImageDimensionsUtils;

public class EditTextureActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "EditTextureActivity";
    public static final String EXTRA_SPRITE_DATA = "sprite_data";

    // Result data keys for passing sprite changes back to EditSceneActivity
    public static final String RESULT_SPRITE_NAME = "result_sprite_name";
    public static final String RESULT_WIDTH = "result_width";
    public static final String RESULT_HEIGHT = "result_height";
    public static final String RESULT_TEXTURE_COORDINATES = "result_texture_coordinates";
    public static final String RESULT_TEXTURE_RESOURCE = "result_texture_resource";
    public static final String RESULT_TEXTURE_RESOURCE_ID = "result_texture_resource_id";

    private SquareGLSurfaceView glSurfaceView;
    private EditSceneManager renderer;
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private SpriteData spriteData;
    private boolean glSetupComplete = false;
    private TextureSliderController textureSliderController;
    private TextureEditState textureEditState;
    private float lastTouchX = 0;
    private float lastTouchY = 0;
    private boolean isTouching = false;
    private boolean hasUnsavedChanges = false;
    private boolean wasGyroMotionEnabled = true;  // Track original gyro state to restore on exit

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_texture);

        // Disable gyro motion for texture editing to work with unscaled sprite dimensions
        wasGyroMotionEnabled = MotionConfig.isGyroMotionEnabled();
        MotionConfig.setGyroMotionEnabled(false);
        Log.d(TAG, "Gyro motion disabled for texture editing (was: " + wasGyroMotionEnabled + ")");

        // Set up back button
        ImageButton backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> onBackButtonPressed());
        }

        // Handle system back gesture using OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackButtonPressed();
            }
        });

        // Get sprite data from intent
        spriteData = getIntent().getParcelableExtra(EXTRA_SPRITE_DATA);

        if (spriteData == null) {
            Log.e(TAG, "Missing sprite data!");
            Toast.makeText(this, "Error: Missing sprite data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Sprite data received: " + spriteData.name +
              ", Width: " + spriteData.width + ", Height: " + spriteData.height);

        // Initialize sensor manager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }

        // Set up GL surface view after view is laid out
        glSurfaceView = findViewById(R.id.sprite_preview_gl_view);
        if (glSurfaceView != null) {
            // Set EGLContext version immediately (must be done before setRenderer)
            glSurfaceView.setEGLContextClientVersion(2);

            // Wait for layout to be complete before setting renderer
            glSurfaceView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                if (glSurfaceView.getWidth() > 0 && glSurfaceView.getHeight() > 0 && !glSetupComplete) {
                    setupGLSurfaceView();
                }
            });
        }
    }

    private void setupGLSurfaceView() {
        if (glSurfaceView == null || glSetupComplete) {
            return;
        }

        try {
            Log.d(TAG, "Setting up GLSurfaceView renderer with sprite: " + spriteData.name);

            // Create renderer with the sprite data (creates a minimal scene with just this sprite)
            renderer = new EditSceneManager(this, spriteData);
            glSurfaceView.setRenderer(renderer);

            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

            // Set up touch listener for texture coordinate manipulation
            glSurfaceView.setOnTouchListener((v, event) -> {
                v.performClick();
                return handleGLViewTouch(v, event);
            });

            glSetupComplete = true;

            // Initialize sliders after a short delay to ensure scene is loaded
            new Handler(Looper.getMainLooper()).postDelayed(this::initializeSliders, 500);

            Log.d(TAG, "GLSurfaceView configured successfully for sprite: " + spriteData.name);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up GLSurfaceView: " + e.getMessage(), e);
            Toast.makeText(this, "Error setting up preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeSliders() {
        SeekBar widthSlider = findViewById(R.id.width_slider);
        SeekBar heightSlider = findViewById(R.id.height_slider);
        SeekBar textureScaleSlider = findViewById(R.id.texture_scale_slider);
        TextView widthValueText = findViewById(R.id.width_value);
        TextView heightValueText = findViewById(R.id.height_value);
        TextView textureScaleValueText = findViewById(R.id.texture_scale_value);
        Button setTextureButton = findViewById(R.id.set_texture_button);

        // Initialize the texture slider controller
        textureSliderController = new TextureSliderController(widthSlider, heightSlider, textureScaleSlider,
                widthValueText, heightValueText, textureScaleValueText);

        // Set callback for slider changes to update sprite and texture coordinates
        textureSliderController.setOnChangeCallback(this::handleSliderChange);

        // Set up the Set Texture button
        if (setTextureButton != null) {
            setTextureButton.setOnClickListener(v -> showSetTextureDialog());
        }

        if (renderer != null) {
            Scene scene = renderer.getCurrentScene();
            if (scene != null && !scene.getSprites().isEmpty()) {
                Sprite sprite = scene.getSprites().get(0);

                if (sprite != null) {
                    // Set sprite dimensions from the sprite data
                    sprite.setWidth(spriteData.width);
                    sprite.setHeight(spriteData.height);

                    // Position the sprite at (0, 0) for texture editing to center it on screen
                    sprite.setPositionAtZero(true);

                    // Set the texture editing baseline to the sprite data dimensions
                    sprite.setTextureEditingBaseline(spriteData.width, spriteData.height);

                    // Load the texture coordinates from spriteData (if available)
                    // These contain the persisted texture state
                    float[] texCoordsToLoad = spriteData.texCoordinates;
                    if (texCoordsToLoad == null || texCoordsToLoad.length != 8) {
                        // Use default if not available
                        texCoordsToLoad = new float[]{
                                0.0f, 1.0f,  // top left
                                0.0f, 0.0f,  // bottom left
                                1.0f, 1.0f,  // top right
                                1.0f, 0.0f   // bottom right
                        };
                    }
                    sprite.setTextureCoordinates(texCoordsToLoad);

                    // Initialize textureEditState from the loaded coordinates
                    // Extract scale and offset from the texture coordinate range
                    float scale = TextureCoordinateCalculator.extractScaleFromCoordinates(
                            texCoordsToLoad,
                            spriteData.width,
                            spriteData.height,
                            sprite.getOriginalTextureCoordinates()
                    );
                    float offsetU = TextureCoordinateCalculator.extractOffsetUFromCoordinates(texCoordsToLoad);
                    float offsetV = TextureCoordinateCalculator.extractOffsetVFromCoordinates(texCoordsToLoad);

                    textureEditState = new TextureEditState(scale, offsetU, offsetV);

                    Log.d(TAG, "Applied sprite data - width: " + spriteData.width +
                          ", height: " + spriteData.height +
                          ", loaded texture state - scale: " + scale +
                          ", offsetU: " + offsetU + ", offsetV: " + offsetV);

                    // Set up the sliders with the sprite and texture state
                    textureSliderController.setup(sprite, textureEditState);
                }
            }
        }
    }

    /**
     * Extract the texture scale from texture coordinates.
     * Scale is determined by the size of the UV window (e.g., 0.25-0.75 = 2x zoom).
     */

    /**
     * Show the image picker dialog to select a new texture for the sprite.
     */
    private void showSetTextureDialog() {
        DrawableImagePickerDialog.showImagePickerDialog(this, (imageName, resourceId) -> {
            setNewTexture(imageName, resourceId);
        });
    }

    /**
     * Set a new texture for the sprite without changing sprite dimensions.
     * Texture is displayed at full scale with no offset, centered within the sprite bounds.
     * Aspect ratio fitting is handled by TextureCoordinateCalculator.
     * Loads the texture to GPU before applying it.
     *
     * @param imageName the name of the drawable resource
     * @param resourceId the resource ID of the drawable
     */
    private void setNewTexture(String imageName, int resourceId) {
        try {
            Sprite currentSprite = renderer != null ? renderer.getSelectedSprite() : null;
            if (currentSprite == null) {
                Toast.makeText(this, "Error: No sprite to texture", Toast.LENGTH_SHORT).show();
                return;
            }

            if (renderer == null) {
                Toast.makeText(this, "Error: Renderer not available", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get the dimensions of the new texture image (for logging purposes)
            ImageDimensionsUtils.ImageDimensions imageDims = ImageDimensionsUtils.getImageDimensions(this, resourceId);
            if (imageDims == null) {
                Toast.makeText(this, "Error: Could not load image dimensions", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "New texture dimensions: " + imageDims);

            // Load the texture to GPU asynchronously
            renderer.getTextureManager().getTextureAsync(this, resourceId, (resId, textureId) -> {
                if (textureId == 0) {
                    Log.e(TAG, "Failed to load texture for resourceId=" + resourceId);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error: Failed to load texture", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                Log.d(TAG, "Texture loaded to GPU: resourceId=" + resourceId + ", textureId=" + textureId);

                Sprite sprite = renderer.getSelectedSprite();
                if (sprite == null) {
                    Log.e(TAG, "Sprite is no longer available after texture load");
                    return;
                }

                // Update the sprite with the new texture resource name and ID
                sprite.setTextureResource(imageName);
                sprite.setTextureResourceId(resourceId);
                sprite.setTextureId(textureId);

                // Keep sprite dimensions unchanged
                float spriteWidth = sprite.getWidth();
                float spriteHeight = sprite.getHeight();
                Log.d(TAG, "Sprite dimensions (unchanged): " + spriteWidth + "x" + spriteHeight);

                // Reset texture coordinates to default: full texture, centered, no offset
                // TextureCoordinateCalculator will handle aspect ratio fitting
                TextureEditState newTextureState = new TextureEditState(1.0f, 0.0f, 0.0f);
                sprite.updateTextureCoordinates(newTextureState);
                textureEditState = newTextureState;

                Log.d(TAG, "Texture coordinates reset to: scale=1.0, offsetU=0.0, offsetV=0.0");
                Log.d(TAG, "Updated sprite texture resource: " + imageName + " (resourceId=" + resourceId + ")");

                // Update the sliders to reflect new texture state on the UI thread
                runOnUiThread(() -> {
                    if (textureSliderController != null) {
                        if (renderer.getSelectedSprite() != null) {
                            textureSliderController.setup(renderer.getSelectedSprite(), textureEditState);
                        }
                    }

                    // Mark as having unsaved changes
                    hasUnsavedChanges = true;

                    Toast.makeText(this, "Texture '" + imageName + "' set successfully", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "New texture set: " + imageName + " with resourceId=" + resourceId);
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting new texture: " + e.getMessage(), e);
            Toast.makeText(this, "Error setting texture: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Callback when any slider value changes.
     * Updates sprite texture coordinates and marks as having unsaved changes.
     */
    private void handleSliderChange() {
        updateSpriteTextureCoordinates();
        hasUnsavedChanges = true;
    }

    /**
     * Update sprite texture coordinates based on current TextureEditState.
     * This applies the texture scale and offsets to the sprite's texture coordinate buffer.
     */
    private void updateSpriteTextureCoordinates() {
        Sprite currentSprite = renderer != null ? renderer.getSelectedSprite() : null;
        if (currentSprite != null && textureEditState != null) {
            currentSprite.updateTextureCoordinates(textureEditState);
            Log.d(TAG, "Sprite texture coordinates updated");
        }
    }


    /**
     * Handle touch input on the GLView to manipulate texture coordinates.
     * Dragging moves the texture in the corresponding direction.
     */
    private boolean handleGLViewTouch(View v, MotionEvent event) {
        Sprite currentSprite = renderer != null ? renderer.getSelectedSprite() : null;
        Log.d(TAG, "handleGLViewTouch called - action: " + event.getAction() + ", currentSprite: " + (currentSprite != null ? currentSprite.getName() : "null"));

        if (currentSprite == null) {
            Log.w(TAG, "currentSprite is null in touch handler");
            return false;
        }

        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Start tracking touch
                isTouching = true;
                lastTouchX = touchX;
                lastTouchY = touchY;
                Log.d(TAG, "Touch DOWN at (" + touchX + ", " + touchY + ")");
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isTouching) {
                    // Calculate the delta movement
                    float deltaX = touchX - lastTouchX;
                    float deltaY = touchY - lastTouchY;

                    // Normalize delta to texture coordinate space
                    // The GLView width/height correspond to the sprite dimensions
                    float viewWidth = v.getWidth();
                    float viewHeight = v.getHeight();

                    // Convert pixel movement to texture coordinate offset
                    // Positive deltaX means dragging right, which should move texture right (increase U)
                    // Positive deltaY means dragging down, which should move texture down (decrease V, since V is inverted in textures)
                    float uOffset = -(deltaX / viewWidth);  // Negate to reverse x-axis motion
                    float vOffset = -(deltaY / viewHeight); // Inverted for texture coordinates

                    Log.d(TAG, "Touch MOVE - deltaX: " + deltaX + ", deltaY: " + deltaY + ", uOffset: " + uOffset + ", vOffset: " + vOffset + ", viewSize: " + viewWidth + "x" + viewHeight);

                    // Apply the offset to the texture edit state with clamping
                    if (textureEditState != null) {
                        textureEditState.offsetTextureCoordinates(uOffset, vOffset, currentSprite.getWidth(), currentSprite.getHeight(),
                                currentSprite.getTextureEditingBaselineWidth(), currentSprite.getTextureEditingBaselineHeight(), 1.0f,
                                currentSprite.getTextureCoordinates());
                        updateSpriteTextureCoordinates();
                        hasUnsavedChanges = true;
                    }

                    // Update last position
                    lastTouchX = touchX;
                    lastTouchY = touchY;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // End tracking, texture coordinates remain at their new position
                isTouching = false;
                Log.d(TAG, "Touch released - texture coordinates locked");
                return true;
        }

        return false;
    }


    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && renderer != null) {
            renderer.onGyroscopeChanged(event.values[0], event.values[1], event.values[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No action needed
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) {
            try {
                glSurfaceView.onPause();
            } catch (Exception e) {
                Log.w(TAG, "Error pausing GLSurfaceView: " + e.getMessage());
            }
        }
        if (renderer != null) {
            renderer.pause();
        }
        if (sensorManager != null && gyroscopeSensor != null) {
            sensorManager.unregisterListener(this, gyroscopeSensor);
        }
        // Restore gyro motion to its original state when exiting this activity
        MotionConfig.setGyroMotionEnabled(wasGyroMotionEnabled);
        Log.d(TAG, "Gyro motion restored to: " + wasGyroMotionEnabled);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) {
            try {
                glSurfaceView.onResume();
            } catch (Exception e) {
                Log.w(TAG, "Error resuming GLSurfaceView: " + e.getMessage());
            }
        }
        if (renderer != null) {
            renderer.resume();
        }
        if (sensorManager != null && gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    /**
     * Handle back button press. Shows a confirmation dialog if there are unsaved changes.
     */
    private void onBackButtonPressed() {
        if (hasUnsavedChanges) {
            showUnsavedChangesDialog();
        } else {
            // Reset sprite position flag when exiting texture editing
            resetTextureEditingState();
            finish();
        }
    }

    /**
     * Reset the sprite to normal state after texture editing.
     */
    private void resetTextureEditingState() {
        if (renderer != null) {
            Scene scene = renderer.getCurrentScene();
            if (scene != null && !scene.getSprites().isEmpty()) {
                Sprite sprite = scene.getSprites().get(0);
                if (sprite != null) {
                    sprite.setPositionAtZero(false);
                    Log.d(TAG, "Reset texture editing state for sprite: " + sprite.getName());
                }
            }
        }
    }

    /**
     * Show dialog asking user to save or discard changes.
     */
    private void showUnsavedChangesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.unsaved_changes_title);
        builder.setMessage(R.string.unsaved_changes_message);

        builder.setPositiveButton(R.string.save_button, (dialog, which) -> {
            saveChanges();
            resetTextureEditingState();
            finish();
        });

        builder.setNegativeButton(R.string.discard_button, (dialog, which) -> {
            // Discard changes and exit
            resetTextureEditingState();
            finish();
        });

        builder.setCancelable(true);
        builder.show();
    }

    /**
     * Save the current sprite texture changes to the scene file.
     */
    private void saveChanges() {
        try {
            if (renderer == null) {
                Toast.makeText(this, "Error: Scene not loaded", Toast.LENGTH_SHORT).show();
                return;
            }

            Scene scene = renderer.getCurrentScene();
            if (scene == null) {
                Toast.makeText(this, "Error: Scene not available", Toast.LENGTH_SHORT).show();
                return;
            }

            Sprite currentSprite = renderer.getSelectedSprite();
            if (currentSprite == null) {
                Toast.makeText(this, "Error: Sprite not available", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create an Intent to return the edited sprite data
            Intent resultIntent = new Intent();
            resultIntent.putExtra(RESULT_SPRITE_NAME, spriteData.name);
            resultIntent.putExtra(RESULT_WIDTH, currentSprite.getWidth());
            resultIntent.putExtra(RESULT_HEIGHT, currentSprite.getHeight());

            // Return texture resource information (name and ID)
            resultIntent.putExtra(RESULT_TEXTURE_RESOURCE, currentSprite.getTextureResource());
            resultIntent.putExtra(RESULT_TEXTURE_RESOURCE_ID, currentSprite.getTextureResourceId());

            // Return the texture coordinates - this is the ONLY texture state that matters
            float[] texCoordinates = currentSprite.getTextureCoordinates();
            if (texCoordinates != null) {
                resultIntent.putExtra(RESULT_TEXTURE_COORDINATES, texCoordinates);
                Log.d(TAG, "Returning texture coordinates: [" + texCoordinates[0] + "," + texCoordinates[1] + ",...]");
            }

            // Set the result and finish
            setResult(RESULT_OK, resultIntent);
            hasUnsavedChanges = false;
            Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Texture changes saved for sprite: " + spriteData.name);


            // Finish the activity and return to EditSceneActivity
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error saving changes: " + e.getMessage(), e);
            Toast.makeText(this, "Error saving changes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
