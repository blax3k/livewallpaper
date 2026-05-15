package com.hashilab.dev.editor.activities;

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
import com.example.livewallpaper.logging.TimberLog;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.managers.EditSceneManager;
import com.example.livewallpaper.scene.models.Sprite;
import com.example.livewallpaper.scene.models.SpriteData;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.managers.SceneFileManager;
import com.example.livewallpaper.ui.editor.adapters.SpritesDropdownAdapter;
import com.example.livewallpaper.ui.editor.views.SquareGLSurfaceView;
import com.hashilab.dev.editor.controllers.DrawableImagePickerDialog;
import com.example.livewallpaper.ui.editor.builders.SpriteDetailsBuilder;
import com.hashilab.dev.editor.activities.FullscreenPreviewActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EditSceneActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "EditSceneActivity";
    public static final String EXTRA_SCENE_FILE_NAME = "scene_file_name";

    private SquareGLSurfaceView glSurfaceView;
    private EditSceneManager renderer;
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
        TimberLog.d(TAG, "EditSceneActivity onCreate called");

        setContentView(R.layout.activity_edit_scene);
        TimberLog.d(TAG, "Edit scene layout inflated successfully");

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
                TimberLog.d(TAG, "Gyroscope sensor found");
            } else {
                TimberLog.w(TAG, "Gyroscope sensor not available on this device");
            }
        }

        // Set up menu ellipsis button
        setupMenuEllipsis();

        // Set up sprite menu button
        setupSpriteMenuButton();

        // Get the scene file name from the intent
        String sceneFileName = getIntent().getStringExtra(EXTRA_SCENE_FILE_NAME);
        if (sceneFileName != null) {
            TimberLog.d(TAG, "Scene file name: " + sceneFileName);
            displaySceneInfo(sceneFileName);
            setupGLSurfaceView(sceneFileName, propertiesTable, scaleSlider, scaleValue,
                    widthEdit, heightEdit, parallaxMultiplierSlider, parallaxMultiplierValue,
                    focusPointSlider, focusPointValue);
        } else {
            TimberLog.e(TAG, "No scene file name provided!");
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
                renderer = new EditSceneManager(this, sceneFileName);
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

                // Set up the sprite name change listener to refresh the spinner when sprite name changes
                spriteDetailsBuilder.setOnSpriteNameChangeListener((sprite, newName) -> {
                    TimberLog.d(TAG, "Sprite name changed, refreshing sprites list");
                    refreshSpritesListAndSelect(renderer.getAllSprites().indexOf(sprite));
                });

                sceneFileManager = new SceneFileManager(this, renderer);

                // Set up focus point slider
                setupFocusPointSlider(focusPointSlider, focusPointValue);

                TimberLog.d(TAG, "GLSurfaceView configured successfully");

                // Populate sprite list after a short delay to allow scene to load
                new Handler(Looper.getMainLooper()).postDelayed(this::populateSpritesListView, 500);
            } catch (Exception e) {
                TimberLog.e(TAG, "Error setting up GLSurfaceView: " + e.getMessage(), e);
                Toast.makeText(this, "Error setting up preview: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            TimberLog.e(TAG, "GLSurfaceView not found!");
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

                TimberLog.d(TAG, "Sprites spinner populated with " + spriteNames.size() + " items (plus add sprite button)");
            } catch (Exception e) {
                TimberLog.e(TAG, "Error populating sprites spinner: " + e.getMessage(), e);
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
            TimberLog.d(TAG, "Showing details for sprite: " + sprite.getName());
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

            // Get a unique name for the sprite to prevent duplicates in the scene
            String uniqueSpriteName = renderer.getCurrentScene().getUniqueName(imageName);

            // Create a new sprite with default properties (1.0x1.0, at origin, full parallax)
            SpriteData spriteData = new SpriteData();
            spriteData.textureResource = imageName;
            spriteData.textureResourceId = resourceId;
            spriteData.name = uniqueSpriteName;
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

            TimberLog.d(TAG, "Added new sprite: " + imageName);

            // Sort sprites by parallax multiplier since the new sprite might not be in order
            renderer.getCurrentScene().sortSpritesByParallax();
            TimberLog.d(TAG, "Sprites sorted by parallax multiplier");

            // Select the new sprite and update UI to reflect it
            renderer.setSelectedSprite(newSprite);
            showSpriteDetails(newSprite);
            TimberLog.d(TAG, "New sprite selected and details displayed");

            // Refresh the spinner to show the new sprite and update selection
            // Find the index of the new sprite in the scene
            List<Sprite> allSprites = renderer.getAllSprites();
            int newSpriteIndex = -1;
            for (int i = 0; i < allSprites.size(); i++) {
                if (allSprites.get(i) == newSprite) {
                    newSpriteIndex = i;
                    break;
                }
            }

            refreshSpritesListAndSelect(newSpriteIndex);
            TimberLog.d(TAG, "Sprite list refreshed and new sprite selected at index: " + newSpriteIndex);

            Toast.makeText(this, "Sprite '" + imageName + "' added", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            TimberLog.e(TAG, "Error adding new sprite: " + e.getMessage(), e);
            Toast.makeText(this, "Error adding sprite: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Refresh the sprites list in the spinner.
     */
    private void refreshSpritesList() {
        populateSpritesListView();
    }

    /**
     * Refresh the sprites list in the spinner and select a sprite by index.
     *
     * @param spriteIndex the index of the sprite to select after populating the list
     */
    private void refreshSpritesListAndSelect(int spriteIndex) {
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

                TimberLog.d(TAG, "Sprites spinner refreshed with " + spriteNames.size() + " items");

                // Select the sprite at the specified index
                if (spriteIndex >= 0 && spriteIndex < allSprites.size()) {
                    spritesSpinner.setSelection(spriteIndex);
                    TimberLog.d(TAG, "Sprite selected at index: " + spriteIndex);
                }
            } catch (Exception e) {
                TimberLog.e(TAG, "Error refreshing sprites spinner: " + e.getMessage(), e);
                Toast.makeText(this, "Error refreshing sprite list: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openEditTextureActivity() {
        Sprite currentSprite = renderer.getSelectedSprite();
        if (currentSprite == null) {
            Toast.makeText(this, "No sprite selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create sprite data from the current sprite
        // Only pass the texture coordinates - all other texture state will be derived from these
        SpriteData spriteData = new SpriteData();
        spriteData.name = currentSprite.getName();
        spriteData.textureResource = currentSprite.getTextureResource();
        spriteData.textureResourceId = currentSprite.getTextureResourceId();
        spriteData.width = currentSprite.getOriginalWidth();
        spriteData.height = currentSprite.getOriginalHeight();
        spriteData.positionX = currentSprite.getPositionX();
        spriteData.positionY = currentSprite.getPositionY();
        spriteData.parallaxMultiplier = currentSprite.getParallaxMultiplier();
        spriteData.texCoordinates = currentSprite.getOriginalTextureCoordinates();


        Intent intent = new Intent(this, EditTextureActivity.class);
        intent.putExtra(EditTextureActivity.EXTRA_SPRITE_DATA, spriteData);
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
            TimberLog.d(TAG, "Gyroscope listener unregistered");
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
            TimberLog.d(TAG, "Gyroscope listener registered");
        }
    }

    private void setupMenuEllipsis() {
        ImageButton ellipsisButton = findViewById(R.id.menu_ellipsis_button);
        if (ellipsisButton != null) {
            ellipsisButton.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(EditSceneActivity.this, v);
                popupMenu.getMenuInflater().inflate(R.menu.menu_edit_scene, popupMenu.getMenu());

                // Set the checkbox state based on current gyro motion status
                popupMenu.getMenu().findItem(R.id.menu_enable_gyro).setChecked(MotionConfig.isGyroMotionEnabled());

                popupMenu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.menu_preview) {
                        startFullscreenPreview();
                        return true;
                    } else if (item.getItemId() == R.id.menu_set_time) {
                        showSetTimeDialog();
                        return true;
                    } else if (item.getItemId() == R.id.menu_save) {
                        String currentSceneName = getIntent().getStringExtra(EXTRA_SCENE_FILE_NAME);
                        // Pass a callback to be executed after successful save
                        sceneFileManager.showSaveDialog(currentSceneName, () -> {
                            // Mark that a save occurred so SceneListActivity can refresh
                            setResult(RESULT_OK);
                        });
                        return true;
                    } else if (item.getItemId() == R.id.menu_enable_gyro) {
                        // Toggle the gyro motion enabled state
                        boolean isCurrentlyEnabled = MotionConfig.isGyroMotionEnabled();
                        MotionConfig.setGyroMotionEnabled(!isCurrentlyEnabled);
                        item.setChecked(!isCurrentlyEnabled);
                        Toast.makeText(EditSceneActivity.this,
                                "Gyro motion " + (!isCurrentlyEnabled ? "enabled" : "disabled"),
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    return false;
                });
                popupMenu.show();
            });
        }
    }

    private void startFullscreenPreview() {
        if (renderer == null || renderer.getCurrentScene() == null) {
            Toast.makeText(this, "Error: Scene not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, FullscreenPreviewActivity.class);
        // Pass the current scene data instead of loading from file
        // This ensures the preview shows all current edits
        intent.putExtra(FullscreenPreviewActivity.EXTRA_SCENE_DATA, renderer.getCurrentScene());
        startActivity(intent);
    }

    private void setupSpriteMenuButton() {
        ImageButton spriteMenuButton = findViewById(R.id.sprite_menu_button);
        if (spriteMenuButton != null) {
            spriteMenuButton.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(EditSceneActivity.this, v);
                popupMenu.getMenuInflater().inflate(R.menu.menu_sprite_options, popupMenu.getMenu());

                popupMenu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.menu_sprite_edit_name) {
                        showSpriteNameEditDialog();
                        return true;
                    } else if (item.getItemId() == R.id.menu_sprite_edit_texture) {
                        openEditTextureActivity();
                        return true;
                    } else if (item.getItemId() == R.id.menu_sprite_delete) {
                        showDeleteSpriteConfirmDialog();
                        return true;
                    }
                    return false;
                });
                popupMenu.show();
            });
        }
    }

    private void showDeleteSpriteConfirmDialog() {
        Sprite currentSprite = renderer != null ? renderer.getSelectedSprite() : null;
        if (currentSprite == null) {
            Toast.makeText(this, "No sprite selected", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Sprite");
        builder.setMessage("Are you sure you want to delete \"" + currentSprite.getName() + "\"?");

        builder.setPositiveButton("Continue", (dialog, which) -> {
            try {
                // Delete the sprite from the scene
                renderer.deleteSpriteFromScene(currentSprite);

                // Hide sprite details container
                if (spriteDetailsContainer != null) {
                    spriteDetailsContainer.setVisibility(View.GONE);
                }

                // Refresh the sprites list
                refreshSpritesList();

                Toast.makeText(EditSceneActivity.this,
                        "Sprite \"" + currentSprite.getName() + "\" deleted",
                        Toast.LENGTH_SHORT).show();

                TimberLog.d(TAG, "Sprite deleted: " + currentSprite.getName());
            } catch (Exception e) {
                TimberLog.e(TAG, "Error deleting sprite: " + e.getMessage(), e);
                Toast.makeText(EditSceneActivity.this,
                        "Error deleting sprite: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            Sprite currentSprite = renderer.getSelectedSprite();
            if (currentSprite == null) {
                TimberLog.e(TAG, "No sprite selected when activity result returned");
                return;
            }

            try {
                String spriteName = data.getStringExtra(EditTextureActivity.RESULT_SPRITE_NAME);
                float width = data.getFloatExtra(EditTextureActivity.RESULT_WIDTH, currentSprite.getOriginalWidth());
                float height = data.getFloatExtra(EditTextureActivity.RESULT_HEIGHT, currentSprite.getOriginalHeight());
                float[] texCoordinates = data.getFloatArrayExtra(EditTextureActivity.RESULT_TEXTURE_COORDINATES);
                String textureResource = data.getStringExtra(EditTextureActivity.RESULT_TEXTURE_RESOURCE);
                int textureResourceId = data.getIntExtra(EditTextureActivity.RESULT_TEXTURE_RESOURCE_ID, currentSprite.getTextureResourceId());

                TimberLog.d(TAG, "Received texture edits from EditTextureActivity - texCoordinates updated");

                // Update sprite current dimensions
                renderer.updateSpriteDimensions(currentSprite, width, height);

                // Update the original dimensions to match the new current dimensions
                // This ensures the updated dimensions are saved to JSON correctly
                currentSprite.setWidthAndUpdateOriginal(width);
                currentSprite.setHeightAndUpdateOriginal(height);
                TimberLog.d(TAG, "Updated sprite original dimensions to: " + width + " x " + height);

                // Update texture resource information if provided
                if (textureResource != null) {
                    currentSprite.setTextureResource(textureResource);
                    currentSprite.setTextureResourceId(textureResourceId);
                    TimberLog.d(TAG, "Updated sprite texture resource to: " + textureResource + " (resourceId=" + textureResourceId + ")");
                }

                // Update texture coordinates - this is the ONLY texture state that matters
                if (texCoordinates != null && texCoordinates.length == 8) {
                    currentSprite.setTextureCoordinates(texCoordinates);
                    TimberLog.d(TAG, "Updated sprite texture coordinates");
                }

                TimberLog.d(TAG, "Applied texture edits from EditTextureActivity to sprite: " + spriteName);

                spriteDetailsBuilder.updateDimensionDisplays(width, height);
                spriteDetailsBuilder.updateScaleDisplay(currentSprite);

                Toast.makeText(this, "Texture changes applied to sprite", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                TimberLog.e(TAG, "Error applying texture changes: " + e.getMessage(), e);
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
     * Show dialog to edit the selected sprite's name.
     */
    private void showSpriteNameEditDialog() {
        Sprite currentSprite = renderer != null ? renderer.getSelectedSprite() : null;
        if (currentSprite == null) {
            Toast.makeText(this, "No sprite selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (spriteDetailsBuilder != null) {
            spriteDetailsBuilder.showNameEditDialog(currentSprite);
        }
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
     * Show dialog to set the available time range for the scene using HH:MM pickers.
     * Times are stored as minutes-of-day (0–1439). For overnight ranges, set start after end
     * (e.g. start=22:00, end=06:00).
     */
    private void showSetTimeDialog() {
        if (renderer == null || renderer.getCurrentScene() == null) {
            Toast.makeText(this, "Error: Scene not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        com.example.livewallpaper.scene.models.Scene currentScene = renderer.getCurrentScene();
        int currentStart = currentScene.getStartTime();
        int currentEnd = currentScene.getEndTime();

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int dp8 = (int)(8 * getResources().getDisplayMetrics().density);
        int dp16 = dp8 * 2;
        layout.setPadding(dp16, dp16, dp16, dp8);

        // --- Start time row ---
        android.widget.TextView startLabel = new android.widget.TextView(this);
        startLabel.setText("Start time:");
        layout.addView(startLabel);

        android.widget.LinearLayout startRow = new android.widget.LinearLayout(this);
        startRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        startRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final android.widget.NumberPicker startHourPicker = new android.widget.NumberPicker(this);
        startHourPicker.setMinValue(0);
        startHourPicker.setMaxValue(23);
        startHourPicker.setValue(currentStart / 60);
        startHourPicker.setFormatter(value -> String.format(Locale.US, "%02d", value));

        android.widget.TextView colonStart = new android.widget.TextView(this);
        colonStart.setText(":");
        colonStart.setPadding(dp8, 0, dp8, 0);

        final android.widget.NumberPicker startMinutePicker = new android.widget.NumberPicker(this);
        startMinutePicker.setMinValue(0);
        startMinutePicker.setMaxValue(59);
        startMinutePicker.setValue(currentStart % 60);
        startMinutePicker.setFormatter(value -> String.format(Locale.US, "%02d", value));

        startRow.addView(startHourPicker);
        startRow.addView(colonStart);
        startRow.addView(startMinutePicker);
        layout.addView(startRow);

        // --- End time row ---
        android.widget.TextView endLabel = new android.widget.TextView(this);
        endLabel.setText("End time (inclusive):");
        android.widget.LinearLayout.LayoutParams endLabelParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        endLabelParams.topMargin = dp8;
        endLabel.setLayoutParams(endLabelParams);
        layout.addView(endLabel);

        android.widget.LinearLayout endRow = new android.widget.LinearLayout(this);
        endRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        endRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final android.widget.NumberPicker endHourPicker = new android.widget.NumberPicker(this);
        endHourPicker.setMinValue(0);
        endHourPicker.setMaxValue(23);
        endHourPicker.setValue(currentEnd / 60);
        endHourPicker.setFormatter(value -> String.format(Locale.US, "%02d", value));

        android.widget.TextView colonEnd = new android.widget.TextView(this);
        colonEnd.setText(":");
        colonEnd.setPadding(dp8, 0, dp8, 0);

        final android.widget.NumberPicker endMinutePicker = new android.widget.NumberPicker(this);
        endMinutePicker.setMinValue(0);
        endMinutePicker.setMaxValue(59);
        endMinutePicker.setValue(currentEnd % 60);
        endMinutePicker.setFormatter(value -> String.format(Locale.US, "%02d", value));

        endRow.addView(endHourPicker);
        endRow.addView(colonEnd);
        endRow.addView(endMinutePicker);
        layout.addView(endRow);

        // Hint
        android.widget.TextView hint = new android.widget.TextView(this);
        hint.setText("Tip: for overnight (e.g. 22:00 to 06:00), set start time after end time.");
        hint.setTextSize(11f);
        android.widget.LinearLayout.LayoutParams hintParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp8;
        hint.setLayoutParams(hintParams);
        layout.addView(hint);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Available Time Range");
        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            int newStart = startHourPicker.getValue() * 60 + startMinutePicker.getValue();
            int newEnd = endHourPicker.getValue() * 60 + endMinutePicker.getValue();

            currentScene.setStartTime(newStart);
            currentScene.setEndTime(newEnd);

            String startStr = String.format(Locale.US, "%02d:%02d", newStart / 60, newStart % 60);
            String endStr = String.format(Locale.US, "%02d:%02d", newEnd / 60, newEnd % 60);
            TimberLog.d(TAG, "Scene time range changed to: " + startStr + " – " + endStr);
            Toast.makeText(this, "Time range set to " + startStr + " – " + endStr, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Set up the focus point slider to initialize with the scene's focus point and handle changes.
     * Focus point ranges from 0.0 (left) to 1.0 (right), displayed as 0-100 on the slider.
     */
    private void setupFocusPointSlider(SeekBar focusPointSlider, TextView focusPointValue) {
        if (focusPointSlider == null || focusPointValue == null || renderer == null) {
            TimberLog.w(TAG, "Focus point slider not properly initialized");
            return;
        }

        // Initialize the slider with the scene's current focus point
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                float currentFocusPoint = renderer.getCurrentScene().getXFocus();
                int sliderPosition = (int) (currentFocusPoint * 100);
                focusPointSlider.setProgress(sliderPosition);
                updateFocusPointDisplay(focusPointValue, currentFocusPoint);
                TimberLog.d(TAG, "Focus point slider initialized with value: " + currentFocusPoint);
            } catch (Exception e) {
                TimberLog.e(TAG, "Error initializing focus point slider: " + e.getMessage(), e);
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
                    TimberLog.d(TAG, "Focus point changed to: " + newFocusPoint);

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
