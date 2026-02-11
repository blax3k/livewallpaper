package com.example.livewallpaper.ui.builders;

import android.app.Activity;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.example.livewallpaper.scene.SceneManager;
import com.example.livewallpaper.scene.Sprite;
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

    private final Activity activity;
    private final SceneManager renderer;
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

    public SpriteDetailsBuilder(Activity activity, SceneManager renderer,
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
     * Add a read-only property row to the table.
     */
    private void addPropertyRow(String label, String value) {
        // ...existing code...
    }
}

