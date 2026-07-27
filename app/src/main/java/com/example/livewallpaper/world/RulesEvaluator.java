package com.example.livewallpaper.world;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.RuleActionData;
import com.example.livewallpaper.scene.models.RuleData;

import java.util.List;

/**
 * Evaluates a list of rules against the current world state and fires their actions.
 * Condition evaluation is delegated to {@link ConditionEvaluator}.
 *
 * <h3>Evaluation cycle</h3>
 * Call {@link #evaluate(List)} once per evaluation window (every 5 minutes, gated by
 * {@link WorldStateManager#shouldEvaluateNow()}). Rules are evaluated in list order.
 *
 * <h3>Rule behaviour</h3>
 * <ul>
 *   <li>If any of a rule's {@code conditions} groups passes, all its {@code actions} are executed.</li>
 *   <li>If {@code oneShot} is true and the rule has already fired, it is permanently skipped.</li>
 *   <li>If {@code conditions} is null or empty, the rule always fires.</li>
 * </ul>
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
    private final ConditionEvaluator conditionEvaluator;

    public RulesEvaluator(WorldStateManager worldState) {
        this.worldState = worldState;
        this.conditionEvaluator = new ConditionEvaluator(worldState);
    }

    /**
     * Evaluates all rules in order. Should only be called when
     * {@link WorldStateManager#shouldEvaluateNow()} returns true.
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

    /** Returns true if the rule fired this cycle. */
    private boolean evaluateRule(RuleData rule) {
        if (rule.id == null || rule.id.isEmpty()) {
            TimberLog.w(TAG, "Skipping rule with missing id");
            return false;
        }

        if (rule.oneShot && worldState.isRuleFired(rule.id)) {
            return false;
        }

        if (!conditionEvaluator.evaluateAny(rule.conditions)) {
            return false;
        }

        if (rule.actions != null) {
            for (RuleActionData action : rule.actions) {
                fireAction(rule.id, action);
            }
        }

        if (rule.oneShot) {
            worldState.markRuleFired(rule.id);
        }

        TimberLog.d(TAG, "Rule fired: " + rule.id + (rule.name != null ? " (" + rule.name + ")" : ""));
        return true;
    }

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
