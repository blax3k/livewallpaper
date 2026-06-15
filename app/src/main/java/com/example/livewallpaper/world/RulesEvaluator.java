package com.example.livewallpaper.world;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.RuleActionData;
import com.example.livewallpaper.scene.models.RuleConditionData;
import com.example.livewallpaper.scene.models.RuleConditionGroupData;
import com.example.livewallpaper.scene.models.RuleData;

import java.util.Calendar;
import java.util.List;

/**
 * Evaluates a list of rules against the current world state and fires their actions.
 *
 * <h3>Evaluation cycle</h3>
 * Call {@link #evaluate(List)} once per evaluation window (every 5 minutes, gated by
 * {@link WorldStateManager#shouldEvaluateNow()}). Rules are evaluated in list order.
 *
 * <h3>Rule behaviour</h3>
 * <ul>
 *   <li>If a rule's {@code conditions} group passes, all its {@code actions} are executed.</li>
 *   <li>If {@code oneShot} is true and the rule has already fired (tracked by
 *       {@link WorldStateManager#isRuleFired}), the rule is permanently skipped.</li>
 *   <li>If {@code conditions} is null or has no checks, the rule always fires.</li>
 * </ul>
 *
 * <h3>Condition types</h3>
 * See {@link com.example.livewallpaper.scene.models.RuleConditionData} for full field docs.
 * <pre>
 * "flag_active"              — flagId is currently active
 * "flag_inactive"            — flagId is currently inactive
 * "time_of_day"              — current hour in [startHour, endHour) with overnight wrap
 * "day_of_week"              — today matches one of daysOfWeek (0=Sun … 6=Sat)
 * "scene_count"              — scene show count satisfies operator+intValue
 * "install_duration_hours"   — hours since install satisfies operator+intValue
 * "time_since_flag_change"   — hours since flag was activated/deactivated satisfies operator+intValue
 * </pre>
 *
 * <h3>Action types</h3>
 * <pre>
 * "activate_flag"    — sets flagId active (no-op if already active)
 * "deactivate_flag"  — sets flagId inactive (no-op if already inactive)
 * </pre>
 */
public class RulesEvaluator {
    private static final String TAG = "RulesEvaluator";

    private final WorldStateManager worldState;

    public RulesEvaluator(WorldStateManager worldState) {
        this.worldState = worldState;
    }

    /**
     * Evaluates all rules in order. Should only be called when
     * {@link WorldStateManager#shouldEvaluateNow()} returns true.
     *
     * @param rules the rules to evaluate, in priority order
     */
    public void evaluate(List<RuleData> rules) {
        if (rules == null || rules.isEmpty()) return;

        int fired = 0;
        for (RuleData rule : rules) {
            if (evaluateRule(rule)) fired++;
        }
        TimberLog.d(TAG, "Evaluated " + rules.size() + " rules, " + fired + " fired");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule evaluation
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns true if the rule fired this cycle. */
    private boolean evaluateRule(RuleData rule) {
        if (rule.id == null || rule.id.isEmpty()) {
            TimberLog.w(TAG, "Skipping rule with missing id");
            return false;
        }

        // oneShot rules skip evaluation once they've fired
        if (rule.oneShot && worldState.isRuleFired(rule.id)) {
            return false;
        }

        // Null/empty conditions mean the rule always fires
        if (!conditionsPassed(rule.conditions)) {
            return false;
        }

        // Execute all actions
        if (rule.actions != null) {
            for (RuleActionData action : rule.actions) {
                fireAction(rule.id, action);
            }
        }

        // Mark oneShot rules so they don't fire again
        if (rule.oneShot) {
            worldState.markRuleFired(rule.id);
        }

        TimberLog.d(TAG, "Rule fired: " + rule.id + (rule.name != null ? " (" + rule.name + ")" : ""));
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Condition evaluation
    // ─────────────────────────────────────────────────────────────────────────

    private boolean conditionsPassed(RuleConditionGroupData group) {
        if (group == null || group.checks == null || group.checks.length == 0) return true;

        boolean isOr = "OR".equalsIgnoreCase(group.operator);

        for (RuleConditionData check : group.checks) {
            boolean result = evaluateCondition(check);
            if (isOr && result) return true;   // OR: first true short-circuits
            if (!isOr && !result) return false; // AND: first false short-circuits
        }

        return !isOr; // OR with no trues = false; AND with no falses = true
    }

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
                // -1 means the flag has never changed in this direction — condition fails
                if (hours < 0) return false;
                return compareInt(hours, c.operator, c.intValue);
            }

            default:
                TimberLog.w(TAG, "Unknown condition type '" + c.type + "' — treating as false");
                return false;
        }
    }

    /**
     * Returns true if the current hour of day falls within [startHour, endHour).
     * Handles overnight ranges where startHour > endHour (e.g., night: 22–6).
     */
    private boolean isCurrentHourInRange(int startHour, int endHour) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (startHour <= endHour) {
            return hour >= startHour && hour < endHour;
        } else {
            // Overnight wrap: e.g., 22 to 6 → hour >= 22 OR hour < 6
            return hour >= startHour || hour < endHour;
        }
    }

    /**
     * Returns true if today (0=Sunday … 6=Saturday) is in the given array.
     * Returns false for null/empty arrays.
     */
    private boolean currentDayMatches(int[] daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.length == 0) return false;
        // Calendar.DAY_OF_WEEK: Sunday=1 … Saturday=7; our convention: Sunday=0 … Saturday=6
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
            case ">":  return actual > expected;
            case "<":  return actual < expected;
            default:
                TimberLog.w(TAG, "Unknown operator '" + operator + "' — treating as false");
                return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Action execution
    // ─────────────────────────────────────────────────────────────────────────

    private void fireAction(String ruleId, RuleActionData action) {
        if (action == null || action.type == null) return;

        switch (action.type) {
            case "activate_flag":
                worldState.activateFlag(action.flagId);
                break;

            case "deactivate_flag":
                worldState.deactivateFlag(action.flagId);
                break;

            default:
                TimberLog.w(TAG, "Rule '" + ruleId + "': unknown action type '" + action.type + "'");
        }
    }
}
