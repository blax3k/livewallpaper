package com.example.livewallpaper.world;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.livewallpaper.scene.models.RuleConditionData;
import com.example.livewallpaper.scene.models.RuleConditionGroupData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;

/**
 * Unit tests for {@link ConditionEvaluator}.
 *
 * These deliberately mirror the web editor's ruleEngine.test.ts (frontend/src/ruleEngine.test.ts)
 * so both the on-device evaluator and the simulator's engine are pinned to the SAME condition
 * semantics: minute-precise time_of_day windows with an EXCLUSIVE end that wraps overnight,
 * scene_count keyed by scene id, day-of-week membership, install-duration and
 * time-since-flag-change comparisons, and AND/OR group short-circuiting.
 *
 * WorldStateManager is a Context-backed singleton, so it is mocked (the subclass mock-maker
 * configured under src/test/resources handles the concrete class). "Now" is injected as a fixed
 * Calendar so time-based checks are deterministic — the analogue of the web engine taking
 * currentMinuteOfDay / dayOfWeekNum as explicit clock inputs.
 */
@RunWith(RobolectricTestRunner.class)
public class ConditionEvaluatorTest {

    private WorldStateManager worldState;

    @Before
    public void setUp() {
        worldState = mock(WorldStateManager.class);
    }

    // ── flag_active / flag_inactive ──────────────────────────────────────────

    @Test
    public void flagActiveAndInactive_checkTheActiveSet() {
        when(worldState.isFlagActive("a")).thenReturn(true); // "b" defaults to inactive
        ConditionEvaluator ev = evaluatorAt(tuesdayAt(12, 0));

        assertTrue(check(ev, flag("flag_active", "a")));
        assertFalse(check(ev, flag("flag_active", "b")));
        assertTrue(check(ev, flag("flag_inactive", "b")));
        assertFalse(check(ev, flag("flag_inactive", "a")));
    }

    // ── time_of_day: exclusive end + overnight wrap ──────────────────────────

    @Test
    public void timeOfDay_handlesOvernightWrapAndExclusiveEnd() {
        assertTrue(check(evaluatorAt(tuesdayAt(12, 0)), timeOfDay(6, 0, 18, 0)));
        assertFalse(check(evaluatorAt(tuesdayAt(12, 0)), timeOfDay(13, 0, 18, 0)));
        // Overnight window 22-4 at noon should not match.
        assertFalse(check(evaluatorAt(tuesdayAt(12, 0)), timeOfDay(22, 0, 4, 0)));
        // End is exclusive: at exactly 18:00 a 6->18 window is over.
        assertFalse(check(evaluatorAt(tuesdayAt(18, 0)), timeOfDay(6, 0, 18, 0)));
        assertTrue(check(evaluatorAt(tuesdayAt(22, 59)), timeOfDay(6, 0, 23, 0)));
        // Overnight window 22-4 at 01:00 should match.
        assertTrue(check(evaluatorAt(tuesdayAt(1, 0)), timeOfDay(22, 0, 4, 0)));
    }

    @Test
    public void timeOfDay_respectsMinutes() {
        // Window 09:30 -> 17:45.
        assertFalse(check(evaluatorAt(tuesdayAt(9, 15)), timeOfDay(9, 30, 17, 45))); // before start
        assertTrue(check(evaluatorAt(tuesdayAt(9, 30)), timeOfDay(9, 30, 17, 45)));  // start inclusive
        assertTrue(check(evaluatorAt(tuesdayAt(17, 44)), timeOfDay(9, 30, 17, 45))); // inside
        assertFalse(check(evaluatorAt(tuesdayAt(17, 45)), timeOfDay(9, 30, 17, 45))); // end exclusive
    }

    // ── day_of_week ──────────────────────────────────────────────────────────

    @Test
    public void dayOfWeek_checksMembership() {
        // Tuesday -> dayOfWeekNum 2 (0=Sun .. 6=Sat).
        ConditionEvaluator tuesday = evaluatorAt(tuesdayAt(12, 0));
        assertTrue(check(tuesday, dayOfWeek(2, 3)));
        assertFalse(check(tuesday, dayOfWeek(0, 6)));
    }

    // ── install_duration_hours ───────────────────────────────────────────────

    @Test
    public void installDurationHours_comparesWithOperator() {
        when(worldState.getInstallDurationHours()).thenReturn(252L);
        ConditionEvaluator ev = evaluatorAt(tuesdayAt(12, 0));

        assertTrue(check(ev, intCompare("install_duration_hours", ">=", 100)));
        assertFalse(check(ev, intCompare("install_duration_hours", "<", 100)));
    }

    // ── scene_count (keyed by scene id, defaults to 0) ───────────────────────

    @Test
    public void sceneCount_comparesTrackedCountsDefaultingToZero() {
        when(worldState.getSceneShowCount("forest")).thenReturn(3); // "unseen" defaults to 0
        ConditionEvaluator ev = evaluatorAt(tuesdayAt(12, 0));

        assertTrue(check(ev, sceneCount("forest", ">=", 3)));
        assertTrue(check(ev, sceneCount("unseen", "==", 0)));
        assertFalse(check(ev, sceneCount("forest", "<", 3)));
    }

    // ── time_since_flag_change (false when the flag never changed) ───────────

    @Test
    public void timeSinceFlagChange_isRelativeAndFalseIfNeverChanged() {
        when(worldState.getTimeSinceFlagActivatedHours("cat_appeared")).thenReturn(5L);
        when(worldState.getTimeSinceFlagDeactivatedHours("cat_appeared")).thenReturn(-1L); // never
        ConditionEvaluator ev = evaluatorAt(tuesdayAt(12, 0));

        assertTrue(check(ev, sinceFlag("cat_appeared", "activated", ">=", 5)));
        assertFalse(check(ev, sinceFlag("cat_appeared", "deactivated", ">=", 0)));
    }

    // ── group AND / OR + empty groups ────────────────────────────────────────

    @Test
    public void emptyOrNullGroup_alwaysMatches() {
        ConditionEvaluator ev = evaluatorAt(tuesdayAt(12, 0));
        assertTrue(ev.evaluate(null));
        assertTrue(ev.evaluate(group("AND"))); // no checks
    }

    @Test
    public void andGroup_requiresEveryCheck_orGroup_requiresAny() {
        when(worldState.isFlagActive("morning")).thenReturn(true); // "missing" stays inactive
        ConditionEvaluator ev = evaluatorAt(tuesdayAt(12, 0));

        assertTrue(ev.evaluate(group("AND", flag("flag_active", "morning"))));
        assertFalse(ev.evaluate(group("AND", flag("flag_active", "morning"), flag("flag_active", "missing"))));

        assertTrue(ev.evaluate(group("OR", flag("flag_active", "missing"), flag("flag_active", "morning"))));
        assertFalse(ev.evaluate(group("OR", flag("flag_active", "missing"), flag("flag_active", "other"))));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ConditionEvaluator evaluatorAt(Calendar now) {
        return new ConditionEvaluator(worldState, () -> now);
    }

    /** Evaluates a single condition by wrapping it in a one-check AND group. */
    private boolean check(ConditionEvaluator ev, RuleConditionData condition) {
        return ev.evaluate(group("AND", condition));
    }

    private static Calendar tuesdayAt(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
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

    private static RuleConditionData timeOfDay(int startHour, int startMinute, int endHour, int endMinute) {
        RuleConditionData c = new RuleConditionData();
        c.type = "time_of_day";
        c.startHour = startHour;
        c.startMinute = startMinute;
        c.endHour = endHour;
        c.endMinute = endMinute;
        return c;
    }

    private static RuleConditionData dayOfWeek(int... days) {
        RuleConditionData c = new RuleConditionData();
        c.type = "day_of_week";
        c.daysOfWeek = days;
        return c;
    }

    private static RuleConditionData intCompare(String type, String operator, int value) {
        RuleConditionData c = new RuleConditionData();
        c.type = type;
        c.operator = operator;
        c.intValue = value;
        return c;
    }

    private static RuleConditionData sceneCount(String sceneId, String operator, int value) {
        RuleConditionData c = intCompare("scene_count", operator, value);
        c.sceneId = sceneId;
        return c;
    }

    private static RuleConditionData sinceFlag(String flagId, String changeType, String operator, int value) {
        RuleConditionData c = intCompare("time_since_flag_change", operator, value);
        c.flagId = flagId;
        c.flagChangeType = changeType;
        return c;
    }
}
