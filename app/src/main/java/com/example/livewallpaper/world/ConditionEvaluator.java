package com.example.livewallpaper.world;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.RuleConditionData;
import com.example.livewallpaper.scene.models.RuleConditionGroupData;

import java.util.Calendar;

/**
 * Evaluates {@link RuleConditionGroupData} condition trees against the current world state.
 *
 * Shared by {@link RulesEvaluator} (rule trigger conditions) and
 * {@link SpriteConditionApplicator} (per-sprite visibility/modification conditions)
 * so the evaluation logic is never duplicated.
 *
 * All condition types defined in {@link RuleConditionData} are supported:
 * flag_active, flag_inactive, time_of_day, day_of_week, scene_count,
 * install_duration_hours, time_since_flag_change.
 */
public class ConditionEvaluator {
    private static final String TAG = "ConditionEvaluator";

    private final WorldStateManager worldState;

    public ConditionEvaluator(WorldStateManager worldState) {
        this.worldState = worldState;
    }

    /**
     * Returns true if the condition group passes against the current world state.
     * A null group or a group with no checks always passes (unconditional).
     */
    public boolean evaluate(RuleConditionGroupData group) {
        if (group == null || group.checks == null || group.checks.length == 0) return true;

        boolean isOr = "OR".equalsIgnoreCase(group.operator);

        for (RuleConditionData check : group.checks) {
            boolean result = evaluateCondition(check);
            if (isOr && result) return true;    // OR: first true short-circuits
            if (!isOr && !result) return false; // AND: first false short-circuits
        }

        return !isOr; // OR with no trues = false; AND with no falses = true
    }

    // ─────────────────────────────────────────────────────────────────────────

    private boolean evaluateCondition(RuleConditionData c) {
        if (c == null || c.type == null) return false;

        switch (c.type) {
            case "flag_active":
                return worldState.isFlagActive(c.flagId);

            case "flag_inactive":
                return !worldState.isFlagActive(c.flagId);

            case "time_of_day":
                return isCurrentHourInRange(c.startHour, c.endHour);

            case "day_of_week":
                return currentDayMatches(c.daysOfWeek);

            case "scene_count":
                return compareInt(worldState.getSceneShowCount(c.sceneId), c.operator, c.intValue);

            case "install_duration_hours":
                return compareInt(worldState.getInstallDurationHours(), c.operator, c.intValue);

            case "time_since_flag_change": {
                long hours = "activated".equals(c.flagChangeType)
                        ? worldState.getTimeSinceFlagActivatedHours(c.flagId)
                        : worldState.getTimeSinceFlagDeactivatedHours(c.flagId);
                if (hours < 0) return false; // flag has never changed in this direction
                return compareInt(hours, c.operator, c.intValue);
            }

            default:
                TimberLog.w(TAG, "Unknown condition type '" + c.type + "' — treating as false");
                return false;
        }
    }

    /**
     * True if the current hour of day falls within [startHour, endHour).
     * Handles overnight ranges where startHour > endHour (e.g., night: 22–6).
     */
    private boolean isCurrentHourInRange(int startHour, int endHour) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (startHour <= endHour) {
            return hour >= startHour && hour < endHour;
        } else {
            return hour >= startHour || hour < endHour;
        }
    }

    /** True if today (0=Sunday … 6=Saturday) appears in daysOfWeek. */
    private boolean currentDayMatches(int[] daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.length == 0) return false;
        int today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
        for (int d : daysOfWeek) {
            if (d == today) return true;
        }
        return false;
    }

    private boolean compareInt(long actual, String operator, int expected) {
        if (operator == null) return false;
        switch (operator) {
            case ">=": return actual >= expected;
            case "<=": return actual <= expected;
            case "==": return actual == expected;
            case ">":  return actual >  expected;
            case "<":  return actual <  expected;
            default:
                TimberLog.w(TAG, "Unknown operator '" + operator + "' — treating as false");
                return false;
        }
    }
}
