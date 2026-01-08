package com.example.livewallpaper;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            Log.d(TAG, "onCreate called");

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

            ToggleButton scrollToggle = findViewById(R.id.toggle_scroll_motion);
            if (scrollToggle != null) {
                Log.d(TAG, "Toggle button found, setting click listener");
                // Set initial state based on the config
                scrollToggle.setChecked(ScrollMotionConfig.isScrollMotionEnabled());
                scrollToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    ScrollMotionConfig.setScrollMotionEnabled(isChecked);
                    Log.d(TAG, "Scroll motion toggled: " + (isChecked ? "ON" : "OFF"));
                    Toast.makeText(MainActivity.this,
                        "Scroll motion " + (isChecked ? "enabled" : "disabled"),
                        Toast.LENGTH_SHORT).show();
                });
                Log.d(TAG, "Toggle button click listener set");
            } else {
                Log.e(TAG, "Toggle button not found in layout!");
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
}


