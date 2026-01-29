package com.example.livewallpaper.ui;

import android.content.Context;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to show a dialog for selecting drawable resources to add as new sprites.
 */
public class DrawableImagePickerDialog {
    private static final String TAG = "DrawableImagePickerDialog";

    /**
     * Show a dialog to select a drawable resource.
     *
     * @param context the Android context
     * @param onImageSelected callback when an image is selected
     */
    public static void showImagePickerDialog(Context context, OnImageSelectedListener onImageSelected) {
        // Get list of available drawable names
        List<String> drawableNames = getAvailableDrawables(context);

        if (drawableNames.isEmpty()) {
            Log.w(TAG, "No drawable resources found");
            return;
        }

        // Convert to array for dialog
        String[] items = drawableNames.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Select Image for New Sprite");
        builder.setItems(items, (dialog, which) -> {
            String selectedImageName = items[which];
            int resourceId = getDrawableResourceId(context, selectedImageName);
            if (resourceId != 0) {
                onImageSelected.onImageSelected(selectedImageName, resourceId);
            } else {
                Log.e(TAG, "Failed to resolve drawable: " + selectedImageName);
            }
        });

        builder.show();
    }

    /**
     * Get all available drawable names from the R.drawable class.
     *
     * @param context the Android context
     * @return a list of drawable names (without resource names that start with "ic_" or contain "xml")
     */
    private static List<String> getAvailableDrawables(Context context) {
        List<String> drawables = new ArrayList<>();

        try {
            Class<?> drawablesClass = Class.forName(context.getPackageName() + ".R$drawable");
            java.lang.reflect.Field[] fields = drawablesClass.getFields();

            for (java.lang.reflect.Field field : fields) {
                String fieldName = field.getName();
                // Skip system icons, launchers, and XML drawables
                if (!fieldName.startsWith("ic_") && !fieldName.startsWith("ic_launcher") &&
                    !fieldName.endsWith("xml")) {
                    drawables.add(fieldName);
                }
            }
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "R.drawable class not found", e);
        }

        Log.d(TAG, "Found " + drawables.size() + " drawable resources");
        return drawables;
    }

    /**
     * Get the resource ID for a drawable by name.
     *
     * @param context the Android context
     * @param resourceName the name of the drawable
     * @return the resource ID, or 0 if not found
     */
    private static int getDrawableResourceId(Context context, String resourceName) {
        try {
            Class<?> drawablesClass = Class.forName(context.getPackageName() + ".R$drawable");
            java.lang.reflect.Field field = drawablesClass.getField(resourceName);
            return field.getInt(null);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to get drawable resource ID for: " + resourceName, e);
            return 0;
        }
    }

    /**
     * Callback interface for when an image is selected.
     */
    public interface OnImageSelectedListener {
        void onImageSelected(String imageName, int resourceId);
    }
}
