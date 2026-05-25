package com.hashilab.dev.editor.activities;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.gl.GLWallpaperService;
import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.managers.SceneFileManager;
import com.example.livewallpaper.sensors.MotionConfig;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            TimberLog.d(TAG, "onCreate called");

            MotionConfig.initialize(this);
            initializePersistentScenes();

            setContentView(R.layout.activity_main);

            Button setWallpaperButton = findViewById(R.id.btn_set_wallpaper);
            if (setWallpaperButton != null) {
                setWallpaperButton.setOnClickListener(v -> setWallpaper());
            } else {
                TimberLog.e(TAG, "Set Wallpaper button not found!");
            }

            Button viewScenesButton = findViewById(R.id.btn_view_scenes);
            if (viewScenesButton != null) {
                viewScenesButton.setOnClickListener(v -> viewScenes());
            } else {
                TimberLog.e(TAG, "View Scenes button not found!");
            }

            Button browseWebEditorButton = findViewById(R.id.btn_browse_web_editor);
            if (browseWebEditorButton != null) {
                browseWebEditorButton.setOnClickListener(v -> browseWebEditor());
            }

            Button settingsButton = findViewById(R.id.btn_settings);
            if (settingsButton != null) {
                settingsButton.setOnClickListener(v -> openSettings());
            } else {
                TimberLog.e(TAG, "Settings button not found!");
            }

        } catch (Exception e) {
            TimberLog.e(TAG, "Error in onCreate: " + e.getMessage(), e);
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
            TimberLog.e(TAG, "Error setting wallpaper: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to set wallpaper: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void viewScenes() {
        try {
            startActivity(new Intent(this, SceneListActivity.class));
        } catch (Exception e) {
            TimberLog.e(TAG, "Error navigating to scenes: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to open scenes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void browseWebEditor() {
        try {
            startActivity(new Intent(this, ProjectBrowserActivity.class));
        } catch (Exception e) {
            TimberLog.e(TAG, "Error opening web editor browser: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to open web editor browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(this, SettingsActivity.class));
        } catch (Exception e) {
            TimberLog.e(TAG, "Error opening settings: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to open settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void initializePersistentScenes() {
        try {
            SceneFileManager sceneFileManager = new SceneFileManager(this, null);
            String[] sceneFiles = sceneFileManager.loadAvailableSceneFiles();
            TimberLog.d(TAG, "Persistent scenes initialized with " + sceneFiles.length + " scene files");
        } catch (Exception e) {
            TimberLog.e(TAG, "Error initializing persistent scenes: " + e.getMessage(), e);
        }
    }
}
