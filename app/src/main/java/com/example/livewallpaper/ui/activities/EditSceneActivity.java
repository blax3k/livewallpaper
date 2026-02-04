package com.example.livewallpaper.ui.activities;

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
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.SceneManager;
import com.example.livewallpaper.scene.Sprite;
import com.example.livewallpaper.scene.TextureEditState;
import com.example.livewallpaper.ui.managers.SceneFileManager;
import com.example.livewallpaper.ui.adapters.SpritesDropdownAdapter;
import com.example.livewallpaper.ui.views.SquareGLSurfaceView;
import com.example.livewallpaper.ui.controllers.DrawableImagePickerDialog;
import com.example.livewallpaper.ui.builders.SpriteDetailsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditSceneActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "EditSceneActivity";
    public static final String EXTRA_SCENE_FILE_NAME = "scene_file_name";

    private SquareGLSurfaceView glSurfaceView;
    private SceneManager renderer;
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private Spinner spritesSpinner;
    private ScrollView spriteDetailsContainer;
    private TextView positionXValue;
    private TextView positionYValue;

    // Touch handling for sprite position
    private float lastTouchX = 0;
    private float lastTouchY = 0;
    private boolean isTouching = false;
    private static final float POSITION_MIN = -15f;
    private static final float POSITION_MAX = 15f;

    // Helper controllers
    private SpriteDetailsBuilder spriteDetailsBuilder;
    private SceneFileManager sceneFileManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "EditSceneActivity onCreate called");

        setContentView(R.layout.activity_edit_scene);
        Log.d(TAG, "Edit scene layout inflated successfully");

        // Initialize view references
        spritesSpinner = findViewById(R.id.sprites_spinner);
        spriteDetailsContainer = findViewById(R.id.sprite_details_container);
        positionXValue = findViewById(R.id.position_x_value);
        positionYValue = findViewById(R.id.position_y_value);

        SeekBar scaleSlider = findViewById(R.id.scale_slider);
        TextView scaleValue = findViewById(R.id.scale_value);
        EditText widthEdit = findViewById(R.id.width_edit);
        EditText heightEdit = findViewById(R.id.height_edit);
        SeekBar parallaxMultiplierSlider = findViewById(R.id.parallax_multiplier_slider);
        TextView parallaxMultiplierValue = findViewById(R.id.parallax_multiplier_value);
        SeekBar focusPointSlider = findViewById(R.id.focus_point_slider);
        TextView focusPointValue = findViewById(R.id.focus_point_value);
        TableLayout propertiesTable = findViewById(R.id.sprite_properties_table);

        // Set up click listeners for manual position input
        positionXValue.setOnClickListener(v -> showPositionInputDialog("X"));
        positionYValue.setOnClickListener(v -> showPositionInputDialog("Y"));

        // Initialize sensor manager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (gyroscopeSensor != null) {
                Log.d(TAG, "Gyroscope sensor found");
            } else {
                Log.w(TAG, "Gyroscope sensor not available on this device");
            }
        }

        // Set up save button
        setupSaveButton();

        // Get the scene file name from the intent
        String sceneFileName = getIntent().getStringExtra(EXTRA_SCENE_FILE_NAME);
        if (sceneFileName != null) {
            Log.d(TAG, "Scene file name: " + sceneFileName);
            displaySceneInfo(sceneFileName);
            setupGLSurfaceView(sceneFileName, propertiesTable, scaleSlider, scaleValue,
                    widthEdit, heightEdit, parallaxMultiplierSlider, parallaxMultiplierValue,
                    focusPointSlider, focusPointValue);
        } else {
            Log.e(TAG, "No scene file name provided!");
            Toast.makeText(this, "Error: No scene selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void displaySceneInfo(String sceneFileName) {
        TextView sceneNameTextView = findViewById(R.id.scene_name_display);
        if (sceneNameTextView != null) {
            sceneNameTextView.setText("Edit " + sceneFileName);
        }
    }

    private void setupGLSurfaceView(String sceneFileName, TableLayout propertiesTable,
                                    SeekBar scaleSlider, TextView scaleValue,
                                    EditText widthEdit, EditText heightEdit,
                                    SeekBar parallaxMultiplierSlider, TextView parallaxMultiplierValue,
                                    SeekBar focusPointSlider, TextView focusPointValue) {
        glSurfaceView = findViewById(R.id.scene_preview_gl_view);
        if (glSurfaceView != null) {
            try {
                // Set EGLContext version
                glSurfaceView.setEGLContextClientVersion(2);

                // Set the renderer
                renderer = new SceneManager(this, sceneFileName);
                glSurfaceView.setRenderer(renderer);

                // Set render mode to continuous
                glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

                // Set up touch listener for sprite position manipulation
                glSurfaceView.setOnTouchListener((v, event) -> handleGLViewTouch(v, event));

                // Initialize helper classes
                spriteDetailsBuilder = new SpriteDetailsBuilder(this, renderer, propertiesTable,
                        null, positionXValue, null, positionYValue,
                        scaleSlider, scaleValue, widthEdit, heightEdit,
                        parallaxMultiplierSlider, parallaxMultiplierValue);
                sceneFileManager = new SceneFileManager(this, renderer);

                // Set up texture button listener
                spriteDetailsBuilder.setTextureButtonListener(v -> openEditTextureActivity());

                // Set up focus point slider
                setupFocusPointSlider(focusPointSlider, focusPointValue);

                Log.d(TAG, "GLSurfaceView configured successfully");

                // Populate sprite list after a short delay to allow scene to load
                new Handler(Looper.getMainLooper()).postDelayed(this::populateSpritesListView, 500);
            } catch (Exception e) {
                Log.e(TAG, "Error setting up GLSurfaceView: " + e.getMessage(), e);
                Toast.makeText(this, "Error setting up preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e(TAG, "GLSurfaceView not found!");
            Toast.makeText(this, "Error: GLSurfaceView not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void populateSpritesListView() {
        if (spritesSpinner != null && renderer != null) {
            try {
                // Get the sprite list from the renderer
                List<Sprite> allSprites = renderer.getAllSprites();

                // Create a list of sprite names
                List<String> spriteNames = new ArrayList<>();
                for (Sprite sprite : allSprites) {
                    spriteNames.add(sprite.getName());
                }

                // Create and set the custom adapter (includes "+ sprite" button)
                SpritesDropdownAdapter adapter = new SpritesDropdownAdapter(this, spriteNames);
                spritesSpinner.setAdapter(adapter);

                // Set up item selection listener
                spritesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        // Check if the "+ sprite" item was selected
                        if (adapter.isAddSpriteItem(position)) {
                            // Show image picker dialog
                            showAddSpriteDialog();
                            // Reset spinner to the first sprite
                            if (adapter.getSpriteCount() > 0) {
                                spritesSpinner.setSelection(0);
                            }
                        } else {
                            // Normal sprite selection
                            Sprite selectedSprite = renderer.selectSpriteByIndex(position);
                            if (selectedSprite != null) {
                                showSpriteDetails(selectedSprite);
                            }
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

                Log.d(TAG, "Sprites spinner populated with " + spriteNames.size() + " items (plus add sprite button)");
            } catch (Exception e) {
                Log.e(TAG, "Error populating sprites spinner: " + e.getMessage(), e);
                Toast.makeText(this, "Error loading sprite list: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showSpriteDetails(Sprite sprite) {
        if (sprite == null || spriteDetailsBuilder == null) {
            return;
        }

        spriteDetailsBuilder.build(sprite);

        if (spriteDetailsContainer != null) {
            spriteDetailsContainer.setVisibility(View.VISIBLE);
            Log.d(TAG, "Showing details for sprite: " + sprite.getName());
        }
    }

    /**
     * Show dialog to add a new sprite by selecting an image from drawables.
     */
    private void showAddSpriteDialog() {
        DrawableImagePickerDialog.showImagePickerDialog(this, (imageName, resourceId) -> {
            addNewSprite(imageName, resourceId);
        });
    }

    /**
     * Add a new sprite to the scene with a 1.0x1.0 size.
     *
     * @param imageName the name of the drawable resource
     * @param resourceId the resource ID of the drawable
     */
    private void addNewSprite(String imageName, int resourceId) {
        try {
            if (renderer == null) {
                Toast.makeText(this, "Error: Scene not loaded", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create a new sprite with default properties (1.0x1.0, at origin, full parallax)
            com.example.livewallpaper.scene.SpriteData spriteData = new com.example.livewallpaper.scene.SpriteData();
            spriteData.textureResource = imageName;
            spriteData.textureResourceId = resourceId;
            spriteData.name = imageName;
            spriteData.width = 1.0f;
            spriteData.height = 1.0f;
            spriteData.positionX = 0.0f;
            spriteData.positionY = 0.0f;
            spriteData.parallaxMultiplier = 1.0f;
            spriteData.texCoordinates = null; // Use default texture coordinates

            // Create the sprite from the data
            Sprite newSprite = new Sprite(spriteData);

            // Add to the scene
            renderer.addSpriteToScene(newSprite);

            Log.d(TAG, "Added new sprite: " + imageName);

            // Refresh the spinner to show the new sprite
            refreshSpritesList();

            Toast.makeText(this, "Sprite '" + imageName + "' added", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error adding new sprite: " + e.getMessage(), e);
            Toast.makeText(this, "Error adding sprite: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Refresh the sprites list in the spinner.
     */
    private void refreshSpritesList() {
        populateSpritesListView();
    }

    private void openEditTextureActivity() {
        Sprite currentSprite = renderer.getSelectedSprite();
        if (currentSprite == null) {
            Toast.makeText(this, "No sprite selected", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, EditTextureActivity.class);
        intent.putExtra(EditTextureActivity.EXTRA_SPRITE_NAME, currentSprite.getName());
        intent.putExtra(EditTextureActivity.EXTRA_SCENE_FILE_NAME, getIntent().getStringExtra(EXTRA_SCENE_FILE_NAME));
        intent.putExtra(EditTextureActivity.EXTRA_WIDTH, currentSprite.getWidth());
        intent.putExtra(EditTextureActivity.EXTRA_HEIGHT, currentSprite.getHeight());

        TextureEditState currentState = currentSprite.getCurrentTextureEditState();
        intent.putExtra(EditTextureActivity.EXTRA_TEXTURE_SCALE, currentState.getTextureScale());
        intent.putExtra(EditTextureActivity.EXTRA_TEXTURE_OFFSET_U, currentState.getTextureOffsetU());
        intent.putExtra(EditTextureActivity.EXTRA_TEXTURE_OFFSET_V, currentState.getTextureOffsetV());

        startActivityForResult(intent, 100);
    }

    @Override
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
            glSurfaceView.onPause();
        }
        if (renderer != null) {
            renderer.pause();
        }
        if (sensorManager != null && gyroscopeSensor != null) {
            sensorManager.unregisterListener(this, gyroscopeSensor);
            Log.d(TAG, "Gyroscope listener unregistered");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }
        if (renderer != null) {
            renderer.resume();
        }
        if (sensorManager != null && gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
            Log.d(TAG, "Gyroscope listener registered");
        }
    }

    private void setupSaveButton() {
        Button saveButton = findViewById(R.id.save_button);
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> {
                String currentSceneName = getIntent().getStringExtra(EXTRA_SCENE_FILE_NAME);
                // Pass a callback to be executed after successful save
                sceneFileManager.showSaveDialog(currentSceneName, () -> {
                    // Mark that a save occurred so SceneListActivity can refresh
                    setResult(RESULT_OK);
                });
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            Sprite currentSprite = renderer.getSelectedSprite();
            if (currentSprite == null) {
                Log.e(TAG, "No sprite selected when activity result returned");
                return;
            }

            try {
                String spriteName = data.getStringExtra(EditTextureActivity.RESULT_SPRITE_NAME);
                float width = data.getFloatExtra(EditTextureActivity.RESULT_WIDTH, currentSprite.getWidth());
                float height = data.getFloatExtra(EditTextureActivity.RESULT_HEIGHT, currentSprite.getHeight());
                float textureScale = data.getFloatExtra(EditTextureActivity.RESULT_TEXTURE_SCALE, 1.0f);
                float textureOffsetU = data.getFloatExtra(EditTextureActivity.RESULT_TEXTURE_OFFSET_U, 0f);
                float textureOffsetV = data.getFloatExtra(EditTextureActivity.RESULT_TEXTURE_OFFSET_V, 0f);

                renderer.updateSpriteDimensions(currentSprite, width, height);
                renderer.updateSpriteTexture(currentSprite, textureScale, textureOffsetU, textureOffsetV);

                Log.d(TAG, "Applied texture edits from EditTextureActivity to sprite: " + spriteName);

                spriteDetailsBuilder.updateDimensionDisplays(width, height);
                spriteDetailsBuilder.updateScaleDisplay(currentSprite);

                Toast.makeText(this, "Texture changes applied to sprite", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Log.e(TAG, "Error applying texture changes: " + e.getMessage(), e);
                Toast.makeText(this, "Error applying changes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Handle touch events on the GL view to move sprite position.
     * Dragging updates the sprite's X and Y position within bounds [-15, 15].
     */
    private boolean handleGLViewTouch(View v, MotionEvent event) {
        Sprite currentSprite = renderer != null ? renderer.getSelectedSprite() : null;
        if (currentSprite == null) {
            return false;
        }

        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isTouching = true;
                lastTouchX = touchX;
                lastTouchY = touchY;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isTouching) {
                    // Calculate delta movement in pixels
                    float deltaX = (touchX - lastTouchX) * 0.15f;
                    float deltaY = (touchY - lastTouchY) * 0.15f;

                    // Get view dimensions
                    float viewWidth = v.getWidth();
                    float viewHeight = v.getHeight();

                    // Convert pixel movement to position coordinates
                    // The position range is -15 to 15, spread across the view
                    float positionRange = POSITION_MAX - POSITION_MIN;
                    float deltaPositionX = (deltaX / viewWidth) * positionRange;
                    float deltaPositionY = (deltaY / viewHeight) * positionRange;

                    // Get current position and apply delta with clamping
                    float newX = Math.max(POSITION_MIN, Math.min(POSITION_MAX,
                            currentSprite.getPositionX() + deltaPositionX));
                    float newY = Math.max(POSITION_MIN, Math.min(POSITION_MAX,
                            currentSprite.getPositionY() + deltaPositionY));

                    // Update sprite position
                    renderer.updateSpritePosition(currentSprite, newX, newY);
                    updatePositionDisplay(newX, newY);

                    // Update last touch position
                    lastTouchX = touchX;
                    lastTouchY = touchY;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isTouching = false;
                // Snap to nearest 0.05 increment when finger is lifted
                if (currentSprite != null) {
                    float snappedX = Math.round(currentSprite.getPositionX() * 20f) / 20f;
                    float snappedY = Math.round(currentSprite.getPositionY() * 20f) / 20f;
                    renderer.updateSpritePosition(currentSprite, snappedX, snappedY);
                    updatePositionDisplay(snappedX, snappedY);
                }
                return true;
        }

        return false;
    }

    /**
     * Update the position display fields.
     */
    private void updatePositionDisplay(float x, float y) {
        if (positionXValue != null) {
            positionXValue.setText(String.format(Locale.US, "%.2f", x));
        }
        if (positionYValue != null) {
            positionYValue.setText(String.format(Locale.US, "%.2f", y));
        }
    }

    /**
     * Show a dialog to manually input a position value.
     */
    private void showPositionInputDialog(String axis) {
        Sprite currentSprite = renderer != null ? renderer.getSelectedSprite() : null;
        if (currentSprite == null) {
            Toast.makeText(this, "No sprite selected", Toast.LENGTH_SHORT).show();
            return;
        }

        float currentValue = axis.equals("X") ? currentSprite.getPositionX() : currentSprite.getPositionY();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Position " + axis);
        builder.setMessage("Enter a value between -15 and 15:");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format(Locale.US, "%.2f", currentValue));
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                float value = Float.parseFloat(input.getText().toString());

                // Clamp value to range [-15, 15]
                if (value < POSITION_MIN || value > POSITION_MAX) {
                    Toast.makeText(EditSceneActivity.this,
                            "Value must be between " + POSITION_MIN + " and " + POSITION_MAX,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Update sprite position
                if (axis.equals("X")) {
                    renderer.updateSpritePosition(currentSprite, value, currentSprite.getPositionY());
                    updatePositionDisplay(value, currentSprite.getPositionY());
                } else {
                    renderer.updateSpritePosition(currentSprite, currentSprite.getPositionX(), value);
                    updatePositionDisplay(currentSprite.getPositionX(), value);
                }

                Toast.makeText(EditSceneActivity.this, "Position " + axis + " updated", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(EditSceneActivity.this, "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Set up the focus point slider to initialize with the scene's focus point and handle changes.
     * Focus point ranges from 0.0 (left) to 1.0 (right), displayed as 0-100 on the slider.
     */
    private void setupFocusPointSlider(SeekBar focusPointSlider, TextView focusPointValue) {
        if (focusPointSlider == null || focusPointValue == null || renderer == null) {
            Log.w(TAG, "Focus point slider not properly initialized");
            return;
        }

        // Initialize the slider with the scene's current focus point
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                float currentFocusPoint = renderer.getCurrentScene().getXFocus();
                int sliderPosition = (int) (currentFocusPoint * 100);
                focusPointSlider.setProgress(sliderPosition);
                updateFocusPointDisplay(focusPointValue, currentFocusPoint);
                Log.d(TAG, "Focus point slider initialized with value: " + currentFocusPoint);
            } catch (Exception e) {
                Log.e(TAG, "Error initializing focus point slider: " + e.getMessage(), e);
            }
        }, 1000);

        // Set up listener for slider changes
        focusPointSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Convert slider progress (0-100) to focus point (0.0-1.0)
                    float newFocusPoint = progress / 100.0f;

                    // Update scene's focus point
                    renderer.getCurrentScene().setXFocus(newFocusPoint);
                    Log.d(TAG, "Focus point changed to: " + newFocusPoint);

                    // Update display value
                    updateFocusPointDisplay(focusPointValue, newFocusPoint);

                    // Update phone guide position in the renderer
                    renderer.updatePhoneGuidePosition(newFocusPoint);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    /**
     * Update the focus point value display in the UI.
     */
    private void updateFocusPointDisplay(TextView focusPointValue, float value) {
        focusPointValue.setText(String.format(Locale.US, "%.2f", value));
    }
}

