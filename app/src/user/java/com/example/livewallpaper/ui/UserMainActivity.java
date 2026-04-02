package com.example.livewallpaper.ui;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.gl.GLWallpaperRenderer;
import com.example.livewallpaper.gl.GLWallpaperService;
import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.managers.AvatarSceneManager;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.managers.SceneFileManager;
import com.example.livewallpaper.scene.managers.LiveWallpaperSceneManager;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class UserMainActivity extends AppCompatActivity {
    private static final String TAG = "UserMainActivity";
    private GLSurfaceView glSurfaceView;
    private AvatarSceneManager sceneManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_main);

        // Initialize MotionConfig
        MotionConfig.initialize(this);
        
        // Initialize bundled scenes
        initializePersistentScenes();

        setupGLView();

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

    private void setupGLView() {
        LinearLayout glViewLayout = findViewById(R.id.glViewLayout);
        if (glViewLayout == null) return;

        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setPreserveEGLContextOnPause(true);
        
        // Ensure it fills the parent layout
        glSurfaceView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));



        sceneManager = new AvatarSceneManager(this, "test.json");
        
        glSurfaceView.setRenderer(new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                sceneManager.onSurfaceCreated();
            }

            @Override
            public void onSurfaceChanged(GL10 gl, int width, int height) {
                sceneManager.onSurfaceChanged(width, height);
            }

            @Override
            public void onDrawFrame(GL10 gl) {
                sceneManager.onDrawFrame();
            }
        });

        glViewLayout.addView(glSurfaceView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
            if (sceneManager != null) {
                sceneManager.onRendererResume(System.nanoTime());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) {
            glSurfaceView.onPause();
            if (sceneManager != null) {
                sceneManager.onRendererPause();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sceneManager != null) {
            sceneManager.onDestroy();
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
