package com.example.livewallpaper.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.example.livewallpaper.scene.Sprite;

import java.util.Locale;

/**
 * Builds and manages the sprite details UI.
 * Handles property row creation and sprite property editing setup.
 */
public class SpriteDetailsBuilder {
    private static final String TAG = "SpriteDetailsBuilder";

    private final Activity activity;
    private final ScenePreviewRenderer renderer;
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
    private View.OnClickListener textureButtonListener;

    public SpriteDetailsBuilder(Activity activity, ScenePreviewRenderer renderer,
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
     * Build and display sprite details in the properties table.
     */
    public void build(Sprite sprite) {
        if (sprite == null || propertiesTable == null) {
            return;
        }

        // Sync with renderer - ensure sprite is selected and highlighted
        renderer.setSelectedSprite(sprite);

        propertiesTable.removeAllViews();

        // Add name as read-only property
        addPropertyRow("Name", sprite.getName());

        // Add texture resource button
        addPropertyButtonRow("Texture Resource");

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

        // Set up dimension fields
        dimensionController.setup(sprite, aspectRatio);

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
     * Add a read-only property row to the table.
     */
    private void addPropertyRow(String label, String value) {
        TableRow row = new TableRow(activity);
        row.setLayoutParams(new TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            TableLayout.LayoutParams.WRAP_CONTENT
        ));

        // Label text view
        TextView labelView = new TextView(activity);
        labelView.setText(label + ":");
        labelView.setLayoutParams(new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        ));
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setPadding(8, 8, 8, 8);

        // Value text view
        TextView valueView = new TextView(activity);
        valueView.setText(value);
        valueView.setLayoutParams(new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT,
            1.0f
        ));
        valueView.setPadding(8, 8, 8, 8);

        row.addView(labelView);
        row.addView(valueView);
        propertiesTable.addView(row);
    }

    /**
     * Add a property row with an action button.
     */
    private void addPropertyButtonRow(String label) {
        TableRow row = new TableRow(activity);
        row.setLayoutParams(new TableLayout.LayoutParams(
            TableLayout.LayoutParams.MATCH_PARENT,
            TableLayout.LayoutParams.WRAP_CONTENT
        ));

        // Label text view
        TextView labelView = new TextView(activity);
        labelView.setText(label + ":");
        labelView.setLayoutParams(new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        ));
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setPadding(8, 8, 8, 8);

        // Button to open texture picker
        Button textureButton = new Button(activity);
        textureButton.setText("Edit Texture");
        textureButton.setLayoutParams(new TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT,
            1.0f
        ));
        textureButton.setPadding(8, 8, 8, 8);

        // Apply the listener immediately if it's been set
        if (textureButtonListener != null) {
            textureButton.setOnClickListener(textureButtonListener);
        }

        row.addView(labelView);
        row.addView(textureButton);
        propertiesTable.addView(row);
    }

    /**
     * Set the click listener for the texture button.
     * This listener will be applied to all future texture buttons created during build().
     */
    public void setTextureButtonListener(View.OnClickListener listener) {
        this.textureButtonListener = listener;
    }
}
