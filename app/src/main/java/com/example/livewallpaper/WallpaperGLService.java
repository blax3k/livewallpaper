package com.example.livewallpaper;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import androidx.annotation.NonNull;

/**
 * A WallpaperService that renders OpenGL content as a live wallpaper.
 * Manages EGL context creation and a dedicated render thread.
 */
public class WallpaperGLService extends WallpaperService {
    private static final String TAG = "WallpaperGLService";

    @Override
    public Engine onCreateEngine() {
        return new GLWallpaperEngine();
    }

    private class GLWallpaperEngine extends Engine implements SurfaceHolder.Callback {
        private volatile boolean running = false;
        private Thread renderThread;

        // EGL objects
        private EGLDisplay eglDisplay;
        private EGLContext eglContext;
        private EGLSurface eglSurface;
        private EGLConfig eglConfig;

        // Renderer
        private GLWallpaperRenderer renderer;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            try {
                super.onCreate(surfaceHolder);
                surfaceHolder.addCallback(this);
                renderer = new SimpleRenderer();
                setTouchEventsEnabled(false);
                Log.d(TAG, "Engine created");
            } catch (Exception e) {
                Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
                throw e;
            }
        }

        @Override
        public void onDestroy() {
            stopRendering();
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
                Log.e(TAG, "Renderer is null in surfaceChanged");
            }
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            stopRendering();
        }

        private boolean initEGL(SurfaceHolder holder) {
            try {
                // Get the default EGL display
                eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                    Log.e(TAG, "Failed to get EGL display");
                    return false;
                }

                // Initialize EGL
                int[] version = new int[2];
                if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                    Log.e(TAG, "Failed to initialize EGL");
                    return false;
                }

                Log.d(TAG, "EGL version: " + version[0] + "." + version[1]);

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
                    Log.e(TAG, "Failed to choose EGL config");
                    return false;
                }

                eglConfig = configs[0];
                if (eglConfig == null) {
                    Log.e(TAG, "No EGL config available");
                    return false;
                }

                // Create GL context
                int[] attribListContext = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
                };
                eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, attribListContext, 0);
                if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
                    Log.e(TAG, "Failed to create EGL context");
                    return false;
                }

                // Create GL surface
                int[] surfaceAttribs = { EGL14.EGL_NONE };
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, holder.getSurface(), surfaceAttribs, 0);
                if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                    Log.e(TAG, "Failed to create EGL surface");
                    return false;
                }

                // Make the context current
                if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    Log.e(TAG, "Failed to make EGL context current");
                    return false;
                }

                renderer.onSurfaceCreated();
                Log.d(TAG, "EGL initialized successfully");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "initEGL failed", e);
                return false;
            }
        }

        private void releaseEGL() {
            try {
                if (renderer != null) {
                    renderer.onDestroy();
                }

                if (eglDisplay != null && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);

                    if (eglSurface != null) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface);
                    }
                    if (eglContext != null) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext);
                    }
                    EGL14.eglTerminate(eglDisplay);
                }

                Log.d(TAG, "EGL released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing EGL", e);
            } finally {
                eglDisplay = null;
                eglContext = null;
                eglSurface = null;
                eglConfig = null;
            }
        }

        private void startRendering() {
            if (running) {
                return;
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
                        renderer.onDrawFrame();

                        // Swap buffers
                        EGL14.eglSwapBuffers(eglDisplay, eglSurface);

                        // Sleep to throttle to approximately 60 FPS
                        //noinspection BusyWait
                        Thread.sleep(16);
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Rendering error", e);
                    }
                }

                releaseEGL();
            });
            renderThread.start();
            Log.d(TAG, "Rendering started");
        }

        private void stopRendering() {
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
            Log.d(TAG, "Rendering stopped");
        }
    }
}

