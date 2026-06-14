package com.example.livewallpaper.scene.models;

/**
 * A condition block attached to a sprite.
 * If the condition group passes, all listed modifications are applied to the sprite
 * before the scene is rendered.
 *
 * Sprites may have multiple condition blocks. They are evaluated in order and the
 * FIRST matching block wins (subsequent blocks are skipped for that render cycle).
 *
 * A sprite with no condition blocks always renders with its base configuration.
 * A sprite whose first (or only) condition block does not match renders with base config.
 * To hide a sprite by default and show it conditionally, make the first block a
 * "visible: false" block with an always-true condition, and add a second block
 * with the real condition and "visible: true".
 *
 * Condition blocks live in the scene JSON alongside the sprite they belong to.
 */
public class SpriteConditionData {
    /**
     * Conditions that must pass for this block's modifications to be applied.
     * If null or empty checks array, the block always matches (useful for defaults).
     */
    public RuleConditionGroupData conditions;

    /** Modifications to apply when conditions pass. */
    public SpriteModificationData[] modifications;
}
