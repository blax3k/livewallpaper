package com.example.livewallpaper.logging;

import android.content.Context;
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
 * Logs are stored in the app-specific directory on external storage to avoid permission issues.
 */
public class FileLoggingTree extends Timber.Tree {

    private static final String TAG = "FileLoggingTree";
    private static final String LOG_DIR = "logs";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private final Context context;
    private File logFile;
    private final Object lock = new Object();

    public FileLoggingTree(Context context) {
        this.context = context.getApplicationContext();
        initializeLogFile();
        Log.d(TAG, "FileLoggingTree initialized. Log file: " + (logFile != null ? logFile.getAbsolutePath() : "null"));
    }

    private void initializeLogFile() {
        try {
            // Use getExternalFilesDir to avoid EACCES (Permission denied) on Android 10+
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) {
                Log.e(TAG, "External files directory is null");
                return;
            }

            File logDir = new File(externalDir, LOG_DIR);
            if (!logDir.exists()) {
                if (!logDir.mkdirs()) {
                    Log.e(TAG, "Failed to create log directory: " + logDir.getAbsolutePath());
                    return;
                }
            }

            // Create a new log file for each day
            String date = FILE_DATE_FORMAT.format(new Date());
            logFile = new File(logDir, "app_logs_" + date + ".txt");

            // Create file if it doesn't exist
            if (!logFile.exists()) {
                if (!logFile.createNewFile()) {
                    Log.e(TAG, "Failed to create log file: " + logFile.getAbsolutePath());
                } else {
                    Log.d(TAG, "Created new log file: " + logFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error initializing log file", e);
        }
    }

    @Override
    protected void log(int priority, String tag, String message, Throwable t) {
        // Always attempt to initialize if logFile is null
        if (logFile == null) {
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
            Log.e(TAG, "Error writing to log file", e);
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
        if (logFile == null) return;
        
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.append(message).append("\n");
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file", e);
        }
    }

    /**
     * Get the path to the log file for debugging purposes.
     *
     * @param context The context to use for retrieving the directory.
     * @return The absolute path to the current log file, or null if not initialized.
     */
    public static String getLogFilePath(Context context) {
        try {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) return null;
            
            File logDir = new File(externalDir, LOG_DIR);
            String date = FILE_DATE_FORMAT.format(new Date());
            File logFile = new File(logDir, "app_logs_" + date + ".txt");
            return logFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error getting log file path", e);
            return null;
        }
    }
}
