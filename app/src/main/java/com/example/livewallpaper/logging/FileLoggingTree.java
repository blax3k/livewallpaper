package com.example.livewallpaper.logging;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import timber.log.Timber;

/**
 * Timber tree implementation that writes logs to a file on external storage.
 * Logs are stored in the app's cache directory on external storage.
 */
public class FileLoggingTree extends Timber.Tree {

    private static final String LOG_DIR = "LiveWallpaperLogs";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private File logFile;
    private final Object lock = new Object();

    public FileLoggingTree() {
        initializeLogFile();
    }

    private void initializeLogFile() {
        try {
            File logDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), LOG_DIR);
            if (!logDir.exists()) {
                if (!logDir.mkdirs()) {
                    Log.e("FileLoggingTree", "Failed to create log directory: " + logDir.getAbsolutePath());
                    return;
                }
            }

            // Create a new log file for each day
            String date = FILE_DATE_FORMAT.format(new Date());
            logFile = new File(logDir, "app_logs_" + date + ".txt");

            // Create file if it doesn't exist
            if (!logFile.exists()) {
                if (!logFile.createNewFile()) {
                    Log.e("FileLoggingTree", "Failed to create log file: " + logFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            Log.e("FileLoggingTree", "Error initializing log file", e);
        }
    }

    @Override
    protected void log(int priority, String tag, String message, Throwable t) {
        if (logFile == null || !logFile.exists()) {
            initializeLogFile();
        }

        if (logFile == null) {
            return;
        }

        try {
            synchronized (lock) {
                String logMessage = formatLogMessage(priority, tag, message);
                writeToFile(logMessage);

                if (t != null) {
                    writeToFile(Log.getStackTraceString(t));
                }
            }
        } catch (Exception e) {
            Log.e("FileLoggingTree", "Error writing to log file", e);
        }
    }

    private String formatLogMessage(int priority, String tag, String message) {
        String priorityString = getPriorityString(priority);
        String timestamp = DATE_FORMAT.format(new Date());
        return String.format("[%s] %s [%s]: %s", timestamp, priorityString, tag, message);
    }

    private String getPriorityString(int priority) {
        switch (priority) {
            case Log.VERBOSE:
                return "V";
            case Log.DEBUG:
                return "D";
            case Log.INFO:
                return "I";
            case Log.WARN:
                return "W";
            case Log.ERROR:
                return "E";
            case Log.ASSERT:
                return "A";
            default:
                return "?";
        }
    }

    private void writeToFile(String message) {
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.append(message).append("\n");
            writer.flush();
        } catch (IOException e) {
            Log.e("FileLoggingTree", "Failed to write to log file", e);
        }
    }

    /**
     * Get the path to the log file for debugging purposes.
     *
     * @return The absolute path to the current log file, or null if not initialized.
     */
    public static String getLogFilePath() {
        try {
            File logDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), LOG_DIR);
            String date = FILE_DATE_FORMAT.format(new Date());
            File logFile = new File(logDir, "app_logs_" + date + ".txt");
            return logFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("FileLoggingTree", "Error getting log file path", e);
            return null;
        }
    }
}


