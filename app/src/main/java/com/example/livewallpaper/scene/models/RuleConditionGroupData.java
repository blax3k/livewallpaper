package com.example.livewallpaper.scene.models;

/**
 * A logical group of conditions evaluated together.
 * Used both for rule trigger conditions and for sprite condition blocks.
 *
 * {@code operator} = "AND" — all checks must pass.
 * {@code operator} = "OR"  — at least one check must pass.
 */
public class RuleConditionGroupData {
    /** "AND" or "OR" */
    public String operator = "AND";

    public RuleConditionData[] checks;
}
