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
    private LinearLayout spritesListContainer;
    private ScrollView spriteDetailsContainer;
    private Sprite currentSprite;
    private SeekBar positionXSlider;
    private TextView positionXValue;
    private SeekBar positionYSlider;
    private TextView positionYValue;
    private SeekBar widthSlider;
    private TextView widthValue;
    private SeekBar heightSlider;
    private TextView heightValue;
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
        spritesListContainer = findViewById(R.id.sprites_list_container);
        spriteDetailsContainer = findViewById(R.id.sprite_details_container);
        positionXSlider = findViewById(R.id.position_x_slider);
        positionXValue = findViewById(R.id.position_x_value);
        positionYSlider = findViewById(R.id.position_y_slider);
        positionYValue = findViewById(R.id.position_y_value);
        widthSlider = findViewById(R.id.width_slider);
        widthValue = findViewById(R.id.width_value);
        heightSlider = findViewById(R.id.height_slider);
        heightValue = findViewById(R.id.height_value);
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
                positionYSlider, positionYValue, -5f, 5f, 0.1f, "Position Y",
                v -> currentSprite.setPositionY(v),
                () -> currentSprite.getPositionY()
            ));

            setupGenericSlider(new SliderConfig(
                widthSlider, widthValue, 0.5f, 20f, 0.1f, "Width",
                v -> currentSprite.setWidth(v),
                () -> currentSprite.getWidth()
            ));

            setupGenericSlider(new SliderConfig(
                heightSlider, heightValue, 0.5f, 20f, 0.1f, "Height",
                v -> currentSprite.setHeight(v),
                () -> currentSprite.getHeight()
            ));

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
            spriteData.textureResourceId = sprite.getTextureResourceId();
            spriteData.width = sprite.getWidth();
            spriteData.height = sprite.getHeight();
            spriteData.positionX = sprite.getPositionX();
            spriteData.positionY = sprite.getPositionY();
            spriteData.parallaxMultiplier = sprite.getParallaxMultiplier();
            spriteDatas.add(spriteData);
        }
        sceneData.sprites = spriteDatas.toArray(new com.example.livewallpaper.scene.SpriteData[0]);

        // Serialize to JSON
        com.google.gson.Gson gson = new com.google.gson.Gson();
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
                float textureScale = data.getFloatExtra(EditTextureActivity.RESULT_TEXTURE_SCALE, currentSprite.getTextureScale());
                float textureOffsetU = data.getFloatExtra(EditTextureActivity.RESULT_TEXTURE_OFFSET_U, 0f);
                float textureOffsetV = data.getFloatExtra(EditTextureActivity.RESULT_TEXTURE_OFFSET_V, 0f);

                // Apply the changes to the current sprite
                currentSprite.setWidth(width);
                currentSprite.setHeight(height);
                currentSprite.setTextureScale(textureScale);
                currentSprite.setTextureOffsetU(textureOffsetU);
                currentSprite.setTextureOffsetV(textureOffsetV);

                Log.d(TAG, "Applied texture edits from EditTextureActivity to sprite: " + spriteName +
                      " - Width: " + width + ", Height: " + height + ", TextureScale: " + textureScale +
                      ", OffsetU: " + textureOffsetU + ", OffsetV: " + textureOffsetV);

                Toast.makeText(this, "Texture changes applied to sprite", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Log.e(TAG, "Error applying texture changes: " + e.getMessage(), e);
                Toast.makeText(this, "Error applying changes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
