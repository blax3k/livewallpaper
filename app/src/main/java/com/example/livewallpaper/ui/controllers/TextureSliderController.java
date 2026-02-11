package com.example.livewallpaper.ui.controllers;

import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.livewallpaper.scene.Sprite;
import com.example.livewallpaper.scene.TextureEditState;

/**
 * Manages texture-related sliders for EditTextureActivity.
 * Handles width, height, and texture scale sliders with their update logic.
 */
public class TextureSliderController {
    private static final String TAG = "TextureSliderController";

    private final SeekBar widthSlider;
    private final SeekBar heightSlider;
    private final SeekBar textureScaleSlider;
    private final TextView widthValueText;
    private final TextView heightValueText;
    private final TextView textureScaleValueText;

    private Sprite currentSprite;
    private TextureEditState textureEditState;
    private Runnable onChangeCallback;

    public TextureSliderController(SeekBar widthSlider, SeekBar heightSlider, SeekBar textureScaleSlider,
                                   TextView widthValueText, TextView heightValueText, TextView textureScaleValueText) {
        this.widthSlider = widthSlider;
        this.heightSlider = heightSlider;
        this.textureScaleSlider = textureScaleSlider;
        this.widthValueText = widthValueText;
        this.heightValueText = heightValueText;
        this.textureScaleValueText = textureScaleValueText;
    }

    /**
     * Set callback to be invoked when any slider value changes.
     * Useful for updating texture coordinates or other dependent UI.
     */
    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    /**
     * Initialize all sliders for a sprite with given texture edit state.
     */
    public void setup(Sprite sprite, TextureEditState state) {
        if (sprite == null || state == null) {
            return;
        }

        this.currentSprite = sprite;
        this.textureEditState = state;

        float currentWidth = sprite.getWidth();
        float currentHeight = sprite.getHeight();
        float currentTextureScale = state.getTextureScale();

        // Width/Height: max 15.0, increments of 0.1 (slider value = dimension / 0.1)
        widthSlider.setProgress(Math.round(currentWidth / 0.1f));
        heightSlider.setProgress(Math.round(currentHeight / 0.1f));

        // Texture Scale: 1.0 to 8.0, increments of 0.1
        // Formula: (scale - 1.0) / 0.1
        int textureScaleProgress = Math.round((currentTextureScale - 1.0f) / 0.1f);
        textureScaleSlider.setProgress(Math.max(0, Math.min(70, textureScaleProgress)));

        updateWidthDisplay(currentWidth);
        updateHeightDisplay(currentHeight);
        updateTextureScaleDisplay(currentTextureScale);

        setupListeners();
    }

    /**
     * Set up the slider listeners.
     */
    private void setupListeners() {
        widthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && currentSprite != null) {
                    float width = progress * 0.1f;
                    currentSprite.setWidthAndUpdateOriginal(width);
                    updateWidthDisplay(width);
                    updateTextureScaleDisplay(textureEditState.getTextureScale());
                    notifyChange();
                    Log.d(TAG, "Width changed to: " + width);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        heightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && currentSprite != null) {
                    float height = progress * 0.1f;
                    currentSprite.setHeightAndUpdateOriginal(height);
                    updateHeightDisplay(height);
                    updateTextureScaleDisplay(textureEditState.getTextureScale());
                    notifyChange();
                    Log.d(TAG, "Height changed to: " + height);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        textureScaleSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && currentSprite != null && textureEditState != null) {
                    // Texture scale: 1.0 + (progress * 0.1), range 1.0 to 8.0
                    float scale = 1.0f + (progress * 0.1f);
                    textureEditState.setTextureScale(scale);
                    updateTextureScaleDisplay(scale);
                    notifyChange();
                    Log.d(TAG, "Texture scale changed to: " + scale);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    /**
     * Update the width display value.
     */
    public void updateWidthDisplay(float width) {
        if (widthValueText != null) {
            widthValueText.setText(String.format("%.1f", width));
        }
    }

    /**
     * Update the height display value.
     */
    public void updateHeightDisplay(float height) {
        if (heightValueText != null) {
            heightValueText.setText(String.format("%.1f", height));
        }
    }

    /**
     * Update the texture scale display value.
     */
    public void updateTextureScaleDisplay(float scale) {
        if (textureScaleValueText != null) {
            textureScaleValueText.setText(String.format("%.1fx", scale));
        }
    }

    /**
     * Get the current sprite being edited.
     */
    public Sprite getCurrentSprite() {
        return currentSprite;
    }

    /**
     * Get the current texture edit state.
     */
    public TextureEditState getTextureEditState() {
        return textureEditState;
    }

    /**
     * Notify listeners that a value has changed.
     */
    private void notifyChange() {
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }
}
