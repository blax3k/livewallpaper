package com.example.livewallpaper.scene.models;

/**
 * An action executed when a rule's conditions are satisfied.
 *
 * <pre>
 * Type               Required fields
 * ────────────────── ───────────────
 * "activate_flag"    flagId
 * "deactivate_flag"  flagId
 * </pre>
 */
public class RuleActionData {
    public String type;

    /** Flag to activate or deactivate. Used by: activate_flag, deactivate_flag. */
    public String flagId;
}
