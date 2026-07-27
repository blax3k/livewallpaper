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
 * Selects which scene to show using the flag-based scoring system.
 *
 * <h3>Selection algorithm</h3>
 * <ol>
 *   <li>Exclude any scene whose {@code excluded} flags list contains an active flag.</li>
 *   <li>Exclude any scene whose {@code required} flags list has a flag that is NOT active.</li>
 *   <li>Score each remaining scene: sum the {@code weight} for every active flag in
 *       its {@code scored} list (weights may be negative).</li>
 *   <li>Pick the highest-scoring scene. On a tie, prefer the scene shown fewest times
 *       (tracked by {@link WorldStateManager}). On a further tie, pick randomly.</li>
 * </ol>
 * This mirrors the web editor's simulator ranking (frontend/src/simulatorScenes.ts) so the
 * device and the simulator always agree on which scene wins.
 *
 * <h3>Advancing vs. starting up</h3>
 * {@link #getNextScene(Scene)} additionally skips the scene already on screen, and returns
 * that same scene when nothing else qualifies — i.e. "stay put". Flag constraints are never
 * relaxed here: a pack with one scene per time-of-day has exactly one eligible scene, so a
 * double tap correctly does nothing rather than cycling through ineligible scenes.
 * {@link #getInitialScene()} must produce something to render, so it — and only it — falls
 * back to ignoring flag constraints when no scene qualifies.
 *
 * <h3>Backward compatibility</h3>
 * Scenes whose JSON has no {@code flags} field have a null {@link SceneFlagDeclarations}.
 * These scenes are always eligible (no required/excluded constraints) and score 0.
 * They will show up whenever no flag-aware scenes outscore them — which is the same
 * behaviour as the old random picker for packs that haven't been updated yet.
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
     * Returns the scene to display when the wallpaper starts up.
     *
     * Something must be rendered, so if no scene passes the required/excluded filter the
     * constraints are relaxed and every scene is considered. This keeps an impossible world
     * state (e.g. conflicting required flags) from leaving the wallpaper blank.
     */
    public Scene getInitialScene() {
        requireScenes();

        List<Scene> eligible = buildEligibleList();
        if (eligible.isEmpty()) {
            TimberLog.w(TAG, "No scenes passed flag eligibility filter at startup — relaxing constraints");
            eligible = new ArrayList<>(scenes);
        }

        return pickBest(eligible);
    }

    /**
     * Returns the next scene to display, or {@code currentScene} itself when no other scene is
     * eligible right now — the caller should then stay on the current scene instead of switching.
     *
     * @param currentScene the scene currently on screen (never selected again)
     */
    public Scene getNextScene(Scene currentScene) {
        requireScenes();

        // Eligibility is evaluated over every scene, then the one already on screen is removed.
        // Doing it in this order keeps a pack whose only eligible scene is the current one from
        // looking like "nothing qualifies" — it means "nothing new qualifies", so we stay put.
        List<Scene> eligible = buildEligibleList();
        eligible.removeIf(scene -> scene.getSceneId().equals(currentScene.getSceneId()));

        if (eligible.isEmpty()) {
            TimberLog.d(TAG, "No eligible scene other than '" + currentScene.getSceneId()
                    + "' — staying on the current scene");
            return currentScene;
        }

        return pickBest(eligible);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Scores the given scenes and returns the winner. The list must be non-empty. */
    private Scene pickBest(List<Scene> eligible) {
        // Score the eligible scenes and find the maximum score.
        int[] scores = new int[eligible.size()];
        int maxScore = Integer.MIN_VALUE;
        for (int i = 0; i < eligible.size(); i++) {
            scores[i] = computeScore(eligible.get(i));
            if (scores[i] > maxScore) maxScore = scores[i];
        }

        // Collect the scenes that share the top score.
        List<Scene> topScorers = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) {
            if (scores[i] == maxScore) topScorers.add(eligible.get(i));
        }

        logSelection(eligible, scores, maxScore, topScorers);

        // Only one winner — done.
        if (topScorers.size() == 1) {
            return topScorers.get(0);
        }

        // Tiebreaker — prefer the scene shown fewest times.
        int minCount = Integer.MAX_VALUE;
        for (Scene scene : topScorers) {
            int count = worldState.getSceneShowCount(scene.getSceneId());
            if (count < minCount) minCount = count;
        }

        List<Scene> leastShown = new ArrayList<>();
        for (Scene scene : topScorers) {
            if (worldState.getSceneShowCount(scene.getSceneId()) == minCount) {
                leastShown.add(scene);
            }
        }

        // Still tied — pick randomly.
        Scene selected = leastShown.get(random.nextInt(leastShown.size()));
        TimberLog.d(TAG, "Selected (tiebreak): " + selected.getSceneId()
                + " (showCount=" + worldState.getSceneShowCount(selected.getSceneId()) + ")");
        return selected;
    }

    private void requireScenes() {
        if (scenes.isEmpty()) {
            throw new IllegalStateException("No scenes available");
        }
    }

    /** Returns all scenes that pass the required/excluded flag checks. */
    private List<Scene> buildEligibleList() {
        List<Scene> eligible = new ArrayList<>();
        for (Scene scene : scenes) {
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
                    TimberLog.d(TAG, "Scene '" + scene.getSceneId()
                            + "' excluded: flag '" + flagId + "' is active");
                    return false;
                }
            }
        }

        if (decl.required != null) {
            for (String flagId : decl.required) {
                if (!worldState.isFlagActive(flagId)) {
                    TimberLog.d(TAG, "Scene '" + scene.getSceneId()
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
                    + topScorers.get(0).getSceneId());
        } else {
            StringBuilder sb = new StringBuilder("Tie at score " + maxScore + " between: ");
            for (Scene s : topScorers) sb.append(s.getSceneId()).append(' ');
            TimberLog.d(TAG, sb.toString());
        }
        for (int i = 0; i < eligible.size(); i++) {
            TimberLog.d(TAG, "  score=" + scores[i] + " scene=" + eligible.get(i).getSceneId());
        }
    }
}
