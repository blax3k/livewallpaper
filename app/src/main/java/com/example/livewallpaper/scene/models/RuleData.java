package com.example.livewallpaper.scene.models;

/**
 * Defines a rule in the storytelling system.
 *
 * Rules are evaluated each time the user views the live wallpaper (debounced to once every
 * 5 minutes). When all conditions in the condition group pass, all actions are executed.
 *
 * One-shot rules: if {@code oneShot} is true, the rule fires at most once. After firing,
 * the WorldStateManager marks it as fired and skips it in future evaluations.
 * The {@code fired} field is NOT stored here — it lives in WorldStateManager's persistent
 * store, keyed by {@code id}. This class is the immutable definition loaded from JSON.
 *
 * Rule definitions live at the pack level (rules.json).
 */
public class RuleData {
    /** Unique stable identifier for this rule. Used by WorldStateManager to track fired state. */
    public String id;

    /** Human-readable label for editor tooling. */
    public String name;

    /**
     * Condition groups the rule fires under. Checks within a group are combined by that group's
     * operator; the groups themselves are OR'd against each other, so the rule fires as soon as
     * any one group fully matches. Null or empty means the rule always fires (e.g., a time-of-day
     * setter with no preconditions).
     *
     * This is an array to match the web editor's {@code RuleDefinition.conditions}
     * (packages/types/src/index.ts) — the exact JSON the pack's rules.json carries.
     */
    public RuleConditionGroupData[] conditions;

    /** Actions executed when conditions pass. */
    public RuleActionData[] actions;

    /**
     * If true, this rule fires at most once per world state lifetime.
     * Use for milestone events (e.g., "cat appears for the first time").
     * If false, the rule re-evaluates every cycle (use for time-of-day flag setters, etc.).
     */
    public boolean oneShot = false;
}
