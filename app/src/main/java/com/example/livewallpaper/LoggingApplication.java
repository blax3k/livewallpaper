package com.example.livewallpaper;

import android.app.Application;

import com.example.livewallpaper.logging.FileLoggingTree;
import com.example.livewallpaper.world.WorldStateManager;

import timber.log.Timber;

/**
 * Application class that initializes Timber and file logging for debugging purposes.
 * All log statements made through Timber will be written to a file in the app's
 * external files directory for debugging purposes.
 */
public class LoggingApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        initializeLogging();
        // Ensures the install timestamp is recorded on the very first launch.
        // All subsequent calls are no-ops (the timestamp is never overwritten).
        WorldStateManager.get(this);
    }

    private void initializeLogging() {
        // Plant a debug tree for console logging
        Timber.plant(new Timber.DebugTree());

        // Plant file logging tree for all builds
        Timber.plant(new FileLoggingTree(this));
    }
}
