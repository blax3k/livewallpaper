package com.example.livewallpaper.ui.builders;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TextView;

import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.managers.BaseSceneManager;
import com.example.livewallpaper.scene.models.Sprite;
import com.example.livewallpaper.ui.controllers.DimensionController;
import com.example.livewallpaper.ui.controllers.ScaleSliderController;
import com.example.livewallpaper.ui.controllers.SliderController;

import java.util.Locale;

/**
 * Builds and manages the sprite details UI.
 * Handles property row creation and sprite property editing setup.
 */
public class SpriteDetailsBuilder {
    private static final String TAG = "SpriteDetailsBuilder";

    /**
     * Interface for callbacks when sprite name changes.
     */
    public interface OnSpriteNameChangeListener {
        void onSpriteNameChanged(Sprite sprite, String newName);
    }

    private final Activity activity;
    private final BaseSceneManager renderer;
    private final TableLayout propertiesTable;
    private final SliderController sliderManager;
    private final ScaleSliderController scaleController;
    private final DimensionController dimensionController;
    private final SeekBar positionXSlider;
    private final TextView positionXValue;
    private final SeekBar positionYSlider;
    private final TextView positionYValue;
    private final SeekBar parallaxMultiplierSlider;
    private final TextView parallaxMultiplierValue;
    private OnSpriteNameChangeListener onSpriteNameChangeListener;

    public SpriteDetailsBuilder(Activity activity, BaseSceneManager renderer,
                               TableLayout propertiesTable,
                               SeekBar positionXSlider, TextView positionXValue,
                               SeekBar positionYSlider, TextView positionYValue,
                               SeekBar scaleSlider, TextView scaleValue,
                               EditText widthEdit, EditText heightEdit,
                               SeekBar parallaxMultiplierSlider, TextView parallaxMultiplierValue) {
        this.activity = activity;
        this.renderer = renderer;
        this.propertiesTable = propertiesTable;
        this.positionXSlider = positionXSlider;
        this.positionXValue = positionXValue;
        this.positionYSlider = positionYSlider;
        this.positionYValue = positionYValue;
        this.parallaxMultiplierSlider = parallaxMultiplierSlider;
        this.parallaxMultiplierValue = parallaxMultiplierValue;

        // Initialize helper controllers
        this.sliderManager = new SliderController(activity);
        this.scaleController = new ScaleSliderController(activity, renderer, scaleSlider, scaleValue);
        this.dimensionController = new DimensionController(renderer, widthEdit, heightEdit);
    }

    /**
     * Set the listener for sprite name change events.
     *
     * @param listener the listener to be called when sprite name changes
     */
    public void setOnSpriteNameChangeListener(OnSpriteNameChangeListener listener) {
        this.onSpriteNameChangeListener = listener;
    }

    /**
     * Build and display sprite details in the properties table.
     */
    public void build(Sprite sprite) {
        if (sprite == null || propertiesTable == null) {
            return;
        }

        // Sync with renderer - ensure sprite is selected and highlighted
        renderer.setSelectedSprite(sprite);

        propertiesTable.removeAllViews();

        // Set up position displays and sliders (if sliders exist)
        if (positionXSlider != null) {
            sliderManager.setupSlider(new SliderController.SliderConfig(
                positionXSlider, positionXValue, -15f, 15f, 0.25f, "Position X",
                v -> renderer.updateSpritePosition(sprite, v, sprite.getPositionY()),
                sprite::getPositionX
            ));

            sliderManager.setupSlider(new SliderController.SliderConfig(
                positionYSlider, positionYValue, -15f, 15f, 0.25f, "Position Y",
                v -> renderer.updateSpritePosition(sprite, sprite.getPositionX(), v),
                sprite::getPositionY
            ));
        } else {
            // No sliders - just update the text displays
            if (positionXValue != null) {
                positionXValue.setText(String.format(Locale.US, "%.2f", sprite.getPositionX()));
            }
            if (positionYValue != null) {
                positionYValue.setText(String.format(Locale.US, "%.2f", sprite.getPositionY()));
            }
        }

        // Set up scale slider
        float aspectRatio = renderer.calculateAspectRatio(sprite);
        scaleController.setup(sprite);

        // Set up dimension fields and connect scale slider to dimension updates
        dimensionController.setup(sprite, aspectRatio);
        scaleController.setOnDimensionChangeCallback(() ->
            dimensionController.updateDisplays(sprite.getOriginalWidth(), sprite.getOriginalHeight())
        );

        // Set up parallax multiplier slider
        sliderManager.setupSlider(new SliderController.SliderConfig(
            parallaxMultiplierSlider, parallaxMultiplierValue, 0.1f, 2.0f, 0.01f, "Parallax Multiplier",
            v -> renderer.updateSpriteParallax(sprite, v),
            sprite::getParallaxMultiplier,
            () -> { if (renderer != null) renderer.requestSpriteResort(); },
            true
        ));

        Log.d(TAG, "Built details for sprite: " + sprite.getName());
    }

    /**
     * Update dimension displays when sprite is modified externally.
     */
    public void updateDimensionDisplays(float width, float height) {
        dimensionController.updateDisplays(width, height);
    }

    /**
     * Update scale slider display when sprite is modified externally.
     */
    public void updateScaleDisplay(Sprite sprite) {
        scaleController.updateDisplay(sprite);
    }

    /**
     * Show a modal dialog to edit the sprite name.
     * Public method so it can be called from EditSceneActivity menu.
     *
     * @param sprite the sprite being renamed
     */
    public void showNameEditDialog(Sprite sprite) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Edit Sprite Name");

        // Create a container for the EditText and error message
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16, 16, 16, 16);

        // EditText for name input
        EditText nameInput = new EditText(activity);
        nameInput.setText(sprite.getName());
        nameInput.setSingleLine(true);
        nameInput.selectAll();
        LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        editTextParams.bottomMargin = 8;
        nameInput.setLayoutParams(editTextParams);
        container.addView(nameInput);

        // Error message TextView (initially hidden)
        TextView errorMessage = new TextView(activity);
        errorMessage.setTextColor(android.graphics.Color.RED);
        errorMessage.setTextSize(12);
        errorMessage.setVisibility(android.view.View.GONE);
        container.addView(errorMessage);

        builder.setView(container);

        // Create dialog but don't show it yet (we need to set up button listeners)
        AlertDialog dialog = builder.create();

        // Set up text change listener for real-time validation
        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateNameInput(s.toString().trim(), sprite, errorMessage, dialog);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Add Save button
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Save", (dialogInterface, which) -> {
            String newName = nameInput.getText().toString().trim();
            handleNameChange(sprite, newName);
            dialog.dismiss();
        });

        // Add Cancel button
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", (dialogInterface, which) -> {
            dialog.dismiss();
        });

        dialog.show();

        // Perform initial validation and disable Save button if needed
        validateNameInput(nameInput.getText().toString().trim(), sprite, errorMessage, dialog);
    }

    /**
     * Validate the name input and update error message and button state.
     *
     * @param newName the proposed sprite name
     * @param sprite the current sprite
     * @param errorMessage the TextView to display error messages
     * @param dialog the dialog containing the Save button
     */
    private void validateNameInput(String newName, Sprite sprite, TextView errorMessage, AlertDialog dialog) {
        Button saveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);

        // Check if name is empty
        if (newName.isEmpty()) {
            errorMessage.setText(R.string.sprite_name_empty_error);
            errorMessage.setVisibility(android.view.View.VISIBLE);
            saveButton.setEnabled(false);
            return;
        }

        // Check if name is the same as current name (this is allowed)
        if (newName.equals(sprite.getName())) {
            errorMessage.setVisibility(android.view.View.GONE);
            saveButton.setEnabled(true);
            return;
        }

        // Check for duplicate names in the scene
        String uniqueName = renderer.getCurrentScene().getUniqueName(newName);
        if (!uniqueName.equals(newName)) {
            // Name is a duplicate
            errorMessage.setText(R.string.sprite_name_must_be_unique);
            errorMessage.setVisibility(android.view.View.VISIBLE);
            saveButton.setEnabled(false);
            return;
        }

        // Name is valid
        errorMessage.setVisibility(android.view.View.GONE);
        saveButton.setEnabled(true);
    }

    /**
     * Handle sprite name change - assumes the name has been validated.
     *
     * @param sprite the sprite being renamed
     * @param newName the new validated name
     */
    private void handleNameChange(Sprite sprite, String newName) {
        // Validate the name is not empty (should already be validated)
        if (newName.isEmpty()) {
            return;
        }

        // Check if the name is the same as the current name - no change needed
        if (newName.equals(sprite.getName())) {
            return;
        }

        // Update the sprite's name
        sprite.setName(newName);

        Log.d(TAG, "Sprite name updated to: " + newName);

        // Notify listener of the name change
        if (onSpriteNameChangeListener != null) {
            onSpriteNameChangeListener.onSpriteNameChanged(sprite, newName);
        }
    }
}

