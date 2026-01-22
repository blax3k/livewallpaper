package com.example.livewallpaper.ui;

import android.content.Context;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.Sprite;

import java.util.ArrayList;
import java.util.List;

public class EditSceneActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "EditSceneActivity";
    public static final String EXTRA_SCENE_FILE_NAME = "scene_file_name";
    private SquareGLSurfaceView glSurfaceView;
    private ScenePreviewRenderer renderer;
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private List<Sprite> allSprites;
    private LinearLayout spritesListContainer;
    private LinearLayout spriteDetailsContainer;
    private Button backToListButton;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "EditSceneActivity onCreate called");

        setContentView(R.layout.activity_edit_scene);
        Log.d(TAG, "Edit scene layout inflated successfully");

        // Initialize view references
        spritesListContainer = findViewById(R.id.sprites_list_container);
        spriteDetailsContainer = findViewById(R.id.sprite_details_container);
        backToListButton = findViewById(R.id.back_to_list_button);
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

        // Set up back button
        if (backToListButton != null) {
            backToListButton.setOnClickListener(v -> showSpritesList());
        }

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
        ListView spritesList = findViewById(R.id.sprites_list_view);
        if (spritesList != null && renderer != null) {
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
                    android.R.layout.simple_list_item_1,
                    spriteNames
                );
                spritesList.setAdapter(adapter);

                // Set up item click listener
                spritesList.setOnItemClickListener((parent, view, position, id) -> {
                    if (position < allSprites.size()) {
                        showSpriteDetails(allSprites.get(position));
                    }
                });

                Log.d(TAG, "Sprites list populated with " + spriteNames.size() + " items");
            } catch (Exception e) {
                Log.e(TAG, "Error populating sprites list: " + e.getMessage(), e);
                Toast.makeText(this, "Error loading sprite list: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showSpriteDetails(Sprite sprite) {
        currentSprite = sprite;
        TableLayout propertiesTable = findViewById(R.id.sprite_properties_table);
        if (propertiesTable != null) {
            propertiesTable.removeAllViews();

            // Add only read-only properties to the table
            addPropertyRow(propertiesTable, "Name", sprite.getName());
            addPropertyRow(propertiesTable, "Texture Resource", sprite.getTextureResource());

            // Set up all editable sliders
            setupPositionXSlider(sprite);
            setupPositionYSlider(sprite);
            setupWidthSlider(sprite);
            setupHeightSlider(sprite);
            setupParallaxMultiplierSlider(sprite);

            // Switch to details view
            if (spritesListContainer != null && spriteDetailsContainer != null) {
                spritesListContainer.setVisibility(View.GONE);
                spriteDetailsContainer.setVisibility(View.VISIBLE);
                Log.d(TAG, "Showing details for sprite: " + sprite.getName());
            }
        }
    }

    private void setupPositionXSlider(Sprite sprite) {
        if (positionXSlider != null && positionXValue != null) {
            // Remove any existing listener to avoid feedback loops
            positionXSlider.setOnSeekBarChangeListener(null);

            // Calculate slider value: range is -15 to 15, with 0.25 increments
            // Max progress = (15 - (-15)) / 0.25 = 30 / 0.25 = 120
            float currentX = sprite.getPositionX();
            int sliderProgress = (int) ((currentX + 15f) / 0.25f);
            positionXSlider.setProgress(sliderProgress);
            positionXValue.setText(String.format("%.2f", currentX));

            // Set up click listener for manual editing
            positionXValue.setOnClickListener(v -> showPositionXEditDialog(sprite));

            // Set up listener for slider changes
            positionXSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && currentSprite != null) {
                        // Convert progress back to position value
                        float newX = -15f + (progress * 0.25f);
                        currentSprite.setPositionX(newX);
                        positionXValue.setText(String.format("%.2f", newX));
                        Log.d(TAG, "Position X updated to: " + newX);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // No action needed
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // No action needed
                }
            });
        }
    }

    private void showPositionXEditDialog(Sprite sprite) {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Position X");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format("%.2f", sprite.getPositionX()));
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                float newValue = Float.parseFloat(input.getText().toString());
                // Clamp value to range -15 to 15
                if (newValue < -15f) newValue = -15f;
                if (newValue > 15f) newValue = 15f;

                // Update sprite
                sprite.setPositionX(newValue);
                positionXValue.setText(String.format("%.2f", newValue));

                // Update slider to reflect new value
                if (positionXSlider != null) {
                    int newProgress = (int) ((newValue + 15f) / 0.25f);
                    positionXSlider.setProgress(newProgress);
                }

                Log.d(TAG, "Position X manually edited to: " + newValue);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Invalid number entered for Position X: " + e.getMessage());
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void setupPositionYSlider(Sprite sprite) {
        if (positionYSlider != null && positionYValue != null) {
            positionYSlider.setOnSeekBarChangeListener(null);

            // Range: -5 to 5, with 0.1 increments
            // Max progress = (5 - (-5)) / 0.1 = 100
            float currentY = sprite.getPositionY();
            int sliderProgress = (int) ((currentY + 5f) / 0.1f);
            positionYSlider.setProgress(sliderProgress);
            positionYValue.setText(String.format("%.2f", currentY));

            positionYValue.setOnClickListener(v -> showPositionYEditDialog(sprite));

            positionYSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && currentSprite != null) {
                        float newY = -5f + (progress * 0.1f);
                        currentSprite.setPositionY(newY);
                        positionYValue.setText(String.format("%.2f", newY));
                        Log.d(TAG, "Position Y updated to: " + newY);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void showPositionYEditDialog(Sprite sprite) {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Position Y");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format("%.2f", sprite.getPositionY()));
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                float newValue = Float.parseFloat(input.getText().toString());
                if (newValue < -5f) newValue = -5f;
                if (newValue > 5f) newValue = 5f;

                sprite.setPositionY(newValue);
                positionYValue.setText(String.format("%.2f", newValue));

                if (positionYSlider != null) {
                    int newProgress = (int) ((newValue + 5f) / 0.1f);
                    positionYSlider.setProgress(newProgress);
                }

                Log.d(TAG, "Position Y manually edited to: " + newValue);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Invalid number entered for Position Y: " + e.getMessage());
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void setupWidthSlider(Sprite sprite) {
        if (widthSlider != null && widthValue != null) {
            widthSlider.setOnSeekBarChangeListener(null);

            // Range: 0.5 to 20, with 0.1 increments
            // Max progress = (20 - 0.5) / 0.1 = 195, clamped to 200
            float currentWidth = sprite.getWidth();
            int sliderProgress = (int) ((currentWidth - 0.5f) / 0.1f);
            widthSlider.setProgress(sliderProgress);
            widthValue.setText(String.format("%.2f", currentWidth));

            widthValue.setOnClickListener(v -> showWidthEditDialog(sprite));

            widthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && currentSprite != null) {
                        float newWidth = 0.5f + (progress * 0.1f);
                        currentSprite.setWidth(newWidth);
                        widthValue.setText(String.format("%.2f", newWidth));
                        Log.d(TAG, "Width updated to: " + newWidth);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void showWidthEditDialog(Sprite sprite) {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Width");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format("%.2f", sprite.getWidth()));
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                float newValue = Float.parseFloat(input.getText().toString());
                if (newValue < 0.5f) newValue = 0.5f;
                if (newValue > 20f) newValue = 20f;

                sprite.setWidth(newValue);
                widthValue.setText(String.format("%.2f", newValue));

                if (widthSlider != null) {
                    int newProgress = (int) ((newValue - 0.5f) / 0.1f);
                    widthSlider.setProgress(newProgress);
                }

                Log.d(TAG, "Width manually edited to: " + newValue);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Invalid number entered for Width: " + e.getMessage());
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void setupHeightSlider(Sprite sprite) {
        if (heightSlider != null && heightValue != null) {
            heightSlider.setOnSeekBarChangeListener(null);

            // Range: 0.5 to 20, with 0.1 increments
            float currentHeight = sprite.getHeight();
            int sliderProgress = (int) ((currentHeight - 0.5f) / 0.1f);
            heightSlider.setProgress(sliderProgress);
            heightValue.setText(String.format("%.2f", currentHeight));

            heightValue.setOnClickListener(v -> showHeightEditDialog(sprite));

            heightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && currentSprite != null) {
                        float newHeight = 0.5f + (progress * 0.1f);
                        currentSprite.setHeight(newHeight);
                        heightValue.setText(String.format("%.2f", newHeight));
                        Log.d(TAG, "Height updated to: " + newHeight);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void showHeightEditDialog(Sprite sprite) {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Height");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format("%.2f", sprite.getHeight()));
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                float newValue = Float.parseFloat(input.getText().toString());
                if (newValue < 0.5f) newValue = 0.5f;
                if (newValue > 20f) newValue = 20f;

                sprite.setHeight(newValue);
                heightValue.setText(String.format("%.2f", newValue));

                if (heightSlider != null) {
                    int newProgress = (int) ((newValue - 0.5f) / 0.1f);
                    heightSlider.setProgress(newProgress);
                }

                Log.d(TAG, "Height manually edited to: " + newValue);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Invalid number entered for Height: " + e.getMessage());
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void setupParallaxMultiplierSlider(Sprite sprite) {
        if (parallaxMultiplierSlider != null && parallaxMultiplierValue != null) {
            parallaxMultiplierSlider.setOnSeekBarChangeListener(null);

            // Range: 0.1 to 2.0, with 0.1 increments
            // Max progress = (2.0 - 0.1) / 0.1 = 19, clamped to 200 for better granularity
            // Actually use: (2.0 - 0.1) / 0.01 = 190 for finer control
            float currentMultiplier = sprite.getParallaxMultiplier();
            int sliderProgress = (int) ((currentMultiplier - 0.1f) / 0.01f);
            parallaxMultiplierSlider.setProgress(sliderProgress);
            parallaxMultiplierSlider.setEnabled(true);
            parallaxMultiplierValue.setText(String.format("%.2f", currentMultiplier));

            parallaxMultiplierValue.setOnClickListener(v -> showParallaxMultiplierEditDialog(sprite));

            parallaxMultiplierSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && currentSprite != null) {
                        float newMultiplier = 0.1f + (progress * 0.01f);
                        currentSprite.setParallaxMultiplier(newMultiplier);
                        parallaxMultiplierValue.setText(String.format("%.2f", newMultiplier));
                        Log.d(TAG, "Parallax Multiplier updated to: " + newMultiplier);

                        // Request re-sort on GL thread to avoid ConcurrentModificationException
                        if (renderer != null) {
                            renderer.requestSpriteResort();
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void showParallaxMultiplierEditDialog(Sprite sprite) {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Parallax Multiplier");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format("%.2f", sprite.getParallaxMultiplier()));
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                float newValue = Float.parseFloat(input.getText().toString());
                if (newValue < 0.1f) newValue = 0.1f;
                if (newValue > 2.0f) newValue = 2.0f;

                sprite.setParallaxMultiplier(newValue);
                parallaxMultiplierValue.setText(String.format("%.2f", newValue));

                if (parallaxMultiplierSlider != null) {
                    int newProgress = (int) ((newValue - 0.1f) / 0.01f);
                    parallaxMultiplierSlider.setProgress(newProgress);
                }

                // Request re-sort on GL thread to avoid ConcurrentModificationException
                if (renderer != null) {
                    renderer.requestSpriteResort();
                }

                Log.d(TAG, "Parallax Multiplier manually edited to: " + newValue);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Invalid number entered for Parallax Multiplier: " + e.getMessage());
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

    private void showSpritesList() {
        if (spritesListContainer != null && spriteDetailsContainer != null) {
            spriteDetailsContainer.setVisibility(View.GONE);
            spritesListContainer.setVisibility(View.VISIBLE);
            Log.d(TAG, "Showing sprites list");
        }
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
}
