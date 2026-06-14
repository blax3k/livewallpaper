package com.example.livewallpaper.scene.models;

/**
 * A single condition check within a rule.
 * The {@code type} field is a discriminator — only the fields relevant to that type need to be set.
 *
 * <pre>
 * Type                        Required fields
 * ─────────────────────────── ────────────────────────────────────────────────────────────────
 * "flag_active"               flagId
 * "flag_inactive"             flagId
 * "time_of_day"               startHour, endHour  (0–23 inclusive; handles overnight wrap)
 * "day_of_week"               daysOfWeek  (0=Sunday … 6=Saturday)
 * "scene_count"               sceneId, operator, intValue
 * "install_duration_hours"    operator, intValue
 * "time_since_flag_change"    flagId, flagChangeType ("activated" | "deactivated"), operator, intValue
 * </pre>
 *
 * {@code operator} values: ">=", "<=", "==", ">", "<"
 */
public class RuleConditionData {
    public String type;

    /** Flag identifier. Used by: flag_active, flag_inactive, time_since_flag_change. */
    public String flagId;

    /**
     * Whether to measure time since the flag was activated or deactivated.
     * Values: "activated" | "deactivated". Used by: time_since_flag_change.
     */
    public String flagChangeType;

    /** Scene identifier. Used by: scene_count. */
    public String sceneId;

    /** Comparison operator for numeric checks. Used by: scene_count, install_duration_hours, time_since_flag_change. */
    public String operator;

    /** Numeric value for comparison. Used by: scene_count, install_duration_hours, time_since_flag_change. */
    public int intValue;

    /** Start of time-of-day window (0–23). Used by: time_of_day. */
    public int startHour;

    /** End of time-of-day window (0–23, inclusive; wrap-around supported for e.g. 22–6). Used by: time_of_day. */
    public int endHour;

    /** Days of week to match (0=Sunday … 6=Saturday). Used by: day_of_week. */
    public int[] daysOfWeek;
}
