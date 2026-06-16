package com.example.livewallpaper.world;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.Sprite;
import com.example.livewallpaper.scene.models.SpriteConditionData;
import com.example.livewallpaper.scene.models.SpriteModificationData;

import java.util.List;

/**
 * Applies sprite-level condition blocks to a scene's sprites based on the current world state.
 *
 * <h3>When to call</h3>
 * Call {@link #applyToScene(Scene)} from the GL thread:
 * <ul>
 *   <li>When a new scene becomes active (scene name change detected in onDrawFrame).</li>
 *   <li>After rules evaluation fires and flag state may have changed (onRendererResume).</li>
 * </ul>
 *
 * <h3>Condition evaluation</h3>
 * Each sprite may define an ordered list of {@link SpriteConditionData} blocks. They are
 * tested in order and the <em>first matching block</em> wins — its modifications are applied
 * and the rest are skipped. If no block matches, the sprite renders with its base values.
 * Before applying, sprite state is always reset to its base values so modifications from a
 * previous evaluation cycle don't accumulate.
 *
 * <h3>Supported modifications</h3>
 * <ul>
 *   <li>{@code visible}            — show or hide the sprite entirely.</li>
 *   <li>{@code position}           — override world-space X/Y position.</li>
 *   <li>{@code parallax}           — override parallax multiplier.</li>
 *   <li>{@code size}               — override width and height in world units.</li>
 *   <li>{@code texture_coordinates}— override UV coordinates (8 floats).</li>
 *   <li>{@code texture}            — texture resource swap (not yet implemented; requires
 *       async GL texture loading — planned for a future stage).</li>
 * </ul>
 *
 * <h3>Condition types</h3>
 * All types supported by {@link ConditionEvaluator} are available, but in practice sprite
 * conditions are most naturally expressed as flag checks (flag_active / flag_inactive).
 */
public class SpriteConditionApplicator {
    private static final String TAG = "SpriteConditionApplicator";

    private final ConditionEvaluator conditionEvaluator;

    public SpriteConditionApplicator(WorldStateManager worldState) {
        this.conditionEvaluator = new ConditionEvaluator(worldState);
    }

    /**
     * Evaluates and applies conditions to every sprite in the scene.
     * Must be called on the GL thread.
     *
     * @param scene the scene whose sprites should be conditioned
     */
    public void applyToScene(Scene scene) {
        if (scene == null) return;
        List<Sprite> sprites = scene.getSprites();
        synchronized (sprites) {
            for (Sprite sprite : sprites) {
                applyToSprite(sprite);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-sprite logic
    // ─────────────────────────────────────────────────────────────────────────

    private void applyToSprite(Sprite sprite) {
        // Always restore base state first so previous modifications don't persist
        sprite.resetConditionOverrides();

        SpriteConditionData[] conditions = sprite.getConditions();
        if (conditions == null || conditions.length == 0) return;

        for (SpriteConditionData block : conditions) {
            if (conditionEvaluator.evaluate(block.conditions)) {
                applyModifications(sprite, block.modifications);
                TimberLog.d(TAG, "Condition block matched for sprite '" + sprite.getName() + "'");
                return; // first match wins
            }
        }
    }

    private void applyModifications(Sprite sprite, SpriteModificationData[] modifications) {
        if (modifications == null) return;
        for (SpriteModificationData mod : modifications) {
            applyModification(sprite, mod);
        }
    }

    private void applyModification(Sprite sprite, SpriteModificationData mod) {
        if (mod == null || mod.type == null) return;

        switch (mod.type) {
            case "visible":
                sprite.setHidden(!mod.visible);
                break;

            case "position":
                sprite.applyConditionPosition(mod.positionX, mod.positionY);
                break;

            case "parallax":
                sprite.applyConditionParallax(mod.parallaxMultiplier);
                break;

            case "size":
                sprite.applyConditionSize(mod.width, mod.height);
                break;

            case "texture_coordinates":
                if (mod.texCoordinates != null && mod.texCoordinates.length == 8) {
                    sprite.applyConditionTexCoords(mod.texCoordinates);
                } else {
                    TimberLog.w(TAG, "texture_coordinates mod on '" + sprite.getName()
                            + "' has wrong length — expected 8 floats");
                }
                break;

            case "texture":
                // Texture resource swaps require async GL texture loading.
                // The data model and SpriteData already carry the textureResource field,
                // but wiring up the TextureManager reload is deferred to a future stage.
                TimberLog.w(TAG, "Texture swap conditions are not yet implemented (sprite: "
                        + sprite.getName() + ")");
                break;

            default:
                TimberLog.w(TAG, "Unknown modification type '" + mod.type
                        + "' on sprite '" + sprite.getName() + "'");
        }
    }
}
