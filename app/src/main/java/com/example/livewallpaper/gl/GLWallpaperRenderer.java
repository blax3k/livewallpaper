package com.example.livewallpaper.gl;

/**
 * Interface for OpenGL wallpaper renderers.
 * Implementations should handle GL rendering and lifecycle callbacks.
 */
public interface GLWallpaperRenderer {
    /**
     * Called when the GL surface is created.
     * Initialize GL settings and resources here.
     */
    void onSurfaceCreated();

    /**
     * Called when the surface dimensions change.
     * Update viewport and projection matrices here.
     *
     * @param width  the new surface width
     * @param height the new surface height
     */
    void onSurfaceChanged(int width, int height);

    /**
     * Called every frame to render the wallpaper.
     */
    void onDrawFrame();

    /**
     * Called when the renderer is being destroyed.
     * Release GL resources here.
     */
    void onDestroy();

    void onScrollOffsetChanged(float offsetX);

    /**
     * Called when gyroscope data is updated.
     * Use this to move sprites based on device rotation.
     *
     * @param rotationX rotation around X axis in rad/s (pitch)
     * @param rotationY rotation around Y axis in rad/s (roll)
     * @param rotationZ rotation around Z axis in rad/s (yaw)
     */
    void onGyroscopeChanged(float rotationX, float rotationY, float rotationZ);

    /**
     * Called by the hosting service when rendering is about to start or visibility has resumed.
     * The renderer can use the provided timestamp to reset or initialize frame-timers and avoid
     * treating the long pause as a large delta-time.
     *
     * @param resumeTimeNs a recent System.nanoTime() timestamp from the caller; if <=0 the
     *                     renderer should choose a sane fallback.
     */
    void onRendererResume(long resumeTimeNs);

    /**
     * Called by the hosting service when rendering is being stopped or visibility is lost.
     * The renderer should invalidate any frame-timer state so a subsequent resume is explicit.
     */
    void onRendererPause();

    /**
     * Called when the renderer is being suspended (e.g., screen turned off).
     * The renderer should prepare for suspension but NOT release GL resources.
     * This allows fast resumption without reloading sprites and textures.
     */
    void onRendererSuspend();

    /**
     * Called when the renderer is resuming after suspension.
     * The renderer can restore any necessary state.
     * GL resources are still valid from the suspension.
     */
    void onRendererSuspendResume();

    /**
     * Called when a double-tap gesture is detected.
     * @param x the x-coordinate of the tap
     * @param y the y-coordinate of the tap
     */
    void onDoubleTap(float x, float y);
}

