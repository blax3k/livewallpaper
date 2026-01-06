package com.example.livewallpaper;

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
}


