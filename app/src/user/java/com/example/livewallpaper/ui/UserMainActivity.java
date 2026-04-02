package com.example.livewallpaper.ui;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.livewallpaper.R;
import com.example.livewallpaper.gl.GLWallpaperService;
import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.managers.AvatarSceneManager;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.managers.SceneFileManager;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class UserMainActivity extends AppCompatActivity {
    private static final String TAG = "UserMainActivity";
    private GLSurfaceView glSurfaceView;
    private AvatarSceneManager sceneManager;
    private boolean wallpaperIsSet = false;

    // Sensor management for gyroscope
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private volatile boolean sensorRegistered = false;
    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && sceneManager != null) {
                // Pass gyroscope data to scene manager
                // event.values[0] = rotation around X axis (pitch)
                // event.values[1] = rotation around Y axis (roll)
                // event.values[2] = rotation around Z axis (yaw)
                sceneManager.onGyroscopeChanged(event.values[0], event.values[1], event.values[2]);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Handle accuracy changes if needed
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_main);

        // Initialize MotionConfig
        MotionConfig.initialize(this);
        
        // Initialize sensor manager for gyroscope input
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (gyroscopeSensor == null) {
                TimberLog.w(TAG, "Gyroscope sensor not available on this device");
            }
        }

        // Initialize bundled scenes
        initializePersistentScenes();

        // Check if the live wallpaper is currently set
        wallpaperIsSet = isLiveWallpaperSet();
        TimberLog.d(TAG, "Live wallpaper is " + (wallpaperIsSet ? "SET" : "NOT SET"));

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

        // Create scene manager with appropriate scene based on wallpaper status
        String sceneToLoad = wallpaperIsSet ? "avatar_set.json" : "avatar_unset.json";
        sceneManager = new AvatarSceneManager(this, sceneToLoad);
        TimberLog.d(TAG, "Loaded scene: " + sceneToLoad);

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

        // Check if wallpaper status changed (e.g., user just set the wallpaper)
        boolean currentWallpaperStatus = isLiveWallpaperSet();
        if (currentWallpaperStatus != wallpaperIsSet) {
            wallpaperIsSet = currentWallpaperStatus;
            TimberLog.d(TAG, "Wallpaper status changed to: " + (wallpaperIsSet ? "SET" : "NOT SET"));

            // CRITICAL FIX: Must recreate the entire GLSurfaceView and renderer
            // The renderer closure captures the sceneManager variable reference.
            // When we reassign sceneManager, the OLD renderer still calls methods on the OLD destroyed instance.
            // Solution: Recreate the entire view hierarchy to create a fresh renderer closure with the new sceneManager.

            LinearLayout glViewLayout = findViewById(R.id.glViewLayout);
            if (glViewLayout != null && glSurfaceView != null && sceneManager != null) {
                // Pause the old GLSurfaceView
                glSurfaceView.onPause();

                // Destroy old scene manager
                sceneManager.onDestroy();

                // Remove the old GLSurfaceView from the layout
                glViewLayout.removeView(glSurfaceView);
                glSurfaceView = null;

                // Create new scene manager with appropriate scene
                String sceneToLoad = wallpaperIsSet ? "avatar_set.json" : "avatar_unset.json";
                sceneManager = new AvatarSceneManager(this, sceneToLoad);
                TimberLog.d(TAG, "Scene switched to: " + sceneToLoad);

                // Recreate the GLSurfaceView with a fresh renderer closure
                setupGLView();
            }
        }

        // Register sensor listener when activity becomes visible
        if (!sensorRegistered && sensorManager != null && gyroscopeSensor != null) {
            sensorManager.registerListener(sensorEventListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_UI);
            sensorRegistered = true;
            TimberLog.d(TAG, "Gyroscope sensor registered");
        }

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

        // Unregister sensor listener to stop sensor updates while not visible
        if (sensorRegistered && sensorManager != null) {
            sensorManager.unregisterListener(sensorEventListener);
            sensorRegistered = false;
            TimberLog.d(TAG, "Gyroscope sensor unregistered");
        }

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

    /**
     * Detects if the live wallpaper is currently set as the active wallpaper.
     *
     * @return true if GLWallpaperService is the current wallpaper, false otherwise
     */
    private boolean isLiveWallpaperSet() {
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
            ComponentName activeWallpaper = wallpaperManager.getWallpaperInfo().getComponent();
            ComponentName ourWallpaper = new ComponentName(this, GLWallpaperService.class);

            boolean isSet = activeWallpaper != null && activeWallpaper.equals(ourWallpaper);
            TimberLog.d(TAG, "Wallpaper check - Active: " + activeWallpaper + ", Ours: " + ourWallpaper + ", IsSet: " + isSet);
            return isSet;
        } catch (Exception e) {
            // If there's an error (e.g., no wallpaper set), return false
            TimberLog.d(TAG, "Error checking wallpaper status (likely no wallpaper set): " + e.getMessage());
            return false;
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
