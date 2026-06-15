package com.example.livewallpaper.scene;

import com.example.livewallpaper.logging.TimberLog;
import com.example.livewallpaper.scene.models.Scene;
import com.example.livewallpaper.scene.models.SceneFlagDeclarations;
import com.example.livewallpaper.scene.models.ScoredFlagEntry;
import com.example.livewallpaper.world.WorldStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Selects the next scene to show using the flag-based scoring system.
 *
 * <h3>Selection algorithm</h3>
 * <ol>
 *   <li>Skip the scene that is currently displayed (no immediate repeats).</li>
 *   <li>Exclude any scene whose {@code excluded} flags list contains an active flag.</li>
 *   <li>Exclude any scene whose {@code required} flags list has a flag that is NOT active.</li>
 *   <li>Score each remaining scene: sum the {@code weight} for every active flag in
 *       its {@code scored} list (weights may be negative).</li>
 *   <li>Pick the highest-scoring scene. On a tie, prefer the scene shown fewest times
 *       (tracked by {@link WorldStateManager}). On a further tie, pick randomly.</li>
 * </ol>
 *
 * <h3>Backward compatibility</h3>
 * Scenes whose JSON has no {@code flags} field have a null {@link SceneFlagDeclarations}.
 * These scenes are always eligible (no required/excluded constraints) and score 0.
 * They will show up whenever no flag-aware scenes outscore them — which is the same
 * behaviour as the old random picker for packs that haven't been updated yet.
 *
 * <h3>Fallbacks</h3>
 * If every scene is excluded by flags, the restriction is relaxed and all scenes except
 * the current one are considered (score 0). This prevents a black screen when the world
 * state produces an impossible selection (e.g., conflicting required flags).
 */
public class ScenePicker {
    private static final String TAG = "ScenePicker";

    private final List<Scene> scenes;
    private final WorldStateManager worldState;
    private final Random random;

    public ScenePicker(List<Scene> scenes, WorldStateManager worldState) {
        this.scenes = new ArrayList<>(scenes);
        this.worldState = worldState;
        this.random = new Random();
    }

    /**
     * Returns the next scene to display.
     *
     * @param currentScene the scene currently on screen (will not be selected again
     *                     unless it is the only scene available)
     */
    public Scene getNextScene(Scene currentScene) {
        if (scenes.isEmpty()) {
            throw new IllegalStateException("No scenes available");
        }

        // Step 1: Build eligible set (pass required/excluded filter, skip current)
        List<Scene> eligible = buildEligibleList(currentScene);

        // Fallback: if all scenes failed the flag filter, ignore flag constraints and
        // use all scenes except the current one so the wallpaper never goes blank.
        if (eligible.isEmpty()) {
            TimberLog.w(TAG, "No scenes passed flag eligibility filter — relaxing constraints");
            for (Scene scene : scenes) {
                if (!scene.getSceneName().equals(currentScene.getSceneName())) {
                    eligible.add(scene);
                }
            }
        }

        // Absolute fallback: only one scene exists and it's the current one.
        if (eligible.isEmpty()) {
            TimberLog.w(TAG, "Only one scene available — returning it");
            return scenes.get(0);
        }

        // Step 2: Score eligible scenes and find the maximum score.
        int[] scores = new int[eligible.size()];
        int maxScore = Integer.MIN_VALUE;
        for (int i = 0; i < eligible.size(); i++) {
            scores[i] = computeScore(eligible.get(i));
            if (scores[i] > maxScore) maxScore = scores[i];
        }

        // Step 3: Collect scenes that share the top score.
        List<Scene> topScorers = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) {
            if (scores[i] == maxScore) topScorers.add(eligible.get(i));
        }

        logSelection(eligible, scores, maxScore, topScorers);

        // Step 4a: Only one winner — done.
        if (topScorers.size() == 1) {
            return topScorers.get(0);
        }

        // Step 4b: Tiebreaker — prefer the scene shown fewest times.
        int minCount = Integer.MAX_VALUE;
        for (Scene scene : topScorers) {
            int count = worldState.getSceneShowCount(scene.getSceneName());
            if (count < minCount) minCount = count;
        }

        List<Scene> leastShown = new ArrayList<>();
        for (Scene scene : topScorers) {
            if (worldState.getSceneShowCount(scene.getSceneName()) == minCount) {
                leastShown.add(scene);
            }
        }

        // Step 4c: Still tied — pick randomly.
        Scene selected = leastShown.get(random.nextInt(leastShown.size()));
        TimberLog.d(TAG, "Selected (tiebreak): " + selected.getSceneName()
                + " (showCount=" + worldState.getSceneShowCount(selected.getSceneName()) + ")");
        return selected;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all scenes that pass the required/excluded flag checks,
     * excluding the scene currently on screen.
     */
    private List<Scene> buildEligibleList(Scene currentScene) {
        List<Scene> eligible = new ArrayList<>();
        for (Scene scene : scenes) {
            if (scene.getSceneName().equals(currentScene.getSceneName())) continue;
            if (isEligible(scene)) eligible.add(scene);
        }
        return eligible;
    }

    /**
     * A scene is eligible if:
     *   - none of its excluded flags are active, AND
     *   - all of its required flags are active.
     * A scene with no flag declarations is always eligible.
     */
    private boolean isEligible(Scene scene) {
        SceneFlagDeclarations decl = scene.getFlagDeclarations();
        if (decl == null) return true;

        if (decl.excluded != null) {
            for (String flagId : decl.excluded) {
                if (worldState.isFlagActive(flagId)) {
                    TimberLog.d(TAG, "Scene '" + scene.getSceneName()
                            + "' excluded: flag '" + flagId + "' is active");
                    return false;
                }
            }
        }

        if (decl.required != null) {
            for (String flagId : decl.required) {
                if (!worldState.isFlagActive(flagId)) {
                    TimberLog.d(TAG, "Scene '" + scene.getSceneName()
                            + "' excluded: required flag '" + flagId + "' is inactive");
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Sums the weights of all active scored flags for the given scene.
     * Returns 0 for scenes with no flag declarations or no scored flags.
     */
    private int computeScore(Scene scene) {
        SceneFlagDeclarations decl = scene.getFlagDeclarations();
        if (decl == null || decl.scored == null) return 0;

        int score = 0;
        for (ScoredFlagEntry entry : decl.scored) {
            if (worldState.isFlagActive(entry.flagId)) {
                score += entry.weight;
            }
        }
        return score;
    }

    private void logSelection(List<Scene> eligible, int[] scores, int maxScore, List<Scene> topScorers) {
        if (topScorers.size() == 1) {
            TimberLog.d(TAG, "Selected (unique top score " + maxScore + "): "
                    + topScorers.get(0).getSceneName());
        } else {
            StringBuilder sb = new StringBuilder("Tie at score " + maxScore + " between: ");
            for (Scene s : topScorers) sb.append(s.getSceneName()).append(' ');
            TimberLog.d(TAG, sb.toString());
        }
        for (int i = 0; i < eligible.size(); i++) {
            TimberLog.d(TAG, "  score=" + scores[i] + " scene=" + eligible.get(i).getSceneName());
        }
    }
}
