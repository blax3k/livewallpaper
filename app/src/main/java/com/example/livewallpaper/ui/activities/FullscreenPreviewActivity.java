package com.example.livewallpaper.ui.activities;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.livewallpaper.R;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.managers.EditSceneManager;

public class FullscreenPreviewActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "FullscreenPreviewActivity";
    public static final String EXTRA_SCENE_FILE_NAME = "scene_file_name";
    public static final String EXTRA_SCENE_DATA = "scene_data";

    private GLSurfaceView glSurfaceView;
    private EditSceneManager renderer;
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "FullscreenPreviewActivity onCreate called");

        android.view.Window window = getWindow();

        // Step 1: Tell the window to allow drawing behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false);
        Log.d(TAG, "WindowCompat.setDecorFitsSystemWindows(false) called");

        // Step 2: Set status bar and navigation bar colors to transparent
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        Log.d(TAG, "Status bar and navigation bar colors set to TRANSPARENT");

        // Step 3: Hide the system bars with immersive mode
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            Log.d(TAG, "System bars hidden with transient behavior");
        }

        // Step 4: Allow drawing into the display cutout area (the notch)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            Log.d(TAG, "LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES set - content will extend into cutout");
        }

        setContentView(R.layout.activity_fullscreen_preview);
        Log.d(TAG, "Content view set");

        // Check for preloaded scene data first (preferred for EditSceneActivity transitions)
        Scene preloadedScene = getIntent().getParcelableExtra(EXTRA_SCENE_DATA);
        if (preloadedScene != null) {
            Log.d(TAG, "Preloaded scene data found: " + preloadedScene.getSceneName());
            setupSceneWithPreloadedData(preloadedScene);
        } else {
            // Fall back to loading from file (legacy behavior)
            String sceneFileName = getIntent().getStringExtra(EXTRA_SCENE_FILE_NAME);
            if (sceneFileName == null) {
                Log.e(TAG, "No scene file name or preloaded scene provided!");
                finish();
                return;
            }
            Log.d(TAG, "Loading scene from file: " + sceneFileName);
            setupSceneFromFile(sceneFileName);
        }
    }

    /**
     * Setup the GL surface view and renderer with a preloaded Scene object.
     * This is used when transitioning from EditSceneActivity with current scene data.
     * The scene contains all current edits and doesn't need to be reloaded from disk.
     *
     * @param scene the preloaded Scene object
     */
    private void setupSceneWithPreloadedData(Scene scene) {
        // Initialize sensor manager for gyro support
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }

        // Set up the GL surface view with preloaded scene
        setupGLSurfaceView(scene);

        // Set up close button
        setupCloseButton();

        // Log view dimensions after layout
        logViewDimensions();
    }

    /**
     * Setup the GL surface view and renderer with a scene loaded from file.
     * This is the legacy behavior for backward compatibility.
     *
     * @param sceneFileName the name of the scene file to load
     */
    private void setupSceneFromFile(String sceneFileName) {
        // Initialize sensor manager for gyro support
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }

        // Set up the GL surface view with file-based loading
        setupGLSurfaceView(sceneFileName);

        // Set up close button
        setupCloseButton();

        // Log view dimensions after layout
        logViewDimensions();
    }

    private void logViewDimensions() {
        View glView = findViewById(R.id.fullscreen_preview_gl_view);
        if (glView != null) {
            glView.post(() -> {
                Log.d(TAG, "=== GLView Dimensions ===");
                Log.d(TAG, "GLView Width: " + glView.getWidth());
                Log.d(TAG, "GLView Height: " + glView.getHeight());
                Log.d(TAG, "GLView X: " + glView.getX());
                Log.d(TAG, "GLView Y: " + glView.getY());
                Log.d(TAG, "GLView Left: " + glView.getLeft());
                Log.d(TAG, "GLView Top: " + glView.getTop());
                Log.d(TAG, "GLView Right: " + glView.getRight());
                Log.d(TAG, "GLView Bottom: " + glView.getBottom());

                View rootView2 = getWindow().getDecorView();
                Log.d(TAG, "=== Root View Dimensions ===");
                Log.d(TAG, "Root Width: " + rootView2.getWidth());
                Log.d(TAG, "Root Height: " + rootView2.getHeight());
            });
        }
    }

    /**
     * Setup the GL surface view with a Scene object (either preloaded or loaded from file).
     * This method handles both initialization paths without needing to know the source.
     *
     * @param scene the Scene object to render
     */
    private void setupGLSurfaceView(Scene scene) {
        glSurfaceView = findViewById(R.id.fullscreen_preview_gl_view);
        Log.d(TAG, "GLSurfaceView reference obtained: " + (glSurfaceView != null ? "SUCCESS" : "NULL"));

        if (glSurfaceView != null) {
            try {
                Log.d(TAG, "Configuring GLSurfaceView...");
                glSurfaceView.setEGLContextClientVersion(2);
                Log.d(TAG, "EGL context version set to 2");

                // Create a new renderer with the preloaded scene
                renderer = new EditSceneManager(this, scene);
                glSurfaceView.setRenderer(renderer);
                Log.d(TAG, "Renderer set");

                glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
                Log.d(TAG, "Render mode set to CONTINUOUSLY");

                Log.d(TAG, "Fullscreen preview GLSurfaceView configured successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error setting up fullscreen preview GLSurfaceView: " + e.getMessage(), e);
                finish();
            }
        } else {
            Log.e(TAG, "Fullscreen preview GLSurfaceView not found!");
            finish();
        }
    }

    /**
     * Setup the GL surface view with a scene file name (legacy behavior).
     * Loads the scene from disk using the provided file name.
     *
     * @param sceneFileName the name of the scene file to load
     */
    private void setupGLSurfaceView(String sceneFileName) {
        glSurfaceView = findViewById(R.id.fullscreen_preview_gl_view);
        Log.d(TAG, "GLSurfaceView reference obtained: " + (glSurfaceView != null ? "SUCCESS" : "NULL"));

        if (glSurfaceView != null) {
            try {
                Log.d(TAG, "Configuring GLSurfaceView...");
                glSurfaceView.setEGLContextClientVersion(2);
                Log.d(TAG, "EGL context version set to 2");

                // Create a new renderer with the file name (legacy behavior)
                renderer = new EditSceneManager(this, sceneFileName);
                glSurfaceView.setRenderer(renderer);
                Log.d(TAG, "Renderer set");

                glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
                Log.d(TAG, "Render mode set to CONTINUOUSLY");

                Log.d(TAG, "Fullscreen preview GLSurfaceView configured successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error setting up fullscreen preview GLSurfaceView: " + e.getMessage(), e);
                finish();
            }
        } else {
            Log.e(TAG, "Fullscreen preview GLSurfaceView not found!");
            finish();
        }
    }

    private void setupCloseButton() {
        ImageButton closeButton = findViewById(R.id.fullscreen_close_button);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                Log.d(TAG, "Close button clicked, exiting fullscreen preview");
                finish();
            });
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && renderer != null) {
            renderer.onGyroscopeChanged(event.values[0], event.values[1], event.values[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No action needed
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }
        if (renderer != null) {
            renderer.pause();
        }
        if (sensorManager != null && gyroscopeSensor != null) {
            sensorManager.unregisterListener(this, gyroscopeSensor);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");

        if (glSurfaceView != null) {
            glSurfaceView.onResume();
            Log.d(TAG, "GLSurfaceView resumed");
        }
        if (renderer != null) {
            renderer.resume();
            Log.d(TAG, "Renderer resumed");
        }
        if (sensorManager != null && gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
            Log.d(TAG, "Gyroscope listener registered");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Renderer cleanup is handled by onPause and GLSurfaceView lifecycle
    }
}
