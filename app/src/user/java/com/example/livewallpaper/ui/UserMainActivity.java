package com.example.livewallpaper.ui;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.gl.GLWallpaperService;
import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.managers.SceneFileManager;

public class UserMainActivity extends AppCompatActivity {
    private static final String TAG = "UserMainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_main);

        // Initialize MotionConfig
        MotionConfig.initialize(this);
        
        // Initialize bundled scenes
        initializePersistentScenes();

        Button setWallpaperButton = findViewById(R.id.btn_set_wallpaper);
        if (setWallpaperButton != null) {
            setWallpaperButton.setOnClickListener(v -> setWallpaper());
        }

        View settingsButton = findViewById(R.id.btn_settings);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, UserSettingsActivity.class);
                startActivity(intent);
            });
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
            Toast.makeText(this, "Failed to set wallpaper", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializePersistentScenes() {
        try {
            SceneFileManager sceneFileManager = new SceneFileManager(this, null);
            sceneFileManager.loadAvailableSceneFiles();
        } catch (Exception e) {
            TimberLog.e(TAG, "Error initializing scenes: " + e.getMessage(), e);
        }
    }
}
