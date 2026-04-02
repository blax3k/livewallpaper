package com.hashilab.dev.editor.activities;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import com.example.livewallpaper.logging.TimberLog;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;
import android.view.View;
import android.widget.AdapterView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.gl.GLWallpaperService;
import com.example.livewallpaper.R;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.managers.SceneFileManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            TimberLog.d(TAG, "onCreate called");

            // Initialize MotionConfig with persistent storage
            MotionConfig.initialize(this);

            // Initialize persistent scenes folder on app startup
            initializePersistentScenes();

            setContentView(R.layout.activity_main);
            TimberLog.d(TAG, "Layout inflated successfully");

            Button setWallpaperButton = findViewById(R.id.btn_set_wallpaper);
            if (setWallpaperButton != null) {
                TimberLog.d(TAG, "Button found, setting click listener");
                setWallpaperButton.setOnClickListener(v -> setWallpaper());
                TimberLog.d(TAG, "Button click listener set");
            } else {
                TimberLog.e(TAG, "Button not found in layout!");
                Toast.makeText(this, "UI initialization failed", Toast.LENGTH_SHORT).show();
            }

            Button viewScenesButton = findViewById(R.id.btn_view_scenes);
            if (viewScenesButton != null) {
                TimberLog.d(TAG, "View Scenes button found, setting click listener");
                viewScenesButton.setOnClickListener(v -> viewScenes());
                TimberLog.d(TAG, "View Scenes button click listener set");
            } else {
                TimberLog.e(TAG, "View Scenes button not found in layout!");
            }

            Button reloadScenesButton = findViewById(R.id.btn_reload_scenes);
            if (reloadScenesButton != null) {
                TimberLog.d(TAG, "Reload Scenes button found, setting click listener");
                reloadScenesButton.setOnClickListener(v -> reloadScenes());
                TimberLog.d(TAG, "Reload Scenes button click listener set");
            } else {
                TimberLog.e(TAG, "Reload Scenes button not found in layout!");
            }

            Switch scrollToggle = findViewById(R.id.toggle_scroll_motion);
            if (scrollToggle != null) {
                TimberLog.d(TAG, "Scroll toggle switch found, setting listener");
                // Set initial state based on the config
                scrollToggle.setChecked(MotionConfig.isScrollMotionEnabled());
                scrollToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    MotionConfig.setScrollMotionEnabled(isChecked);
                    TimberLog.d(TAG, "Scroll motion toggled: " + (isChecked ? "ON" : "OFF"));
                    Toast.makeText(MainActivity.this,
                        "Scroll motion " + (isChecked ? "enabled" : "disabled"),
                        Toast.LENGTH_SHORT).show();
                });
                TimberLog.d(TAG, "Scroll toggle switch listener set");
            } else {
                TimberLog.e(TAG, "Scroll toggle switch not found in layout!");
            }

            Switch gyroToggle = findViewById(R.id.toggle_gyro_motion);
            if (gyroToggle != null) {
                TimberLog.d(TAG, "Gyro toggle switch found, setting listener");
                // Set initial state based on the config
                gyroToggle.setChecked(MotionConfig.isGyroMotionEnabled());
                gyroToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    MotionConfig.setGyroMotionEnabled(isChecked);
                    TimberLog.d(TAG, "Gyro motion toggled: " + (isChecked ? "ON" : "OFF"));
                    Toast.makeText(MainActivity.this,
                        "Gyro motion " + (isChecked ? "enabled" : "disabled"),
                        Toast.LENGTH_SHORT).show();
                });
                TimberLog.d(TAG, "Gyro toggle switch listener set");
            } else {
                TimberLog.e(TAG, "Gyro toggle switch not found in layout!");
            }

            setupTimeOverrideSpinner();

        } catch (Exception e) {
            TimberLog.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Initialization error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupTimeOverrideSpinner() {
        Spinner timeSpinner = findViewById(R.id.spinner_time_override);
        if (timeSpinner == null) {
            TimberLog.e(TAG, "Time override spinner not found!");
            return;
        }

        List<String> options = new ArrayList<>();
        options.add(MotionConfig.OVERRIDE_AUTO);
        options.add("DAWN");
        options.add("DAY");
        options.add("SUNSET");
        options.add("NIGHT");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeSpinner.setAdapter(adapter);

        // Set initial selection
        String currentOverride = MotionConfig.getTimeOfDayOverride();
        int initialPosition = options.indexOf(currentOverride);
        if (initialPosition >= 0) {
            timeSpinner.setSelection(initialPosition);
        }

        timeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = options.get(position);
                if (!selected.equals(MotionConfig.getTimeOfDayOverride())) {
                    MotionConfig.setTimeOfDayOverride(selected);
                    TimberLog.d(TAG, "Time of day override changed to: " + selected);
                    Toast.makeText(MainActivity.this, "Time override: " + selected, Toast.LENGTH_SHORT).show();
                    
                    // Trigger an immediate scene cycle in the wallpaper if it's running
                    GLWallpaperService.triggerSceneCycle();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
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
            TimberLog.e(TAG, "Error setting wallpaper: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to set wallpaper: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void viewScenes() {
        try {
            Intent intent = new Intent(this, SceneListActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            TimberLog.e(TAG, "Error navigating to scenes: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to open scenes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void reloadScenes() {
        try {
            GLWallpaperService.refreshSceneList(this);
            TimberLog.d(TAG, "Scene reload requested");
            Toast.makeText(this, "Scenes reloaded", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            TimberLog.e(TAG, "Error reloading scenes: " + e.getMessage(), e);
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
            TimberLog.d(TAG, "Persistent scenes initialized with " + sceneFiles.length + " scene files");
        } catch (Exception e) {
            TimberLog.e(TAG, "Error initializing persistent scenes: " + e.getMessage(), e);
        }
    }
}
