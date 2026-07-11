package com.paperdesk.gamification;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreakServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 12);

    @Test
    void firstEverTouchStartsAtOne() {
        var next = StreakService.nextStreak(TODAY, null, 0);
        assertEquals(1, next.current());
        assertFalse(next.alreadyTouchedToday());
    }

    @Test
    void consecutiveDayIncrements() {
        var next = StreakService.nextStreak(TODAY, TODAY.minusDays(1), 4);
        assertEquals(5, next.current());
        assertFalse(next.alreadyTouchedToday());
    }

    @Test
    void sameDayIsIdempotent() {
        var next = StreakService.nextStreak(TODAY, TODAY, 7);
        assertEquals(7, next.current());
        assertTrue(next.alreadyTouchedToday());
    }

    @Test
    void gapOfTwoOrMoreDaysResetsToOne() {
        var next = StreakService.nextStreak(TODAY, TODAY.minusDays(2), 30);
        assertEquals(1, next.current());
        assertFalse(next.alreadyTouchedToday());
    }

    @Test
    void longGapResetsToOne() {
        var next = StreakService.nextStreak(TODAY, TODAY.minusDays(90), 15);
        assertEquals(1, next.current());
    }

    @Test
    void milestonesListIsAscendingAndStartsAtThree() {
        assertEquals(3, StreakService.MILESTONES.get(0));
        for (int i = 1; i < StreakService.MILESTONES.size(); i++) {
            assertTrue(StreakService.MILESTONES.get(i) > StreakService.MILESTONES.get(i - 1));
        }
    }
}
