package com.example.livewallpaper.ui.controllers;

import android.util.Log;
import android.widget.EditText;

import com.example.livewallpaper.scene.SceneTransitionManager;
import com.example.livewallpaper.scene.Sprite;

/**
 * Manages width and height edit fields with proportional update logic.
 * Maintains aspect ratio when one dimension is edited.
 */
public class DimensionController {
    private static final String TAG = "DimensionController";

    private final SceneTransitionManager.ScenePreviewRenderer renderer;
    private final EditText widthEdit;
    private final EditText heightEdit;
    private float originalAspectRatio;

    public DimensionController(SceneTransitionManager.ScenePreviewRenderer renderer, EditText widthEdit, EditText heightEdit) {
        this.renderer = renderer;
        this.widthEdit = widthEdit;
        this.heightEdit = heightEdit;
    }

    /**
     * Set up dimension edit fields for a sprite.
     */
    public void setup(Sprite sprite, float aspectRatio) {
        if (widthEdit == null || heightEdit == null || sprite == null) {
            return;
        }

        this.originalAspectRatio = aspectRatio;

        float currentWidth = sprite.getWidth();
        float currentHeight = sprite.getHeight();

        updateDisplays(currentWidth, currentHeight);

        // Width field focus change listener
        widthEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && sprite != null) {
                try {
                    float newWidth = Float.parseFloat(widthEdit.getText().toString());
                    renderer.updateSpriteWidth(sprite, newWidth, originalAspectRatio);
                    updateDisplays(sprite.getWidth(), sprite.getHeight());
                    Log.d(TAG, "Width updated from EditText: " + newWidth);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid width input");
                    updateDisplays(sprite.getWidth(), sprite.getHeight());
                }
            }
        });

        // Height field focus change listener
        heightEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && sprite != null) {
                try {
                    float newHeight = Float.parseFloat(heightEdit.getText().toString());
                    renderer.updateSpriteHeight(sprite, newHeight, originalAspectRatio);
                    updateDisplays(sprite.getWidth(), sprite.getHeight());
                    Log.d(TAG, "Height updated from EditText: " + newHeight);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid height input");
                    updateDisplays(sprite.getWidth(), sprite.getHeight());
                }
            }
        });
    }

    /**
     * Update both width and height display fields.
     */
    public void updateDisplays(float width, float height) {
        if (widthEdit != null) {
            widthEdit.setText(String.format("%.2f", width));
        }
        if (heightEdit != null) {
            heightEdit.setText(String.format("%.2f", height));
        }
    }
}
