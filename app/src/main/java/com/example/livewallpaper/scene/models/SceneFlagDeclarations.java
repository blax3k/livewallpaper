package com.example.livewallpaper.scene.models;

/**
 * Declares how a scene participates in the flag-based scene selection system.
 *
 * Selection algorithm:
 *   1. Exclude scene if any flag in {@code excluded} is currently active.
 *   2. Exclude scene if any flag in {@code required} is NOT currently active.
 *   3. Score remaining scenes: sum {@code weight} for each active flag in {@code scored}.
 *   4. Pick highest-scoring scene; break ties by fewest show-count, then random.
 */
public class SceneFlagDeclarations {
    /**
     * Scene is ineligible unless ALL of these flags are currently active.
     * Use for hard gating (e.g., scene only shows at night → require "night").
     */
    public String[] required;

    /**
     * Active flags in this list add their weight to this scene's selection score.
     * Weights may be negative. A scene with no active scored flags scores 0.
     */
    public ScoredFlagEntry[] scored;

    /**
     * Scene is ineligible if ANY of these flags are currently active.
     * Use to suppress scenes that would feel wrong given certain world states
     * (e.g., hide the "first cat visit" scene once "cat_lives_here" is active).
     */
    public String[] excluded;
}
