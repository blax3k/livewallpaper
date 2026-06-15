package com.example.livewallpaper.world;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.FlagData;

import java.util.List;
import java.util.Map;

/**
 * Single source of truth for all persistent storytelling world state.
 *
 * Stores and retrieves:
 *   - Install timestamp (set once on first run, never changes)
 *   - Last rule-evaluation timestamp (used for 5-minute debounce)
 *   - Per-flag state: active/inactive, when it was last activated/deactivated
 *   - Per-scene show counts
 *   - Per-rule fired state (for oneShot rules)
 *   - Last scene shown
 *
 * All persistent state lives in a dedicated SharedPreferences file ("WorldState"),
 * completely separate from WallpaperPreferences (which handles project/path config).
 *
 * Usage: obtain the singleton via WorldStateManager.get(context) from any thread.
 * State-mutating methods are synchronized to guard against concurrent access from the
 * GL thread (renderer) and the main thread (rule evaluation callback).
 *
 * Integration points (wired up in later stages):
 *   - Stage 3 (ScenePicker): reads isFlagActive(), getSceneShowCount()
 *   - Stage 4 (RulesEvaluator): reads/writes flag state, rule fired state, shouldEvaluateNow()
 *   - Stage 4 (SceneSwitchManager): calls recordSceneShown() after each scene transition
 *   - Stage 7 (Simulator): calls exportState(), resetState(), setFlagActive(), clearEvaluationCooldown()
 */
public class WorldStateManager {

    private static final String TAG = "WorldStateManager";
    private static final String PREFS_NAME = "WorldState";

    // Top-level keys
    private static final String KEY_INSTALL_TIMESTAMP  = "install_ts";
    private static final String KEY_LAST_EVAL_TS       = "last_eval_ts";
    private static final String KEY_LAST_SCENE_ID      = "last_scene_id";

    // Prefixes for per-entity keys
    private static final String PREFIX_FLAG_ACTIVE         = "flag_active_";
    private static final String PREFIX_FLAG_ACTIVATED_AT   = "flag_activated_at_";
    private static final String PREFIX_FLAG_DEACTIVATED_AT = "flag_deactivated_at_";
    private static final String PREFIX_SCENE_COUNT         = "scene_count_";
    private static final String PREFIX_RULE_FIRED          = "rule_fired_";

    /** Minimum gap between rule evaluations. Callers check shouldEvaluateNow() before evaluating. */
    public static final long EVALUATION_INTERVAL_MS = 5 * 60 * 1000L;

    private static WorldStateManager instance;

    private final SharedPreferences prefs;

    private WorldStateManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ensureInstallTimestamp();
    }

    /** Returns the singleton, creating it on first call. Safe to call from any thread. */
    public static synchronized WorldStateManager get(Context context) {
        if (instance == null) {
            instance = new WorldStateManager(context);
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Install timestamp
    // ─────────────────────────────────────────────────────────────────────────

    private void ensureInstallTimestamp() {
        if (prefs.getLong(KEY_INSTALL_TIMESTAMP, 0L) == 0L) {
            prefs.edit().putLong(KEY_INSTALL_TIMESTAMP, System.currentTimeMillis()).apply();
            TimberLog.d(TAG, "WorldState initialized — install timestamp recorded");
        }
    }

    /** Returns the epoch-millis timestamp recorded on first app launch. */
    public long getInstallTimestamp() {
        return prefs.getLong(KEY_INSTALL_TIMESTAMP, System.currentTimeMillis());
    }

    /** Returns whole hours elapsed since install. Used by the install_duration_hours rule condition. */
    public long getInstallDurationHours() {
        long elapsed = System.currentTimeMillis() - getInstallTimestamp();
        return elapsed / (1000L * 60L * 60L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule evaluation debounce
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if enough time has elapsed since the last evaluation to warrant
     * running the rules engine again. Called by LiveWallpaperSceneManager on resume.
     */
    public boolean shouldEvaluateNow() {
        long lastEval = prefs.getLong(KEY_LAST_EVAL_TS, 0L);
        return System.currentTimeMillis() - lastEval >= EVALUATION_INTERVAL_MS;
    }

    /** Record that a rule evaluation just occurred. Resets the debounce timer. */
    public synchronized void recordEvaluation() {
        prefs.edit().putLong(KEY_LAST_EVAL_TS, System.currentTimeMillis()).apply();
    }

    /**
     * Clears the evaluation cooldown so the next shouldEvaluateNow() call returns true.
     * Used by the simulator to force a fresh evaluation without waiting 5 minutes.
     */
    public synchronized void clearEvaluationCooldown() {
        prefs.edit().remove(KEY_LAST_EVAL_TS).apply();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Flag state
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns whether the given flag is currently active. Defaults to false if never set. */
    public boolean isFlagActive(String flagId) {
        return prefs.getBoolean(PREFIX_FLAG_ACTIVE + flagId, false);
    }

    /**
     * Activates a flag and records the current time as its activation timestamp.
     * No-op if the flag is already active (timestamps are not updated on redundant calls).
     */
    public synchronized void activateFlag(String flagId) {
        if (isFlagActive(flagId)) return;
        prefs.edit()
                .putBoolean(PREFIX_FLAG_ACTIVE + flagId, true)
                .putLong(PREFIX_FLAG_ACTIVATED_AT + flagId, System.currentTimeMillis())
                .apply();
        TimberLog.d(TAG, "Flag activated: " + flagId);
    }

    /**
     * Deactivates a flag and records the current time as its deactivation timestamp.
     * No-op if the flag is already inactive.
     */
    public synchronized void deactivateFlag(String flagId) {
        if (!isFlagActive(flagId)) return;
        prefs.edit()
                .putBoolean(PREFIX_FLAG_ACTIVE + flagId, false)
                .putLong(PREFIX_FLAG_DEACTIVATED_AT + flagId, System.currentTimeMillis())
                .apply();
        TimberLog.d(TAG, "Flag deactivated: " + flagId);
    }

    /**
     * Returns the epoch-millis when this flag was last activated, or -1 if it has never
     * been activated. Used by the time_since_flag_change rule condition.
     */
    public long getFlagActivatedAt(String flagId) {
        return prefs.getLong(PREFIX_FLAG_ACTIVATED_AT + flagId, -1L);
    }

    /**
     * Returns the epoch-millis when this flag was last deactivated, or -1 if it has never
     * been deactivated.
     */
    public long getFlagDeactivatedAt(String flagId) {
        return prefs.getLong(PREFIX_FLAG_DEACTIVATED_AT + flagId, -1L);
    }

    /**
     * Returns whole hours since this flag was last activated, or -1 if it has never been
     * activated. Used by the time_since_flag_change("activated") condition.
     */
    public long getTimeSinceFlagActivatedHours(String flagId) {
        long ts = getFlagActivatedAt(flagId);
        if (ts < 0L) return -1L;
        return (System.currentTimeMillis() - ts) / (1000L * 60L * 60L);
    }

    /**
     * Returns whole hours since this flag was last deactivated, or -1 if it has never been
     * deactivated. Used by the time_since_flag_change("deactivated") condition.
     */
    public long getTimeSinceFlagDeactivatedHours(String flagId) {
        long ts = getFlagDeactivatedAt(flagId);
        if (ts < 0L) return -1L;
        return (System.currentTimeMillis() - ts) / (1000L * 60L * 60L);
    }

    /**
     * Initializes flags from the pack's FlagData definitions.
     * Only flags that have NO existing state in SharedPreferences are initialized —
     * flags that already have state (from a previous session) are left untouched.
     * Call this each time a pack is loaded so newly added flags get their defaults.
     *
     * Called by: SceneFileManager (Stage 3) after loading a pack's flag definitions.
     */
    public synchronized void initializeFlags(List<FlagData> flags) {
        if (flags == null || flags.isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        boolean anyNew = false;
        for (FlagData flag : flags) {
            String key = PREFIX_FLAG_ACTIVE + flag.id;
            if (!prefs.contains(key)) {
                editor.putBoolean(key, flag.defaultActive);
                anyNew = true;
                TimberLog.d(TAG, "Initialized new flag: " + flag.id + " = " + flag.defaultActive);
            }
        }
        if (anyNew) editor.apply();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene show counts
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns how many times this scene has been shown since install. */
    public int getSceneShowCount(String sceneId) {
        return prefs.getInt(PREFIX_SCENE_COUNT + sceneId, 0);
    }

    /**
     * Increments the show count for this scene and records it as the last shown scene.
     * Called by: SceneSwitchManager (Stage 3) after each successful scene transition.
     */
    public synchronized void recordSceneShown(String sceneId) {
        int count = getSceneShowCount(sceneId) + 1;
        prefs.edit()
                .putInt(PREFIX_SCENE_COUNT + sceneId, count)
                .putString(KEY_LAST_SCENE_ID, sceneId)
                .apply();
        TimberLog.d(TAG, "Scene shown: " + sceneId + " (total: " + count + ")");
    }

    /** Returns the sceneId of the last scene shown, or null if no scene has been shown yet. */
    public String getLastShownSceneId() {
        return prefs.getString(KEY_LAST_SCENE_ID, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule fired state (for oneShot rules)
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns true if this oneShot rule has already fired and should be skipped. */
    public boolean isRuleFired(String ruleId) {
        return prefs.getBoolean(PREFIX_RULE_FIRED + ruleId, false);
    }

    /**
     * Marks a oneShot rule as fired so it is skipped in future evaluations.
     * Called by: RulesEvaluator (Stage 4) after executing a oneShot rule's actions.
     */
    public synchronized void markRuleFired(String ruleId) {
        prefs.edit().putBoolean(PREFIX_RULE_FIRED + ruleId, true).apply();
        TimberLog.d(TAG, "Rule marked as fired: " + ruleId);
    }

    /**
     * Resets the fired state of a oneShot rule, allowing it to fire again.
     * Intended for seasonal/repeating milestones where the rule needs to re-arm.
     */
    public synchronized void resetRuleFiredState(String ruleId) {
        prefs.edit().remove(PREFIX_RULE_FIRED + ruleId).apply();
        TimberLog.d(TAG, "Rule fired state reset: " + ruleId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Simulator / debug support
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of all current world state as a key→value map.
     * Used by the web editor simulator to display current state.
     * Keys use the same prefixes as the internal SharedPreferences keys.
     */
    public Map<String, ?> exportState() {
        return prefs.getAll();
    }

    /**
     * Wipes all world state and re-records the install timestamp as "now".
     * Simulates a fresh install. Use in the simulator to test initial-state behavior.
     */
    public synchronized void resetState() {
        prefs.edit().clear().apply();
        ensureInstallTimestamp();
        TimberLog.d(TAG, "WorldState fully reset");
    }

    /**
     * Directly sets a flag's active state and records appropriate timestamps.
     * Delegates to activateFlag / deactivateFlag so timestamp behavior is consistent.
     * Provided as a convenience for the simulator.
     */
    public void setFlagActive(String flagId, boolean active) {
        if (active) {
            activateFlag(flagId);
        } else {
            deactivateFlag(flagId);
        }
    }
}
