package com.example.livewallpaper.world;

import android.content.Context;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.FlagData;
import com.example.livewallpaper.scene.models.RuleData;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Loads the pack-level storytelling assets (flag definitions and rules) from JSON.
 *
 * When a {@code packDir} is supplied (e.g. a downloaded project directory) the loader
 * reads from that directory first and falls back to the app bundle assets only if the
 * file is <em>absent</em> from the directory. This lets downloaded projects override the built-in
 * flags/rules without requiring a new APK build.
 *
 * A file that is present in the pack dir but fails to parse yields an empty list rather than the
 * bundled asset: once a pack declares its own rules, quietly substituting the built-in ones would
 * run a completely different world simulation while looking healthy.
 *
 * Neither file is required to exist — missing files return empty lists so the wallpaper
 * continues to function without any storytelling content configured.
 */
public class PackLoader {
    private static final String TAG = "PackLoader";

    static final String FLAGS_ASSET = "flags.json";
    static final String RULES_ASSET = "rules.json";

    private final Context context;
    private final Gson gson;
    private final File packDir;

    public PackLoader(Context context) {
        this(context, null);
    }

    /**
     * @param context application context
     * @param packDir optional external directory; if non-null and the file exists there,
     *                it is read instead of the bundled asset
     */
    public PackLoader(Context context, File packDir) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.packDir = packDir;
    }

    /**
     * Loads flag definitions. Prefers {@code packDir/flags.json} when available,
     * otherwise falls back to {@code assets/flags.json}.
     */
    public List<FlagData> loadFlags() {
        return loadArray(FLAGS_ASSET, FlagData[].class, "flag");
    }

    /**
     * Loads rule definitions. Prefers {@code packDir/rules.json} when available,
     * otherwise falls back to {@code assets/rules.json}.
     */
    public List<RuleData> loadRules() {
        return loadArray(RULES_ASSET, RuleData[].class, "rule");
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private <T> List<T> loadArray(String filename, Class<T[]> type, String label) {
        // Try external pack directory first
        if (packDir != null) {
            File file = new File(packDir, filename);
            if (file.exists()) {
                try (InputStream is = new FileInputStream(file);
                        InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    T[] items = gson.fromJson(reader, type);
                    if (items == null) return Collections.emptyList();
                    TimberLog.d(TAG, "Loaded " + items.length + " " + label + " definitions from " + file.getAbsolutePath());
                    return Arrays.asList(items);
                } catch (Exception e) {
                    // The pack shipped this file, so it is the authority — do NOT fall back to the
                    // bundled asset. Silently running another pack's rules looks like everything is
                    // fine while the wallpaper behaves nothing like the project the user authored;
                    // running no rules at all is the visible failure this deserves.
                    TimberLog.e(TAG, "Failed to parse " + filename + " from pack dir " + file.getAbsolutePath()
                            + " — no " + label + "s loaded (NOT falling back to the bundled asset)", e);
                    return Collections.emptyList();
                }
            }
        }

        // Fall back to bundled asset
        try (InputStream is = context.getAssets().open(filename);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            T[] items = gson.fromJson(reader, type);
            if (items == null) return Collections.emptyList();
            TimberLog.d(TAG, "Loaded " + items.length + " " + label + " definitions from assets");
            return Arrays.asList(items);
        } catch (IOException e) {
            TimberLog.w(TAG, filename + " not found in assets — no " + label + "s loaded: " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to parse " + filename + " from assets", e);
            return Collections.emptyList();
        }
    }
}
