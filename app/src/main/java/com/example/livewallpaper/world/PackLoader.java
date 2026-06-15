package com.example.livewallpaper.world;

import android.content.Context;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.FlagData;
import com.example.livewallpaper.scene.models.RuleData;
import com.google.gson.Gson;

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
 * Currently reads only from the app bundle (assets/flags.json, assets/rules.json).
 * A future extension will support per-project packs downloaded from the web editor.
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

    public PackLoader(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
    }

    /**
     * Loads flag definitions from assets/flags.json.
     * Returns an empty list if the file is missing or malformed.
     */
    public List<FlagData> loadFlags() {
        try (InputStream is = context.getAssets().open(FLAGS_ASSET);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            FlagData[] flags = gson.fromJson(reader, FlagData[].class);
            if (flags == null) return Collections.emptyList();
            TimberLog.d(TAG, "Loaded " + flags.length + " flag definitions");
            return Arrays.asList(flags);
        } catch (IOException e) {
            TimberLog.w(TAG, "flags.json not found or unreadable — no flags loaded: " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to parse flags.json", e);
            return Collections.emptyList();
        }
    }

    /**
     * Loads rule definitions from assets/rules.json.
     * Returns an empty list if the file is missing or malformed.
     */
    public List<RuleData> loadRules() {
        try (InputStream is = context.getAssets().open(RULES_ASSET);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            RuleData[] rules = gson.fromJson(reader, RuleData[].class);
            if (rules == null) return Collections.emptyList();
            TimberLog.d(TAG, "Loaded " + rules.length + " rule definitions");
            return Arrays.asList(rules);
        } catch (IOException e) {
            TimberLog.w(TAG, "rules.json not found or unreadable — no rules loaded: " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            TimberLog.e(TAG, "Failed to parse rules.json", e);
            return Collections.emptyList();
        }
    }
}
