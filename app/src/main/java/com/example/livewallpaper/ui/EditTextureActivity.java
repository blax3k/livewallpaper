package com.example.livewallpaper.ui;

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
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.Scene;
import com.example.livewallpaper.scene.Sprite;

public class EditTextureActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "EditTextureActivity";
    public static final String EXTRA_SPRITE_NAME = "sprite_name";
    public static final String EXTRA_SCENE_FILE_NAME = "scene_file_name";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";
    public static final String EXTRA_TEXTURE_SCALE = "texture_scale";
    public static final String EXTRA_TEXTURE_OFFSET_U = "texture_offset_u";
    public static final String EXTRA_TEXTURE_OFFSET_V = "texture_offset_v";

    // Result data keys for passing sprite changes back to EditSceneActivity
    public static final String RESULT_SPRITE_NAME = "result_sprite_name";
    public static final String RESULT_WIDTH = "result_width";
    public static final String RESULT_HEIGHT = "result_height";
    public static final String RESULT_TEXTURE_SCALE = "result_texture_scale";
    public static final String RESULT_TEXTURE_OFFSET_U = "result_texture_offset_u";
    public static final String RESULT_TEXTURE_OFFSET_V = "result_texture_offset_v";

    private SquareGLSurfaceView glSurfaceView;
    private ScenePreviewRenderer renderer;
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private String spriteName;
    private String sceneFileName;
    private boolean glSetupComplete = false;
    private SeekBar widthSlider;
    private SeekBar heightSlider;
    private SeekBar textureScaleSlider;
    private TextView widthValueText;
    private TextView heightValueText;
    private TextView textureScaleValueText;
    private Sprite currentSprite;
    private float lastTouchX = 0;
    private float lastTouchY = 0;
    private boolean isTouching = false;
    private boolean hasUnsavedChanges = false;
    private float passedWidth = 0.0f;
    private float passedHeight = 0.0f;
    private float passedTextureScale = 1.0f;
    private float passedTextureOffsetU = 0.0f;
    private float passedTextureOffsetV = 0.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_texture);

        Log.d(TAG, "EditTextureActivity onCreate called");

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

        // Get sprite name and scene file name from intent
        spriteName = getIntent().getStringExtra(EXTRA_SPRITE_NAME);
        sceneFileName = getIntent().getStringExtra(EXTRA_SCENE_FILE_NAME);

        if (spriteName == null || sceneFileName == null) {
            Log.e(TAG, "Missing sprite name or scene file name!");
            Toast.makeText(this, "Error: Missing sprite data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get texture coordinates from intent (if provided)
        passedWidth = getIntent().getFloatExtra(EXTRA_WIDTH, 0.0f);
        passedHeight = getIntent().getFloatExtra(EXTRA_HEIGHT, 0.0f);
        passedTextureScale = getIntent().getFloatExtra(EXTRA_TEXTURE_SCALE, 1.0f);
        passedTextureOffsetU = getIntent().getFloatExtra(EXTRA_TEXTURE_OFFSET_U, 0.0f);
        passedTextureOffsetV = getIntent().getFloatExtra(EXTRA_TEXTURE_OFFSET_V, 0.0f);

        Log.d(TAG, "Sprite name: " + spriteName + ", Scene file: " + sceneFileName +
              ", Width: " + passedWidth + ", Height: " + passedHeight +
              ", TextureScale: " + passedTextureScale + ", OffsetU: " + passedTextureOffsetU + ", OffsetV: " + passedTextureOffsetV);

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
            Log.d(TAG, "Setting up GLSurfaceView renderer for scene: " + sceneFileName);

            // Create renderer that loads the full scene
            renderer = new ScenePreviewRenderer(this, sceneFileName, spriteName);
            glSurfaceView.setRenderer(renderer);

            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

            // Set up touch listener for texture coordinate manipulation
            glSurfaceView.setOnTouchListener(this::handleGLViewTouch);

            glSetupComplete = true;

            // Initialize sliders after a short delay to ensure scene is loaded
            new Handler(Looper.getMainLooper()).postDelayed(this::initializeSliders, 500);

            Log.d(TAG, "GLSurfaceView configured successfully for scene: " + sceneFileName);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up GLSurfaceView: " + e.getMessage(), e);
            Toast.makeText(this, "Error setting up preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeSliders() {
        widthSlider = findViewById(R.id.width_slider);
        heightSlider = findViewById(R.id.height_slider);
        textureScaleSlider = findViewById(R.id.texture_scale_slider);
        widthValueText = findViewById(R.id.width_value);
        heightValueText = findViewById(R.id.height_value);
        textureScaleValueText = findViewById(R.id.texture_scale_value);

        if (renderer != null) {
            Scene scene = renderer.getCurrentScene();
            if (scene != null && !scene.getSprites().isEmpty()) {
                currentSprite = scene.getSprites().get(0);

                if (currentSprite != null) {
                    // Apply dimensions passed from EditSceneActivity
                    if (passedWidth > 0) {
                        currentSprite.setWidth(passedWidth);
                    }
                    if (passedHeight > 0) {
                        currentSprite.setHeight(passedHeight);
                    }

                    // Apply texture coordinates passed from EditSceneActivity
                    if (passedTextureScale > 0) {
                        currentSprite.setTextureScale(passedTextureScale);
                    }
                    if (passedTextureOffsetU != 0) {
                        currentSprite.setTextureOffsetU(passedTextureOffsetU);
                    }
                    if (passedTextureOffsetV != 0) {
                        currentSprite.setTextureOffsetV(passedTextureOffsetV);
                    }

                    // Set initial values
                    float currentWidth = currentSprite.getWidth();
                    float currentHeight = currentSprite.getHeight();
                    float currentTextureScale = currentSprite.getTextureScale();

                    // Width/Height: max 11.0, increments of 0.2 (max slider value = 55)
                    widthSlider.setProgress(Math.round(currentWidth / 0.2f));
                    heightSlider.setProgress(Math.round(currentHeight / 0.2f));

                    // Texture Scale: 1.0 to 8.0, increments of 0.1 (max slider value = 70)
                    // Formula: (currentTextureScale - 1.0) / 0.1 to get slider progress
                    int textureScaleProgress = Math.round((currentTextureScale - 1.0f) / 0.1f);
                    textureScaleSlider.setProgress(Math.max(0, Math.min(70, textureScaleProgress)));

                    updateWidthDisplay(currentWidth);
                    updateHeightDisplay(currentHeight);
                    updateTextureScaleDisplay(currentTextureScale);

                    // Set up listeners
                    widthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if (fromUser && currentSprite != null) {
                                float width = progress * 0.2f;
                                currentSprite.setWidth(width);
                                updateWidthDisplay(width);
                                // Update texture scale display since it may have been adjusted automatically
                                updateTextureScaleDisplay(currentSprite.getTextureScale());
                                hasUnsavedChanges = true;
                                Log.d(TAG, "Width changed to: " + width + ", textureScale now: " + currentSprite.getTextureScale());
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {}

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {}
                    });

                    heightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if (fromUser && currentSprite != null) {
                                float height = progress * 0.2f;
                                currentSprite.setHeight(height);
                                updateHeightDisplay(height);
                                // Update texture scale display since it may have been adjusted automatically
                                updateTextureScaleDisplay(currentSprite.getTextureScale());
                                hasUnsavedChanges = true;
                                Log.d(TAG, "Height changed to: " + height + ", textureScale now: " + currentSprite.getTextureScale());
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {}

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {}
                    });

                    textureScaleSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if (fromUser && currentSprite != null) {
                                // Texture scale: 1.0 + (progress * 0.1), range 1.0 to 8.0
                                float scale = 1.0f + (progress * 0.1f);
                                currentSprite.setTextureScale(scale);
                                updateTextureScaleDisplay(scale);
                                hasUnsavedChanges = true;
                                Log.d(TAG, "Texture scale changed to: " + scale);
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {}

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {}
                    });
                }
            }
        }
    }

    private void updateWidthDisplay(float width) {
        if (widthValueText != null) {
            widthValueText.setText(String.format("%.1f", width));
        }
    }

    private void updateHeightDisplay(float height) {
        if (heightValueText != null) {
            heightValueText.setText(String.format("%.1f", height));
        }
    }

    private void updateTextureScaleDisplay(float scale) {
        if (textureScaleValueText != null) {
            textureScaleValueText.setText(String.format("%.1fx", scale));
        }
    }

    /**
     * Handle touch input on the GLView to manipulate texture coordinates.
     * Dragging moves the texture in the corresponding direction.
     */
    private boolean handleGLViewTouch(View v, MotionEvent event) {
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

                    // Apply the offset to the sprite's texture coordinates
                    currentSprite.offsetTextureCoordinates(uOffset, vOffset);
                    hasUnsavedChanges = true;

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
            finish();
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
            finish();
        });

        builder.setNegativeButton(R.string.discard_button, (dialog, which) -> {
            // Discard changes and exit
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

            if (currentSprite == null) {
                Toast.makeText(this, "Error: Sprite not available", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create an Intent to return the edited sprite data
            Intent resultIntent = new Intent();
            resultIntent.putExtra(RESULT_SPRITE_NAME, spriteName);
            resultIntent.putExtra(RESULT_WIDTH, currentSprite.getWidth());
            resultIntent.putExtra(RESULT_HEIGHT, currentSprite.getHeight());
            resultIntent.putExtra(RESULT_TEXTURE_SCALE, currentSprite.getTextureScale());
            resultIntent.putExtra(RESULT_TEXTURE_OFFSET_U, currentSprite.getTextureOffsetU());
            resultIntent.putExtra(RESULT_TEXTURE_OFFSET_V, currentSprite.getTextureOffsetV());

            // Set the result and finish
            setResult(RESULT_OK, resultIntent);
            hasUnsavedChanges = false;
            Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Texture changes saved for sprite: " + spriteName);

            // Finish the activity and return to EditSceneActivity
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error saving changes: " + e.getMessage(), e);
            Toast.makeText(this, "Error saving changes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
