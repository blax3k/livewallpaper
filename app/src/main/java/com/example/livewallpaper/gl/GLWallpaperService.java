package com.example.livewallpaper.gl;

import timber.log.Timber;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.service.wallpaper.WallpaperService;
import com.example.livewallpaper.logging.TimberLog;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import androidx.annotation.NonNull;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.managers.LiveWallpaperSceneManager;
import com.example.livewallpaper.sensors.MotionConfig;
import com.example.livewallpaper.ui.editor.managers.SceneFileManager;


/**
 * A WallpaperService that renders OpenGL content as a live wallpaper.
 * Manages EGL context creation and a dedicated render thread.
 */
public class GLWallpaperService extends WallpaperService {
    private static final String TAG = "GLWallpaperService";

    // Static reference to the current renderer instance for refreshing from outside
    private static LiveWallpaperSceneManager currentRenderer;

    @Override
    public Engine onCreateEngine() {
        // Initialize MotionConfig to load persisted settings from SharedPreferences
        MotionConfig.initialize(this);
        return new GLWallpaperEngine();
    }

    /**
     * Refresh the scene list in the currently running wallpaper.
     * Call this when scenes have been added, deleted, or reset in the app.
     *
     * @param context the application context
     */
    public static void refreshSceneList(Context context) {
        if (currentRenderer != null) {
            try {
                SceneFileManager sceneFileManager = new SceneFileManager(context, null);
                currentRenderer.refreshSceneList(sceneFileManager);
                TimberLog.d(TAG, "Scene list refreshed in wallpaper");
            } catch (Exception e) {
                TimberLog.e(TAG, "Error refreshing scene list in wallpaper: " + e.getMessage(), e);
            }
        } else {
            TimberLog.d(TAG, "Wallpaper not currently running, scene list will be loaded when wallpaper starts");
        }
    }

    private class GLWallpaperEngine extends Engine implements SurfaceHolder.Callback {
        private volatile boolean running = false;
        private Thread renderThread;

        // EGL objects
        private EGLDisplay eglDisplay;
        private EGLContext eglContext;
        private EGLSurface eglSurface;
        private EGLConfig eglConfig;

        // Track if EGL context has been initialized (survives pause/resume)
        private boolean eglContextInitialized = false;

        // Renderer
        private GLWallpaperRenderer renderer;

        // Gesture detection for double tap
        private GestureDetector gestureDetector;

        // Sensor management
        private SensorManager sensorManager;
        private Sensor gyroscopeSensor;
        // Flag to avoid double registration/unregistration
        private volatile boolean sensorRegistered = false;
        private final SensorEventListener sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && renderer != null) {
                    // Pass gyroscope data to renderer
                    // event.values[0] = rotation around X axis (pitch)
                    // event.values[1] = rotation around Y axis (roll)
                    // event.values[2] = rotation around Z axis (yaw)
                    renderer.onGyroscopeChanged(event.values[0], event.values[1], event.values[2]);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // Handle accuracy changes if needed
            }
        };

        private final GestureDetector.SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                TimberLog.d(TAG, "Double tap detected at x=" + e.getX() + ", y=" + e.getY());
                if (renderer != null) {
                    renderer.onDoubleTap(e.getX(), e.getY());
                }
                return true;
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            try {
                super.onCreate(surfaceHolder);
                surfaceHolder.addCallback(this);
                renderer = new LiveWallpaperSceneManager(GLWallpaperService.this);
                // Set the static reference so the scene list can be refreshed from outside
                currentRenderer = (LiveWallpaperSceneManager) renderer;

                // Enable touch events and initialize gesture detector for double tap detection
                setTouchEventsEnabled(true);
                gestureDetector = new GestureDetector(GLWallpaperService.this, gestureListener);

                // Initialize sensor manager for gyroscope input
                sensorManager = (SensorManager) GLWallpaperService.this.getSystemService(Context.SENSOR_SERVICE);
                if (sensorManager != null) {
                    gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                    if (gyroscopeSensor == null) {
                        TimberLog.w(TAG, "Gyroscope sensor not available on this device");
                    }
                }

                TimberLog.d(TAG, "Engine created");
            } catch (Exception e) {
                TimberLog.e(TAG, "Error in onCreate: " + e.getMessage(), e);
                throw e;
            }
        }

        @Override
        public void onDestroy() {
            // Clear the static reference
            if (currentRenderer == renderer) {
                currentRenderer = null;
            }

            // Ensure sensor listener is unregistered
            if (sensorManager != null && sensorRegistered) {
                sensorManager.unregisterListener(sensorEventListener);
                sensorRegistered = false;
                TimberLog.d(TAG, "Gyroscope sensor unregistered in onDestroy");
            }
            stopRendering();
            // Fully destroy EGL resources when the engine is being destroyed
            destroyEGL();
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                startRendering();
            } else {
                stopRendering();
            }
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            // Start rendering when surface is created and visible
            if (isVisible()) {
                startRendering();
            }
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            if (renderer != null) {
                renderer.onSurfaceChanged(width, height);
            } else {
                TimberLog.e(TAG, "Renderer is null in surfaceChanged");
            }
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            stopRendering();
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            if (renderer != null) {
                renderer.onScrollOffsetChanged(xOffset);
            }
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            // Pass touch events to gesture detector for double tap detection
            gestureDetector.onTouchEvent(event);
        }

        private boolean initEGL(SurfaceHolder holder) {
            try {
                // If we already have a context, just create a new surface and rebind
                if (eglContextInitialized && eglDisplay != null && eglContext != null) {
                    return resumeEGL(holder);
                }

                // First-time initialization: create display, context, and surface

                // Get the default EGL display
                eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                    TimberLog.e(TAG, "Failed to get EGL display");
                    return false;
                }

                // Initialize EGL
                int[] version = new int[2];
                if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                    TimberLog.e(TAG, "Failed to initialize EGL");
                    return false;
                }

                TimberLog.d(TAG, "EGL version: " + version[0] + "." + version[1]);

                // Configure EGL
                int[] attribList = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
                };

                EGLConfig[] configs = new EGLConfig[1];
                int[] numConfigs = new int[1];
                if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
                    TimberLog.e(TAG, "Failed to choose EGL config");
                    return false;
                }

                eglConfig = configs[0];
                if (eglConfig == null) {
                    TimberLog.e(TAG, "No EGL config available");
                    return false;
                }

                // Create GL context
                int[] attribListContext = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
                };
                eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, attribListContext, 0);
                if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
                    TimberLog.e(TAG, "Failed to create EGL context");
                    return false;
                }

                // Create GL surface
                int[] surfaceAttribs = { EGL14.EGL_NONE };
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, holder.getSurface(), surfaceAttribs, 0);
                if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                    TimberLog.e(TAG, "Failed to create EGL surface");
                    return false;
                }

                // Make the context current
                if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    TimberLog.e(TAG, "Failed to make EGL context current");
                    return false;
                }

                // Enable vsync (swap interval = 1 means sync to display refresh rate)
                EGL14.eglSwapInterval(eglDisplay, 1);

                eglContextInitialized = true;
                renderer.onSurfaceCreated();
                TimberLog.d(TAG, "EGL initialized successfully (first time)");
                return true;
            } catch (Exception e) {
                TimberLog.e(TAG, "initEGL failed", e);
                return false;
            }
        }

        /**
         * Resume EGL by creating a new surface with the existing context.
         * Called when we already have an initialized context from a previous session.
         */
        private boolean resumeEGL(SurfaceHolder holder) {
            try {
                // Create new GL surface
                int[] surfaceAttribs = { EGL14.EGL_NONE };
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, holder.getSurface(), surfaceAttribs, 0);
                if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                    TimberLog.e(TAG, "Failed to create EGL surface on resume");
                    return false;
                }

                // Make the context current with the new surface
                if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    TimberLog.e(TAG, "Failed to make EGL context current on resume");
                    return false;
                }

                // Enable vsync
                EGL14.eglSwapInterval(eglDisplay, 1);

                // Notify renderer it's resuming (GL resources are still valid)
                if (renderer != null) {
                    renderer.onRendererSuspendResume();
                }

                TimberLog.d(TAG, "EGL resumed successfully (context preserved)");
                return true;
            } catch (Exception e) {
                TimberLog.e(TAG, "resumeEGL failed", e);
                return false;
            }
        }

        /**
         * Pause EGL by destroying only the surface, keeping the context alive.
         * This preserves all GL resources (textures, shaders, etc.)
         */
        private void pauseEGL() {
            try {
                if (renderer != null) {
                    renderer.onRendererSuspend();
                }

                if (eglDisplay != null && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    // Unbind the context from the surface
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);

                    // Destroy only the surface, keep the context
                    if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface);
                        eglSurface = null;
                    }
                }

                TimberLog.d(TAG, "EGL paused (surface destroyed, context preserved)");
            } catch (Exception e) {
                TimberLog.e(TAG, "Error pausing EGL", e);
            }
        }

        /**
         * Fully release all EGL resources including context.
         * Called only when the engine is being destroyed.
         */
        private void destroyEGL() {
            try {
                if (renderer != null) {
                    renderer.onDestroy();
                }

                if (eglDisplay != null && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);

                    if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface);
                    }
                    if (eglContext != null && eglContext != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext);
                    }
                    EGL14.eglTerminate(eglDisplay);
                }

                TimberLog.d(TAG, "EGL fully destroyed");
            } catch (Exception e) {
                TimberLog.e(TAG, "Error destroying EGL", e);
            } finally {
                eglDisplay = null;
                eglContext = null;
                eglSurface = null;
                eglConfig = null;
                eglContextInitialized = false;
            }
        }

        private void startRendering() {
            if (running) {
                return;
            }

            // Notify renderer that rendering is about to start so it can reset its frame timer
            if (renderer != null) {
                renderer.onRendererResume(System.nanoTime());
            }

            // Register sensor listener now that rendering is starting
            if (!sensorRegistered && sensorManager != null && gyroscopeSensor != null) {
                sensorManager.registerListener(sensorEventListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_UI);
                sensorRegistered = true;
                TimberLog.d(TAG, "Gyroscope sensor registered");
            }

            running = true;
            renderThread = new Thread(() -> {
                SurfaceHolder holder = getSurfaceHolder();
                if (!initEGL(holder)) {
                    running = false;
                    return;
                }

                // Main render loop
                while (running) {
                    try {
                        // Render a frame
                        if (renderer != null) {
                            renderer.onDrawFrame();
                        }
                        // Swap buffers (vsync handled by eglSwapInterval)
                        EGL14.eglSwapBuffers(eglDisplay, eglSurface);
                    } catch (Exception e) {
                        TimberLog.e(TAG, "Rendering error", e);
                    }
                }

                pauseEGL();
            });
            renderThread.start();
            TimberLog.d(TAG, "Rendering started");
        }

        private void stopRendering() {
            // Notify renderer that rendering is stopping so it invalidates its frame timer
            if (renderer != null) {
                renderer.onRendererPause();
            }

            // Unregister sensor listener to stop sensor updates while not visible
            if (sensorRegistered && sensorManager != null) {
                sensorManager.unregisterListener(sensorEventListener);
                sensorRegistered = false;
                TimberLog.d(TAG, "Gyroscope sensor unregistered");
            }

            running = false;
            if (renderThread != null) {
                renderThread.interrupt();
                try {
                    renderThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                renderThread = null;
            }
            TimberLog.d(TAG, "Rendering stopped");
        }
    }
}
