package com.example.livewallpaper.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.example.livewallpaper.scene.models.FlagData;
import com.example.livewallpaper.scene.models.RuleConditionData;
import com.example.livewallpaper.scene.models.RuleData;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Unit tests for {@link PackLoader}.
 *
 * The payload fixtures below are the web editor's {@code GET /api/projects/:id/rules} and
 * {@code /flags} responses — the exact bytes ProjectFileManager writes into a downloaded pack.
 * Pinning them here is the point of this class: the Android models drifted from
 * {@code RuleDefinition} once already (conditions became an array of OR'd groups in migration 0015
 * while RuleData still expected a single object), Gson rejected every downloaded rules.json, and
 * the loader quietly served the bundled assets instead — so the wallpaper ran the built-in
 * time-of-day rules while looking perfectly healthy.
 *
 * Fixtures are written with single quotes and converted to JSON double quotes by {@link #json},
 * purely so the payload stays readable without escaping (the source level here predates text blocks).
 */
@RunWith(RobolectricTestRunner.class)
public class PackLoaderTest {

    /** Real {@code GET /api/projects/:id/rules} output: conditions is an ARRAY of OR'd groups. */
    private static final String BACKEND_RULES_JSON = json(
        "[",
        "  {",
        "    'id': 'set_night',",
        "    'name': 'Set Night',",
        "    'group': 'Time of day',",
        "    'conditions': [",
        "      {",
        "        'operator': 'AND',",
        "        'checks': [",
        "          { 'type': 'time_of_day', 'startHour': 21, 'startMinute': 30, 'endHour': 6, 'endMinute': 0 }",
        "        ]",
        "      }",
        "    ],",
        "    'actions': [",
        "      { 'type': 'activate_flag', 'flagId': 'night' },",
        "      { 'type': 'deactivate_flag', 'flagId': 'day' }",
        "    ],",
        "    'oneShot': false",
        "  }",
        "]");

    /** Real {@code GET /api/projects/:id/flags} output, including the chapter/group extras. */
    private static final String BACKEND_FLAGS_JSON = json(
        "[",
        "  { 'id': 'night', 'name': 'Night', 'defaultActive': false, 'group': 'Time of day' },",
        "  { 'id': 'day', 'name': 'Day', 'defaultActive': true, 'isChapter': true, 'chapterOrder': 1 }",
        "]");

    /** The pre-0015 rule shape: conditions as a single group object rather than an array. */
    private static final String LEGACY_RULES_JSON = json(
        "[",
        "  {",
        "    'id': 'legacy',",
        "    'name': 'Legacy',",
        "    'conditions': { 'operator': 'AND', 'checks': [ { 'type': 'time_of_day', 'startHour': 21, 'endHour': 6 } ] },",
        "    'actions': [ { 'type': 'activate_flag', 'flagId': 'night' } ],",
        "    'oneShot': false",
        "  }",
        "]");

    @Rule
    public TemporaryFolder packDir = new TemporaryFolder();

    private Context context() {
        return RuntimeEnvironment.getApplication();
    }

    // ── the payload the editor actually serves ───────────────────────────────

    @Test
    public void loadsRulesInTheShapeTheWebEditorServes() {
        writePack("rules.json", BACKEND_RULES_JSON);

        List<RuleData> rules = new PackLoader(context(), packDir.getRoot()).loadRules();

        assertEquals("the pack's rules must load, not the bundled asset's four", 1, rules.size());
        RuleData rule = rules.get(0);
        assertEquals("set_night", rule.id);

        assertNotNull("conditions must survive parsing — a null group would fire the rule always",
            rule.conditions);
        assertEquals(1, rule.conditions.length);
        assertEquals("AND", rule.conditions[0].operator);

        RuleConditionData check = rule.conditions[0].checks[0];
        assertEquals("time_of_day", check.type);
        assertEquals(21, check.startHour);
        assertEquals(30, check.startMinute);
        assertEquals(6, check.endHour);

        assertEquals(2, rule.actions.length);
        assertEquals("activate_flag", rule.actions[0].type);
        assertEquals("night", rule.actions[0].flagId);
    }

    @Test
    public void loadsFlagsInTheShapeTheWebEditorServes() {
        writePack("flags.json", BACKEND_FLAGS_JSON);

        List<FlagData> flags = new PackLoader(context(), packDir.getRoot()).loadFlags();

        assertEquals(2, flags.size());
        assertEquals("night", flags.get(0).id);
        assertEquals("Night", flags.get(0).name);
        assertTrue("defaultActive must round-trip", flags.get(1).defaultActive);
    }

    /** The bundled asset is a pack payload too, so it must parse under the same models. */
    @Test
    public void bundledAssetRulesParse() {
        List<RuleData> rules = new PackLoader(context()).loadRules();

        assertEquals("assets/rules.json defines the four time-of-day setters", 4, rules.size());
        for (RuleData rule : rules) {
            assertNotNull("bundled rule '" + rule.id + "' must keep its conditions", rule.conditions);
            assertEquals("time_of_day", rule.conditions[0].checks[0].type);
        }
    }

    // ── failure handling ─────────────────────────────────────────────────────

    /**
     * A pack that ships a malformed rules.json must NOT silently inherit the bundled rules —
     * running another pack's world simulation is far harder to notice than running none.
     */
    @Test
    public void malformedPackFileYieldsNothingRatherThanTheBundledAsset() {
        writePack("rules.json", "{ this is not valid json");

        List<RuleData> rules = new PackLoader(context(), packDir.getRoot()).loadRules();

        assertTrue("a broken pack file must not fall back to assets/rules.json", rules.isEmpty());
    }

    /** Rules in the old single-object shape are malformed now — they must not load silently. */
    @Test
    public void legacySingleGroupConditionsAreRejected() {
        writePack("rules.json", LEGACY_RULES_JSON);

        assertTrue(new PackLoader(context(), packDir.getRoot()).loadRules().isEmpty());
    }

    /** A pack that ships no rules of its own still gets the bundled defaults. */
    @Test
    public void absentPackFileFallsBackToTheBundledAsset() {
        List<RuleData> rules = new PackLoader(context(), packDir.getRoot()).loadRules();

        assertEquals("no rules.json in the pack dir — the bundled asset applies", 4, rules.size());
    }

    @Test
    public void noPackDirFallsBackToTheBundledAsset() {
        assertEquals(4, new PackLoader(context(), null).loadRules().size());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Joins fixture lines and swaps the readability single quotes for JSON double quotes. */
    private static String json(String... lines) {
        return String.join("\n", lines).replace('\'', '"');
    }

    private void writePack(String filename, String json) {
        try (FileWriter writer = new FileWriter(new File(packDir.getRoot(), filename), StandardCharsets.UTF_8)) {
            writer.write(json);
        } catch (IOException e) {
            throw new AssertionError("Failed to stage pack file " + filename, e);
        }
    }
}
