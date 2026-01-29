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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.Sprite;
import com.example.livewallpaper.scene.TextureEditState;

import java.util.ArrayList;
import java.util.List;

public class EditSceneActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "EditSceneActivity";
    public static final String EXTRA_SCENE_FILE_NAME = "scene_file_name";

    private SquareGLSurfaceView glSurfaceView;
    private ScenePreviewRenderer renderer;
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private Spinner spritesSpinner;
    private ScrollView spriteDetailsContainer;

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

        SeekBar positionXSlider = findViewById(R.id.position_x_slider);
        TextView positionXValue = findViewById(R.id.position_x_value);
        SeekBar positionYSlider = findViewById(R.id.position_y_slider);
        TextView positionYValue = findViewById(R.id.position_y_value);
        SeekBar scaleSlider = findViewById(R.id.scale_slider);
        TextView scaleValue = findViewById(R.id.scale_value);
        EditText widthEdit = findViewById(R.id.width_edit);
        EditText heightEdit = findViewById(R.id.height_edit);
        SeekBar parallaxMultiplierSlider = findViewById(R.id.parallax_multiplier_slider);
        TextView parallaxMultiplierValue = findViewById(R.id.parallax_multiplier_value);
        TableLayout propertiesTable = findViewById(R.id.sprite_properties_table);

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
            setupGLSurfaceView(sceneFileName, propertiesTable, positionXSlider, positionXValue,
                    positionYSlider, positionYValue, scaleSlider, scaleValue,
                    widthEdit, heightEdit, parallaxMultiplierSlider, parallaxMultiplierValue);
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
                                    SeekBar positionXSlider, TextView positionXValue,
                                    SeekBar positionYSlider, TextView positionYValue,
                                    SeekBar scaleSlider, TextView scaleValue,
                                    EditText widthEdit, EditText heightEdit,
                                    SeekBar parallaxMultiplierSlider, TextView parallaxMultiplierValue) {
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

                // Initialize helper classes
                spriteDetailsBuilder = new SpriteDetailsBuilder(this, renderer, propertiesTable,
                        positionXSlider, positionXValue, positionYSlider, positionYValue,
                        scaleSlider, scaleValue, widthEdit, heightEdit,
                        parallaxMultiplierSlider, parallaxMultiplierValue);
                sceneFileManager = new SceneFileManager(this, renderer);

                // Set up texture button listener
                spriteDetailsBuilder.setTextureButtonListener(v -> openEditTextureActivity());

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
                        Sprite selectedSprite = renderer.selectSpriteByIndex(position);
                        if (selectedSprite != null) {
                            showSpriteDetails(selectedSprite);
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
        if (sprite == null || spriteDetailsBuilder == null) {
            return;
        }

        spriteDetailsBuilder.build(sprite);

        if (spriteDetailsContainer != null) {
            spriteDetailsContainer.setVisibility(View.VISIBLE);
            Log.d(TAG, "Showing details for sprite: " + sprite.getName());
        }
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
}
