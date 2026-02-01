package com.example.livewallpaper.ui.controllers;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.livewallpaper.scene.SceneManager;
import com.example.livewallpaper.scene.Sprite;

/**
 * Manages scale slider operations for sprite scaling.
 * Handles scale calculation, display updates, and slider interactions.
 */
public class ScaleSliderController {
    private static final String TAG = "ScaleSliderController";
    private static final float MIN_SCALE = 0.2f;
    private static final float MAX_SCALE = 15.0f;
    private static final float SCALE_INCREMENT = 0.1f;

    private final Context context;
    private final SceneManager renderer;
    private final SeekBar scaleSlider;
    private final TextView scaleValue;

    public ScaleSliderController(Context context, SceneManager renderer,
                                SeekBar scaleSlider, TextView scaleValue) {
        this.context = context;
        this.renderer = renderer;
        this.scaleSlider = scaleSlider;
        this.scaleValue = scaleValue;
    }

    /**
     * Set up the scale slider for a sprite.
     */
    public void setup(Sprite sprite) {
        if (scaleSlider == null || scaleValue == null || sprite == null) {
            return;
        }

        scaleSlider.setOnSeekBarChangeListener(null);

        // Calculate initial scale based on the larger ORIGINAL dimension
        float originalWidth = sprite.getOriginalWidth();
        float originalHeight = sprite.getOriginalHeight();
        float largerDimension = Math.max(originalWidth, originalHeight);
        float initialScale = largerDimension;

        // Clamp to valid range
        initialScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, initialScale));

        // Calculate scale range and slider max
        float scaleRange = MAX_SCALE - MIN_SCALE;
        int maxProgress = (int) (scaleRange / SCALE_INCREMENT);

        // Set slider position
        int sliderProgress = (int) ((initialScale - MIN_SCALE) / SCALE_INCREMENT);
        sliderProgress = Math.max(0, Math.min(maxProgress, sliderProgress));
        scaleSlider.setMax(maxProgress);
        scaleSlider.setProgress(sliderProgress);

        // Update the display text
        updateDisplay(initialScale);

        // Set up click listener for manual editing
        scaleValue.setOnClickListener(v -> showEditDialog(sprite, originalWidth, originalHeight));

        // Set up slider listener
        setupSliderListener(sprite);
    }

    /**
     * Update the scale slider and display to match sprite dimensions.
     */
    public void updateDisplay(Sprite sprite) {
        if (sprite == null || scaleSlider == null || scaleValue == null) {
            return;
        }

        float originalWidth = sprite.getOriginalWidth();
        float originalHeight = sprite.getOriginalHeight();

        // Calculate scale based on the larger ORIGINAL dimension
        float largerDimension = Math.max(originalWidth, originalHeight);
        float scale = largerDimension;

        // Clamp scale to valid range
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));

        // Calculate the scale range and slider max
        float scaleRange = MAX_SCALE - MIN_SCALE;
        int maxProgress = (int) (scaleRange / SCALE_INCREMENT);

        // Update slider position
        int sliderProgress = (int) ((scale - MIN_SCALE) / SCALE_INCREMENT);
        sliderProgress = Math.max(0, Math.min(maxProgress, sliderProgress));

        scaleSlider.setOnSeekBarChangeListener(null);
        scaleSlider.setMax(maxProgress);
        scaleSlider.setProgress(sliderProgress);

        // Re-enable listener
        setupSliderListener(sprite);

        // Update display text
        updateDisplay(scale);

        Log.d(TAG, "Updated scale slider display - Scale: " + scale + "x");
    }

    /**
     * Update the scale display value.
     */
    private void updateDisplay(float scale) {
        if (scaleValue != null) {
            scaleValue.setText(String.format("%.2fx", scale));
        }
    }

    /**
     * Set up the slider listener for scale changes.
     */
    private void setupSliderListener(Sprite sprite) {
        if (scaleSlider == null || sprite == null) {
            return;
        }

        scaleSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && sprite != null) {
                    float desiredScale = MIN_SCALE + (progress * SCALE_INCREMENT);
                    desiredScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, desiredScale));

                    // Use ORIGINAL dimensions as the base for scaling
                    float originalWidth = sprite.getOriginalWidth();
                    float originalHeight = sprite.getOriginalHeight();

                    // Calculate the aspect ratio from original dimensions
                    float largerDimension = Math.max(originalWidth, originalHeight);
                    float scaleFactor = 1.0f / largerDimension;
                    float baseWidth = originalWidth * scaleFactor;
                    float baseHeight = originalHeight * scaleFactor;

                    float newWidth = baseWidth * desiredScale;
                    float newHeight = baseHeight * desiredScale;

                    renderer.updateSpriteDimensions(sprite, newWidth, newHeight);

                    updateDisplay(desiredScale);

                    Log.d(TAG, "Scale changed to: " + desiredScale + "x");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    /**
     * Show dialog for manually editing the scale value.
     */
    private void showEditDialog(Sprite sprite, float baseWidth, float baseHeight) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Scale");

        final android.widget.EditText input = new android.widget.EditText(context);
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

                renderer.updateSpriteDimensions(sprite, newWidth, newHeight);

                updateDisplay(scale);
                updateDisplay(sprite);

                Log.d(TAG, "Scale manually edited to: " + scale);
            } catch (NumberFormatException e) {
                Toast.makeText(context, "Invalid number", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Invalid scale input: " + e.getMessage());
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
