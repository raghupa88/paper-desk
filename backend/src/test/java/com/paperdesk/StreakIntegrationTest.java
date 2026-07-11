package com.paperdesk;

import com.paperdesk.domain.Enums.Role;
import com.paperdesk.domain.User;
import com.paperdesk.gamification.StreakService;
import com.paperdesk.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class StreakIntegrationTest {

    @Autowired UserRepo userRepo;
    @Autowired StreakService streaks;

    private long newUser() {
        User u = new User();
        u.email = "streaker" + System.nanoTime() + "@test.io";
        u.passwordHash = "x";
        u.displayName = "Streaker";
        u.role = Role.STUDENT;
        userRepo.save(u);
        return u.id;
    }

    @Test
    void firstTouchStartsStreakAtOneAndPersists() {
        long userId = newUser();
        var result = streaks.touch(userId);
        assertEquals(1, result.currentStreak());
        assertEquals(1, result.longestStreak());
        assertNull(result.milestoneDays());

        User reloaded = userRepo.findById(userId).orElseThrow();
        assertEquals(LocalDate.now(ZoneOffset.UTC), reloaded.lastActiveDate);
    }

    @Test
    void repeatedTouchSameDayDoesNotDoubleCount() {
        long userId = newUser();
        streaks.touch(userId);
        var second = streaks.touch(userId);
        assertEquals(1, second.currentStreak(), "same-day touch must be idempotent");
    }

    @Test
    void backdatingLastActiveSimulatesAContinuedStreakAndHitsAMilestone() {
        long userId = newUser();
        User user = userRepo.findById(userId).orElseThrow();
        user.lastActiveDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        user.currentStreak = 2;
        user.longestStreak = 2;
        userRepo.save(user);

        var result = streaks.touch(userId);
        assertEquals(3, result.currentStreak());
        assertEquals(3, result.longestStreak());
        assertEquals(3, result.milestoneDays(), "day 3 is a milestone");
    }

    @Test
    void lapsedStreakResetsAndLongestIsPreserved() {
        long userId = newUser();
        User user = userRepo.findById(userId).orElseThrow();
        user.lastActiveDate = LocalDate.now(ZoneOffset.UTC).minusDays(5);
        user.currentStreak = 10;
        user.longestStreak = 10;
        userRepo.save(user);

        var result = streaks.touch(userId);
        assertEquals(1, result.currentStreak());
        assertEquals(10, result.longestStreak(), "longest streak is a high-water mark");
        assertNull(result.milestoneDays());
    }
}
