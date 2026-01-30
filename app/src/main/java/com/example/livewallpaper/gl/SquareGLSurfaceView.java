package com.example.livewallpaper.gl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

/**
 * A custom GLSurfaceView that maintains a square aspect ratio.
 */
public class SquareGLSurfaceView extends GLSurfaceView {
    public SquareGLSurfaceView(Context context) {
        super(context);
    }

    public SquareGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Make the view square by using the smaller of width or height
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        int size = Math.min(width, height);

        setMeasuredDimension(size, size);
    }
}
