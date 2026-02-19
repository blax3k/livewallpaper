package com.example.livewallpaper.ui.activities;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.gl.GLWallpaperService;
import com.example.livewallpaper.R;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.ui.managers.SceneFileManager;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            Log.d(TAG, "onCreate called");

            // Initialize MotionConfig with persistent storage
            MotionConfig.initialize(this);

            // Initialize persistent scenes folder on app startup
            initializePersistentScenes();

            setContentView(R.layout.activity_main);
            Log.d(TAG, "Layout inflated successfully");

            Button setWallpaperButton = findViewById(R.id.btn_set_wallpaper);
            if (setWallpaperButton != null) {
                Log.d(TAG, "Button found, setting click listener");
                setWallpaperButton.setOnClickListener(v -> setWallpaper());
                Log.d(TAG, "Button click listener set");
            } else {
                Log.e(TAG, "Button not found in layout!");
                Toast.makeText(this, "UI initialization failed", Toast.LENGTH_SHORT).show();
            }

            Button viewScenesButton = findViewById(R.id.btn_view_scenes);
            if (viewScenesButton != null) {
                Log.d(TAG, "View Scenes button found, setting click listener");
                viewScenesButton.setOnClickListener(v -> viewScenes());
                Log.d(TAG, "View Scenes button click listener set");
            } else {
                Log.e(TAG, "View Scenes button not found in layout!");
            }

            Button reloadScenesButton = findViewById(R.id.btn_reload_scenes);
            if (reloadScenesButton != null) {
                Log.d(TAG, "Reload Scenes button found, setting click listener");
                reloadScenesButton.setOnClickListener(v -> reloadScenes());
                Log.d(TAG, "Reload Scenes button click listener set");
            } else {
                Log.e(TAG, "Reload Scenes button not found in layout!");
            }

            Switch scrollToggle = findViewById(R.id.toggle_scroll_motion);
            if (scrollToggle != null) {
                Log.d(TAG, "Scroll toggle switch found, setting listener");
                // Set initial state based on the config
                scrollToggle.setChecked(MotionConfig.isScrollMotionEnabled());
                scrollToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    MotionConfig.setScrollMotionEnabled(isChecked);
                    Log.d(TAG, "Scroll motion toggled: " + (isChecked ? "ON" : "OFF"));
                    Toast.makeText(MainActivity.this,
                        "Scroll motion " + (isChecked ? "enabled" : "disabled"),
                        Toast.LENGTH_SHORT).show();
                });
                Log.d(TAG, "Scroll toggle switch listener set");
            } else {
                Log.e(TAG, "Scroll toggle switch not found in layout!");
            }

            Switch gyroToggle = findViewById(R.id.toggle_gyro_motion);
            if (gyroToggle != null) {
                Log.d(TAG, "Gyro toggle switch found, setting listener");
                // Set initial state based on the config
                gyroToggle.setChecked(MotionConfig.isGyroMotionEnabled());
                gyroToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    MotionConfig.setGyroMotionEnabled(isChecked);
                    Log.d(TAG, "Gyro motion toggled: " + (isChecked ? "ON" : "OFF"));
                    Toast.makeText(MainActivity.this,
                        "Gyro motion " + (isChecked ? "enabled" : "disabled"),
                        Toast.LENGTH_SHORT).show();
                });
                Log.d(TAG, "Gyro toggle switch listener set");
            } else {
                Log.e(TAG, "Gyro toggle switch not found in layout!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Initialization error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setWallpaper() {
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, GLWallpaperService.class)
            );
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error setting wallpaper: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to set wallpaper: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void viewScenes() {
        try {
            Intent intent = new Intent(this, SceneListActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to scenes: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to open scenes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void reloadScenes() {
        try {
            GLWallpaperService.refreshSceneList(this);
            Log.d(TAG, "Scene reload requested");
            Toast.makeText(this, "Scenes reloaded", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error reloading scenes: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to reload scenes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Initialize the persistent scenes folder by copying bundled scene files if needed.
     * This runs on app startup to ensure the persistent folder is populated.
     */
    private void initializePersistentScenes() {
        try {
            SceneFileManager sceneFileManager = new SceneFileManager(this, null);
            // Call loadAvailableSceneFiles which will populate the folder if empty
            String[] sceneFiles = sceneFileManager.loadAvailableSceneFiles();
            Log.d(TAG, "Persistent scenes initialized with " + sceneFiles.length + " scene files");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing persistent scenes: " + e.getMessage(), e);
        }
    }
}
