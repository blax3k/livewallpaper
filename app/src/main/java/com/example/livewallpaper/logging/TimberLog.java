package com.example.livewallpaper.logging;

import timber.log.Timber;

/**
 * Convenience wrapper around Timber that supports the legacy TimberLog.d(tag, message) format.
 *
 * Usage:
 *   TimberLog.d("MyTag", "Message");  // with custom tag
 *   TimberLog.i("MyTag", "Message");  // info level
 *   Timber.d("Message");              // without tag (uses calling class)
 */
public class TimberLog {

    /**
     * Log a verbose message with a custom tag.
     */
    public static void v(String tag, String message) {
        Timber.tag(tag).v(message);
    }

    /**
     * Log a debug message with a custom tag.
     */
    public static void d(String tag, String message) {
        Timber.tag(tag).d(message);
    }

    /**
     * Log an info message with a custom tag.
     */
    public static void i(String tag, String message) {
        Timber.tag(tag).i(message);
    }

    /**
     * Log a warning message with a custom tag.
     */
    public static void w(String tag, String message) {
        Timber.tag(tag).w(message);
    }

    /**
     * Log a warning message with a custom tag and throwable.
     */
    public static void w(String tag, Throwable t, String message) {
        Timber.tag(tag).w(t, message);
    }

    /**
     * Log a warning message with a custom tag and throwable (message-throwable order).
     */
    public static void w(String tag, String message, Throwable t) {
        Timber.tag(tag).w(t, message);
    }

    /**
     * Log an error message with a custom tag.
     */
    public static void e(String tag, String message) {
        Timber.tag(tag).e(message);
    }

    /**
     * Log an error message with a custom tag and throwable (throwable-message order).
     */
    public static void e(String tag, Throwable t, String message) {
        Timber.tag(tag).e(t, message);
    }

    /**
     * Log an error message with a custom tag and throwable (message-throwable order).
     * This matches the pattern of Log.e(tag, message, throwable).
     */
    public static void e(String tag, String message, Throwable t) {
        Timber.tag(tag).e(t, message);
    }

    /**
     * Log an assert message with a custom tag.
     */
    public static void wtf(String tag, String message) {
        Timber.tag(tag).wtf(message);
    }

    /**
     * Log an assert message with a custom tag and throwable (throwable-message order).
     */
    public static void wtf(String tag, Throwable t, String message) {
        Timber.tag(tag).wtf(t, message);
    }

    /**
     * Log an assert message with a custom tag and throwable (message-throwable order).
     */
    public static void wtf(String tag, String message, Throwable t) {
        Timber.tag(tag).wtf(t, message);
    }
}



