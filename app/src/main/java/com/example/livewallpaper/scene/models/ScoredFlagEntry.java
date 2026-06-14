package com.example.livewallpaper.scene.models;

/**
 * Associates a flag with a scene-specific score weight.
 * When a flag in this list is active, its weight is added to the scene's total selection score.
 * Weights can be negative to penalise scenes when certain flags are active.
 */
public class ScoredFlagEntry {
    /** References a FlagData.id from the pack's flag definitions. */
    public String flagId;

    /**
     * Points this flag contributes to scene selection score when active.
     * Range: -100 to 100. Negative values make this scene less likely when the flag is active.
     */
    public int weight;
}
