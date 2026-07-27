package com.example.livewallpaper.world;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.livewallpaper.scene.models.RuleActionData;
import com.example.livewallpaper.scene.models.RuleConditionData;
import com.example.livewallpaper.scene.models.RuleConditionGroupData;
import com.example.livewallpaper.scene.models.RuleData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link RulesEvaluator}.
 *
 * Mirrors the web editor's runRules cases (frontend/src/ruleEngine.test.ts) so on-device rule
 * firing matches the simulator: matching rules fire their actions in order, a later rule can
 * react to a flag an earlier rule just set (combo), and a fired one-shot rule never fires again.
 *
 * WorldStateManager is backed by a stateful mock (flag + fired sets) — the direct analogue of the
 * web engine's activeFlags / firedOneShotRuleIds — so activations made by one rule are visible to
 * later rules within the same evaluation pass.
 */
@RunWith(RobolectricTestRunner.class)
public class RulesEvaluatorTest {

    private WorldStateManager worldState;
    private final Set<String> activeFlags = new HashSet<>();
    private final Set<String> firedRules = new HashSet<>();

    @Before
    public void setUp() {
        worldState = mock(WorldStateManager.class);

        when(worldState.isFlagActive(anyString())).thenAnswer(inv -> activeFlags.contains(inv.<String>getArgument(0)));
        doAnswer(inv -> { activeFlags.add(inv.getArgument(0)); return null; }).when(worldState).activateFlag(anyString());
        doAnswer(inv -> { activeFlags.remove(inv.getArgument(0)); return null; }).when(worldState).deactivateFlag(anyString());

        when(worldState.isRuleFired(anyString())).thenAnswer(inv -> firedRules.contains(inv.<String>getArgument(0)));
        doAnswer(inv -> { firedRules.add(inv.getArgument(0)); return null; }).when(worldState).markRuleFired(anyString());
    }

    @Test
    public void appliesActionsFromMatchingRules_andSkipsFiredOneShotRules() {
        RuleData setMorning = rule("r1", false, null, action("activate_flag", "morning"));
        RuleData milestone = rule("r2", true,
            groups(group("AND", flag("flag_active", "morning"))),
            action("activate_flag", "milestone"));

        new RulesEvaluator(worldState).evaluate(List.of(setMorning, milestone));

        assertTrue("morning activated", activeFlags.contains("morning"));
        assertTrue("milestone activated once morning is set", activeFlags.contains("milestone"));
        assertTrue("one-shot r2 recorded as fired", firedRules.contains("r2"));

        // Second pass: clear morning; the fired one-shot must NOT re-activate milestone.
        activeFlags.remove("milestone");
        RuleData clearMorning = rule("r1", false, null, action("deactivate_flag", "morning"));
        new RulesEvaluator(worldState).evaluate(List.of(clearMorning, milestone));

        assertFalse("morning deactivated", activeFlags.contains("morning"));
        assertFalse("fired one-shot does not fire again", activeFlags.contains("milestone"));
    }

    @Test
    public void laterRuleReactsToFlagAnEarlierRuleJustActivated() {
        RuleData setter = rule("setter", false, null, action("activate_flag", "a"));
        RuleData combo = rule("combo", false,
            groups(group("AND", flag("flag_active", "a"))),
            action("activate_flag", "b"));

        new RulesEvaluator(worldState).evaluate(List.of(setter, combo));

        assertTrue("combo reacted to the flag set earlier in the same pass", activeFlags.contains("b"));
    }

    @Test
    public void ruleWithNoConditions_alwaysFires() {
        RuleData always = rule("always", false, null, action("activate_flag", "x"));
        new RulesEvaluator(worldState).evaluate(new ArrayList<>(List.of(always)));
        assertTrue(activeFlags.contains("x"));
    }

    @Test
    public void ruleWithEmptyConditionsArray_alwaysFires() {
        RuleData always = rule("always", false, new RuleConditionGroupData[0], action("activate_flag", "x"));
        new RulesEvaluator(worldState).evaluate(List.of(always));
        assertTrue(activeFlags.contains("x"));
    }

    // ── condition groups are OR'd (matches the editor's evaluateConditions) ───

    @Test
    public void firesWhenAnyConditionGroupMatches() {
        activeFlags.add("b");

        // Group 1 fails (flag "a" is inactive), group 2 passes — the rule still fires.
        RuleData either = rule("either", false,
            groups(group("AND", flag("flag_active", "a")), group("AND", flag("flag_active", "b"))),
            action("activate_flag", "fired"));

        new RulesEvaluator(worldState).evaluate(List.of(either));

        assertTrue("a rule fires when any one of its groups matches", activeFlags.contains("fired"));
    }

    @Test
    public void doesNotFireWhenEveryConditionGroupFails() {
        RuleData either = rule("either", false,
            groups(group("AND", flag("flag_active", "a")), group("AND", flag("flag_active", "b"))),
            action("activate_flag", "fired"));

        new RulesEvaluator(worldState).evaluate(List.of(either));

        assertFalse("no group matched, so no action ran", activeFlags.contains("fired"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static RuleData rule(String id, boolean oneShot, RuleConditionGroupData[] conditions, RuleActionData... actions) {
        RuleData r = new RuleData();
        r.id = id;
        r.oneShot = oneShot;
        r.conditions = conditions;
        r.actions = actions;
        return r;
    }

    private static RuleConditionGroupData[] groups(RuleConditionGroupData... groups) {
        return groups;
    }

    private static RuleActionData action(String type, String flagId) {
        RuleActionData a = new RuleActionData();
        a.type = type;
        a.flagId = flagId;
        return a;
    }

    private static RuleConditionGroupData group(String operator, RuleConditionData... checks) {
        RuleConditionGroupData g = new RuleConditionGroupData();
        g.operator = operator;
        g.checks = checks;
        return g;
    }

    private static RuleConditionData flag(String type, String flagId) {
        RuleConditionData c = new RuleConditionData();
        c.type = type;
        c.flagId = flagId;
        return c;
    }
}
