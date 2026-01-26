package com.example.livewallpaper.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.Sprite;
import com.example.livewallpaper.scene.TextureEditState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EditSceneActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "EditSceneActivity";
    public static final String EXTRA_SCENE_FILE_NAME = "scene_file_name";
    private SquareGLSurfaceView glSurfaceView;
    private ScenePreviewRenderer renderer;
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private List<Sprite> allSprites;
    private Spinner spritesSpinner;
    private ScrollView spriteDetailsContainer;
    private Sprite currentSprite;
    private float originalAspectRatio = 1.0f;
    private SeekBar positionXSlider;
    private TextView positionXValue;
    private SeekBar positionYSlider;
    private TextView positionYValue;
    private SeekBar scaleSlider;
    private TextView scaleValue;
    private EditText widthEdit;
    private EditText heightEdit;
    private SeekBar parallaxMultiplierSlider;
    private TextView parallaxMultiplierValue;

    /**
     * Inner class to encapsulate slider configuration and reduce boilerplate.
     * Holds all parameters needed to set up a generic slider with edit dialog support.
     */
    private class SliderConfig {
        final SeekBar slider;
        final TextView valueDisplay;
        final float minValue;
        final float maxValue;
        final float increment;
        final String label;
        final Consumer<Float> onValueChanged;
        final Supplier<Float> currentValueGetter;
        final Runnable onEditComplete;
        final boolean showResortCallback;

        SliderConfig(SeekBar slider, TextView display, float min, float max, float inc,
                     String label, Consumer<Float> onChange, Supplier<Float> currentGetter,
                     Runnable onComplete, boolean showResort) {
            this.slider = slider;
            this.valueDisplay = display;
            this.minValue = min;
            this.maxValue = max;
            this.increment = inc;
            this.label = label;
            this.onValueChanged = onChange;
            this.currentValueGetter = currentGetter;
            this.onEditComplete = onComplete;
            this.showResortCallback = showResort;
        }

        SliderConfig(SeekBar slider, TextView display, float min, float max, float inc,
                     String label, Consumer<Float> onChange, Supplier<Float> currentGetter) {
            this(slider, display, min, max, inc, label, onChange, currentGetter, null, false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "EditSceneActivity onCreate called");

        setContentView(R.layout.activity_edit_scene);
        Log.d(TAG, "Edit scene layout inflated successfully");

        // Initialize view references
        spritesSpinner = findViewById(R.id.sprites_spinner);
        spriteDetailsContainer = findViewById(R.id.sprite_details_container);
        positionXSlider = findViewById(R.id.position_x_slider);
        positionXValue = findViewById(R.id.position_x_value);
        positionYSlider = findViewById(R.id.position_y_slider);
        positionYValue = findViewById(R.id.position_y_value);
        scaleSlider = findViewById(R.id.scale_slider);
        scaleValue = findViewById(R.id.scale_value);
        widthEdit = findViewById(R.id.width_edit);
        heightEdit = findViewById(R.id.height_edit);
        parallaxMultiplierSlider = findViewById(R.id.parallax_multiplier_slider);
        parallaxMultiplierValue = findViewById(R.id.parallax_multiplier_value);

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
            setupGLSurfaceView(sceneFileName);
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

    private void setupGLSurfaceView(String sceneFileName) {
        glSurfaceView = findViewById(R.id.scene_preview_gl_view);
        if (glSurfaceView != null) {
            try {
                // Set EGLContext version
                glSurfaceView.setEGLContextClientVersion(2);

                // Set the renderer
                renderer = new ScenePreviewRenderer(this, sceneFileName);
                glSurfaceView.setRenderer(renderer);

                // Set render mode to continuous
                glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

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
                // Get the scene from the renderer
                allSprites = new ArrayList<>(renderer.getCurrentScene().getSprites());

                // Create a list of sprite names
                List<String> spriteNames = new ArrayList<>();
                for (Sprite sprite : allSprites) {
                    spriteNames.add(sprite.getName());
                }

                // Create and set the adapter
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    spriteNames
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spritesSpinner.setAdapter(adapter);

                // Set up item selection listener
                spritesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (position < allSprites.size()) {
                            showSpriteDetails(allSprites.get(position));
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

                Log.d(TAG, "Sprites spinner populated with " + spriteNames.size() + " items");
            } catch (Exception e) {
                Log.e(TAG, "Error populating sprites spinner: " + e.getMessage(), e);
                Toast.makeText(this, "Error loading sprite list: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showSpriteDetails(Sprite sprite) {
        currentSprite = sprite;
        TableLayout propertiesTable = findViewById(R.id.sprite_properties_table);
        if (propertiesTable != null) {
            propertiesTable.removeAllViews();

            // Add name as read-only property
            addPropertyRow(propertiesTable, "Name", sprite.getName());

            // Add texture resource as a button
            addPropertyButtonRow(propertiesTable, "Texture Resource");

            // ...existing code...
            setupGenericSlider(new SliderConfig(
                positionXSlider, positionXValue, -15f, 15f, 0.25f, "Position X",
                v -> currentSprite.setPositionX(v),
                () -> currentSprite.getPositionX()
            ));

            setupGenericSlider(new SliderConfig(
                positionYSlider, positionYValue, -15f, 15f, 0.25f, "Position Y",
                v -> currentSprite.setPositionY(v),
                () -> currentSprite.getPositionY()
            ));

            // Store original aspect ratio for scale functionality
            float currentWidth = currentSprite.getWidth();
            float currentHeight = currentSprite.getHeight();
            originalAspectRatio = currentHeight > 0 ? currentWidth / currentHeight : 1.0f;

            // Set up scale slider (0.1x to 5.0x)
            setupScaleSlider();

            // Set up width and height edit fields with proportional updates
            setupDimensionEditFields();

            setupGenericSlider(new SliderConfig(
                parallaxMultiplierSlider, parallaxMultiplierValue, 0.1f, 2.0f, 0.01f, "Parallax Multiplier",
                v -> currentSprite.setParallaxMultiplier(v),
                () -> currentSprite.getParallaxMultiplier(),
                () -> { if (renderer != null) renderer.requestSpriteResort(); },
                true
            ));

            // Show details view
            if (spriteDetailsContainer != null) {
                spriteDetailsContainer.setVisibility(View.VISIBLE);
                Log.d(TAG, "Showing details for sprite: " + sprite.getName());
            }
        }
    }

    /**
     * Generic method to set up a slider with manual edit dialog support.
     * Eliminates boilerplate by handling all slider configuration in one place.
     *
     * @param config SliderConfig containing all slider parameters
     */
    private void setupGenericSlider(SliderConfig config) {
        if (config.slider == null || config.valueDisplay == null) return;

        // Remove any existing listener to avoid feedback loops
        config.slider.setOnSeekBarChangeListener(null);

        // Get current value and calculate initial progress
        float currentValue = config.currentValueGetter.get();
        int sliderProgress = (int) ((currentValue - config.minValue) / config.increment);
        config.slider.setProgress(sliderProgress);
        config.valueDisplay.setText(String.format("%.2f", currentValue));

        // Set up click listener for manual editing via dialog
        config.valueDisplay.setOnClickListener(v -> showGenericEditDialog(config, currentValue));

        // Set up listener for slider changes
        config.slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && currentSprite != null) {
                    float newValue = config.minValue + (progress * config.increment);
                    newValue = Math.max(config.minValue, Math.min(config.maxValue, newValue));
                    config.onValueChanged.accept(newValue);
                    config.valueDisplay.setText(String.format("%.2f", newValue));
                    Log.d(TAG, config.label + " updated to: " + newValue);

                    // Call edit complete callback if provided (e.g., for parallax resort)
                    if (config.onEditComplete != null) {
                        config.onEditComplete.run();
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    /**
     * Generic dialog for manual slider value editing.
     * Handles clamping, updating UI, and syncing slider position.
     *
     * @param config SliderConfig containing dialog parameters
     * @param currentValue the current value to display in the dialog
     */
    private void showGenericEditDialog(SliderConfig config, float currentValue) {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit " + config.label);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                          android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL |
                          android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format("%.2f", currentValue));
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                float newValue = Float.parseFloat(input.getText().toString());
                newValue = Math.max(config.minValue, Math.min(config.maxValue, newValue));

                // Update sprite value
                config.onValueChanged.accept(newValue);
                config.valueDisplay.setText(String.format("%.2f", newValue));

                // Update slider to reflect new value
                if (config.slider != null) {
                    int newProgress = (int) ((newValue - config.minValue) / config.increment);
                    config.slider.setProgress(newProgress);
                }

                // Call edit complete callback if provided
                if (config.onEditComplete != null) {
                    config.onEditComplete.run();
                }

                Log.d(TAG, config.label + " manually edited to: " + newValue);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Invalid number entered for " + config.label + ": " + e.getMessage());
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Set up the scale slider that scales both width and height proportionally
     * while maintaining the original aspect ratio.
     * Scale range: 0.2x to 15x
     * Increments: 0.1x per tick
     */
    private void setupScaleSlider() {
        if (scaleSlider == null || scaleValue == null || currentSprite == null) {
            return;
        }

        scaleSlider.setOnSeekBarChangeListener(null);

        // Fixed scale range for all sprites
        float minScale = 0.2f;
        float maxScale = 15.0f;
        float increment = 0.1f;

        // Calculate initial scale based on the larger dimension
        float currentWidth = currentSprite.getWidth();
        float currentHeight = currentSprite.getHeight();
        float largerDimension = Math.max(currentWidth, currentHeight);
        float initialScale = largerDimension; // Scale = larger dimension / 1.0

        // Clamp to valid range
        initialScale = Math.max(minScale, Math.min(maxScale, initialScale));

        // Calculate scale range and slider max
        float scaleRange = maxScale - minScale;
        int maxProgress = (int) (scaleRange / increment);

        // Set slider position
        int sliderProgress = (int) ((initialScale - minScale) / increment);
        sliderProgress = Math.max(0, Math.min(maxProgress, sliderProgress));
        scaleSlider.setMax(maxProgress);
        scaleSlider.setProgress(sliderProgress);

        // Update the display text with the initial scale
        updateScaleDisplay(initialScale);

        // Set up click listener for manual editing
        scaleValue.setOnClickListener(v -> showScaleEditDialog(currentWidth, currentHeight));

        // Set up slider listener with calculated range
        setupScaleSliderListener(minScale, maxScale, increment);
    }

    /**
     * Set up the width and height edit fields with proportional update logic.
     * When one is edited, the other updates to maintain aspect ratio.
     */
    private void setupDimensionEditFields() {
        if (widthEdit == null || heightEdit == null || currentSprite == null) {
            return;
        }

        float currentWidth = currentSprite.getWidth();
        float currentHeight = currentSprite.getHeight();

        updateDimensionDisplays(currentWidth, currentHeight);

        // Width field focus change listener
        widthEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && currentSprite != null) {
                try {
                    float newWidth = Float.parseFloat(widthEdit.getText().toString());
                    float newHeight = newWidth / originalAspectRatio;

                    currentSprite.setWidth(newWidth);
                    currentSprite.setHeight(newHeight);

                    updateDimensionDisplays(newWidth, newHeight);

                    Log.d(TAG, "Width updated from EditText: " + newWidth + ", Height: " + newHeight);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid width input");
                    updateDimensionDisplays(currentSprite.getWidth(), currentSprite.getHeight());
                }
            }
        });

        // Height field focus change listener
        heightEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && currentSprite != null) {
                try {
                    float newHeight = Float.parseFloat(heightEdit.getText().toString());
                    float newWidth = newHeight * originalAspectRatio;

                    currentSprite.setWidth(newWidth);
                    currentSprite.setHeight(newHeight);

                    updateDimensionDisplays(newWidth, newHeight);

                    Log.d(TAG, "Height updated from EditText: " + newHeight + ", Width: " + newWidth);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid height input");
                    updateDimensionDisplays(currentSprite.getWidth(), currentSprite.getHeight());
                }
            }
        });
    }

    /**
     * Show dialog for manually editing the scale value.
     */
    private void showScaleEditDialog(float baseWidth, float baseHeight) {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Scale");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                          android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText("1.00");
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                float scale = Float.parseFloat(input.getText().toString());

                float newWidth = baseWidth * scale;
                float newHeight = baseHeight * scale;

                currentSprite.setWidth(newWidth);
                currentSprite.setHeight(newHeight);

                updateScaleDisplay(scale);
                updateDimensionDisplays(newWidth, newHeight);

                // Recalculate scale slider range and update display
                updateScaleSliderDisplay();

                Log.d(TAG, "Scale manually edited to: " + scale);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Invalid scale input: " + e.getMessage());
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Update the scale display value.
     */
    private void updateScaleDisplay(float scale) {
        if (scaleValue != null) {
            scaleValue.setText(String.format("%.2fx", scale));
        }
    }

    /**
     * Update both width and height display fields.
     */
    private void updateDimensionDisplays(float width, float height) {
        if (widthEdit != null) {
            widthEdit.setText(String.format("%.2f", width));
        }
        if (heightEdit != null) {
            heightEdit.setText(String.format("%.2f", height));
        }
    }

    /**
     * Update the scale slider and display to match the current sprite dimensions.
     * This synchronizes the scale slider when sprite properties are loaded or updated
     * from external sources (e.g., EditTextureActivity).
     */
    private void updateScaleSliderDisplay() {
        if (currentSprite == null || scaleSlider == null || scaleValue == null) {
            return;
        }

        // Fixed scale range for all sprites
        float minScale = 0.2f;
        float maxScale = 15.0f;
        float increment = 0.1f;

        // Get current dimensions
        float currentWidth = currentSprite.getWidth();
        float currentHeight = currentSprite.getHeight();

        // Calculate scale based on the larger dimension divided by 1.0
        // This represents how much larger the sprite is than the base size of 1.0
        float largerDimension = Math.max(currentWidth, currentHeight);
        float scale = largerDimension; // Scale = larger dimension / 1.0

        // Clamp scale to valid range
        scale = Math.max(minScale, Math.min(maxScale, scale));

        // Calculate the scale range and slider max
        float scaleRange = maxScale - minScale;
        int maxProgress = (int) (scaleRange / increment);

        // Update slider position: (scale - minScale) / increment = progress
        int sliderProgress = (int) ((scale - minScale) / increment);
        sliderProgress = Math.max(0, Math.min(maxProgress, sliderProgress));

        scaleSlider.setOnSeekBarChangeListener(null); // Temporarily disable listener
        scaleSlider.setMax(maxProgress);
        scaleSlider.setProgress(sliderProgress);

        // Re-enable listener with updated range
        setupScaleSliderListener(minScale, maxScale, increment);

        // Update display text
        updateScaleDisplay(scale);
        updateDimensionDisplays(currentWidth, currentHeight);

        Log.d(TAG, "Updated scale slider display - Scale: " + scale + "x, Width: " + currentWidth + ", Height: " + currentHeight);
    }

    /**
     * Set up the slider listener for scale changes.
     * Extracted as a separate method to allow re-enabling after updates.
     *
     * @param minScale the minimum scale value
     * @param maxScale the maximum scale value
     * @param increment the increment per slider unit
     */
    private void setupScaleSliderListener(float minScale, float maxScale, float increment) {
        if (scaleSlider == null || currentSprite == null) {
            return;
        }

        scaleSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && currentSprite != null) {
                    // Calculate desired scale based on slider progress and dynamic range
                    float desiredScale = minScale + (progress * increment);
                    desiredScale = Math.max(minScale, Math.min(maxScale, desiredScale));

                    // Get current dimensions and calculate the base size (when larger dimension = 1.0)
                    float currentWidth = currentSprite.getWidth();
                    float currentHeight = currentSprite.getHeight();
                    float largerDimension = Math.max(currentWidth, currentHeight);

                    // Calculate what the base dimensions would be (where larger = 1.0)
                    float scaleFactor = 1.0f / largerDimension;
                    float baseWidth = currentWidth * scaleFactor;
                    float baseHeight = currentHeight * scaleFactor;

                    // Apply desired scale to base dimensions
                    float newWidth = baseWidth * desiredScale;
                    float newHeight = baseHeight * desiredScale;

                    currentSprite.setWidth(newWidth);
                    currentSprite.setHeight(newHeight);

                    updateScaleDisplay(desiredScale);
                    updateDimensionDisplays(newWidth, newHeight);

                    Log.d(TAG, "Scale changed to: " + desiredScale + "x, Width: " + newWidth + ", Height: " + newHeight);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void addPropertyRow(TableLayout table, String label, String value) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            TableLayout.LayoutParams.WRAP_CONTENT
        ));

        // Label text view
        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setLayoutParams(new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        ));
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setPadding(8, 8, 8, 8);

        // Value text view
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setLayoutParams(new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT,
            1.0f
        ));
        valueView.setPadding(8, 8, 8, 8);

        row.addView(labelView);
        row.addView(valueView);
        table.addView(row);
    }

    private void addPropertyButtonRow(TableLayout table, String label) {
        TableRow row = new TableRow(this);
        row.setLayoutParams(new TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            TableLayout.LayoutParams.WRAP_CONTENT
        ));

        // Label text view
        TextView labelView = new TextView(this);
        labelView.setText(label + ":");
        labelView.setLayoutParams(new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        ));
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setPadding(8, 8, 8, 8);

        // Button to open texture picker
        Button textureButton = new Button(this);
        textureButton.setText("Edit Texture");
        textureButton.setLayoutParams(new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT,
            1.0f
        ));
        textureButton.setPadding(8, 8, 8, 8);
        textureButton.setOnClickListener(v -> openTexturePicker());

        row.addView(labelView);
        row.addView(textureButton);
        table.addView(row);
    }

    private void openTexturePicker() {
        Intent intent = new Intent(this, EditTextureActivity.class);
        intent.putExtra(EditTextureActivity.EXTRA_SPRITE_NAME, currentSprite.getName());
        intent.putExtra(EditTextureActivity.EXTRA_SCENE_FILE_NAME, getIntent().getStringExtra(EXTRA_SCENE_FILE_NAME));
        // Pass current sprite dimensions
        intent.putExtra(EditTextureActivity.EXTRA_WIDTH, currentSprite.getWidth());
        intent.putExtra(EditTextureActivity.EXTRA_HEIGHT, currentSprite.getHeight());

        // Extract current texture state from the sprite's actual texture coordinates
        TextureEditState currentState = currentSprite.getCurrentTextureEditState();
        intent.putExtra(EditTextureActivity.EXTRA_TEXTURE_SCALE, currentState.getTextureScale());
        intent.putExtra(EditTextureActivity.EXTRA_TEXTURE_OFFSET_U, currentState.getTextureOffsetU());
        intent.putExtra(EditTextureActivity.EXTRA_TEXTURE_OFFSET_V, currentState.getTextureOffsetV());

        startActivityForResult(intent, 100); // Request code 100 for texture editing
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && renderer != null) {
            // Pass gyroscope data to the renderer
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
        // Unregister sensor listener
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
        // Register sensor listener
        if (sensorManager != null && gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
            Log.d(TAG, "Gyroscope listener registered");
        }
    }

    private void setupSaveButton() {
        Button saveButton = findViewById(R.id.save_button);
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> showSaveSceneDialog());
        }
    }

    private void showSaveSceneDialog() {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Save Scene");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        // Get the current scene name from the intent
        String currentSceneName = getIntent().getStringExtra(EXTRA_SCENE_FILE_NAME);
        if (currentSceneName != null) {
            // Remove .json extension if present
            if (currentSceneName.endsWith(".json")) {
                currentSceneName = currentSceneName.substring(0, currentSceneName.length() - 5);
            }
            input.setText(currentSceneName);
        }
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                String sceneName = input.getText().toString().trim();
                if (sceneName.isEmpty()) {
                    Toast.makeText(this, "Scene name cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }

                saveSceneToDownloads(sceneName);
                Toast.makeText(this, "Scene saved to Downloads", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error saving scene: " + e.getMessage(), e);
                Toast.makeText(this, "Error saving scene: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveSceneToDownloads(String sceneName) throws Exception {
        if (renderer == null) {
            throw new Exception("Renderer not initialized");
        }

        // Get the current scene
        com.example.livewallpaper.scene.Scene scene = renderer.getCurrentScene();

        // Create SceneData object with current sprite values
        com.example.livewallpaper.scene.SceneData sceneData = new com.example.livewallpaper.scene.SceneData();
        // Don't set sceneName - it will be derived from the filename when loaded
        sceneData.xFocus = scene.getXFocus();

        // Create SpriteData array from current sprites
        java.util.List<com.example.livewallpaper.scene.SpriteData> spriteDatas = new java.util.ArrayList<>();
        for (Sprite sprite : scene.getSprites()) {
            com.example.livewallpaper.scene.SpriteData spriteData = new com.example.livewallpaper.scene.SpriteData();
            spriteData.name = sprite.getName();
            spriteData.textureResource = sprite.getTextureResource();
            spriteData.width = sprite.getWidth();
            spriteData.height = sprite.getHeight();
            spriteData.positionX = sprite.getPositionX();
            spriteData.positionY = sprite.getPositionY();
            spriteData.parallaxMultiplier = sprite.getParallaxMultiplier();
            spriteData.texCoordinates = sprite.getTextureCoordinates();
            spriteDatas.add(spriteData);
        }
        sceneData.sprites = spriteDatas.toArray(new com.example.livewallpaper.scene.SpriteData[0]);

        // Serialize to JSON with pretty printing for readability
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
            .setPrettyPrinting()
            .create();
        String sceneJson = gson.toJson(sceneData);

        // Get Downloads folder
        java.io.File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        );

        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }

        // Create file with .json extension
        String fileName = sceneName.endsWith(".json") ? sceneName : sceneName + ".json";
        java.io.File sceneFile = new java.io.File(downloadsDir, fileName);

        // Write to file
        try (java.io.FileWriter writer = new java.io.FileWriter(sceneFile)) {
            writer.write(sceneJson);
        }

        Log.d(TAG, "Scene saved to: " + sceneFile.getAbsolutePath());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle result from EditTextureActivity (request code 100)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null && currentSprite != null) {
            try {
                // Extract the edited sprite data from the result intent
                String spriteName = data.getStringExtra(EditTextureActivity.RESULT_SPRITE_NAME);
                float width = data.getFloatExtra(EditTextureActivity.RESULT_WIDTH, currentSprite.getWidth());
                float height = data.getFloatExtra(EditTextureActivity.RESULT_HEIGHT, currentSprite.getHeight());
                float textureScale = data.getFloatExtra(EditTextureActivity.RESULT_TEXTURE_SCALE, 1.0f);
                float textureOffsetU = data.getFloatExtra(EditTextureActivity.RESULT_TEXTURE_OFFSET_U, 0f);
                float textureOffsetV = data.getFloatExtra(EditTextureActivity.RESULT_TEXTURE_OFFSET_V, 0f);

                // Apply width and height changes
                currentSprite.setWidth(width);
                currentSprite.setHeight(height);

                // Create TextureEditState with the returned values and apply to sprite
                com.example.livewallpaper.scene.TextureEditState textureState =
                    new com.example.livewallpaper.scene.TextureEditState(textureScale, textureOffsetU, textureOffsetV);
                currentSprite.updateTextureCoordinates(textureState);

                Log.d(TAG, "Applied texture edits from EditTextureActivity to sprite: " + spriteName +
                      " - Width: " + width + ", Height: " + height + ", TextureScale: " + textureScale +
                      ", OffsetU: " + textureOffsetU + ", OffsetV: " + textureOffsetV);

                // Synchronize the scale slider and dimension displays with the new sprite dimensions
                updateScaleSliderDisplay();

                Toast.makeText(this, "Texture changes applied to sprite", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Log.e(TAG, "Error applying texture changes: " + e.getMessage(), e);
                Toast.makeText(this, "Error applying changes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
