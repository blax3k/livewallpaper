package com.example.livewallpaper.scene.models;

/**
 * Defines a flag in the pack's world-state vocabulary.
 * Flags are the backbone of the storytelling system — they are either active or inactive,
 * and their active state drives both scene selection scoring and sprite conditions.
 *
 * Flag definitions live at the pack level (flags.json), not per-scene.
 */
public class FlagData {
    /** Unique stable identifier used to reference this flag everywhere else. e.g. "morning", "cat_appears" */
    public String id;

    /** Human-readable display name for editor tooling. e.g. "Morning", "Cat Appears" */
    public String name;

    /** Whether this flag is active when the world state is first created (i.e., fresh install). */
    public boolean defaultActive = false;
}
