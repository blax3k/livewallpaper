package com.example.livewallpaper.ui.editor.controllers;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages generic slider setup and editing for sprite properties.
 * Encapsulates all slider configuration and dialog handling logic.
 */
public class SliderController {
    private static final String TAG = "SliderManager";
    private final Context context;

    public SliderController(Context context) {
        this.context = context;
    }

    /**
     * Configuration class for slider parameters.
     */
    public static class SliderConfig {
        public final SeekBar slider;
        public final TextView valueDisplay;
        public final float minValue;
        public final float maxValue;
        public final float increment;
        public final String label;
        public final Consumer<Float> onValueChanged;
        public final Supplier<Float> currentValueGetter;
        public final Runnable onEditComplete;
        public final boolean showResortCallback;

        public SliderConfig(SeekBar slider, TextView display, float min, float max, float inc,
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

        public SliderConfig(SeekBar slider, TextView display, float min, float max, float inc,
                     String label, Consumer<Float> onChange, Supplier<Float> currentGetter) {
            this(slider, display, min, max, inc, label, onChange, currentGetter, null, false);
        }
    }

    /**
     * Set up a generic slider with manual edit dialog support.
     *
     * @param config the slider configuration
     */
    public void setupSlider(SliderConfig config) {
        if (config.slider == null || config.valueDisplay == null) return;

        // Remove any existing listener to avoid feedback loops
        config.slider.setOnSeekBarChangeListener(null);

        // Get current value and calculate initial progress
        float currentValue = config.currentValueGetter.get();
        int sliderProgress = (int) ((currentValue - config.minValue) / config.increment);
        config.slider.setProgress(sliderProgress);
        config.valueDisplay.setText(String.format("%.2f", currentValue));

        // Set up click listener for manual editing via dialog
        config.valueDisplay.setOnClickListener(v -> showEditDialog(config, currentValue));

        // Set up listener for slider changes
        config.slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float newValue = config.minValue + (progress * config.increment);
                    newValue = Math.max(config.minValue, Math.min(config.maxValue, newValue));
                    config.onValueChanged.accept(newValue);
                    config.valueDisplay.setText(String.format("%.2f", newValue));
                    Log.d(TAG, config.label + " updated to: " + newValue);

                    // Call edit complete callback if provided
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
     * Show a dialog for manual slider value editing.
     *
     * @param config the slider configuration
     * @param currentValue the current value to display
     */
    public void showEditDialog(SliderConfig config, float currentValue) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit " + config.label);

        final android.widget.EditText input = new android.widget.EditText(context);
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

                // Update value
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
                Toast.makeText(context, "Invalid number", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Invalid number entered for " + config.label + ": " + e.getMessage());
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
