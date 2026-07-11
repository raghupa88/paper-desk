package com.paperdesk.gamification;

import com.paperdesk.domain.User;
import com.paperdesk.repo.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Daily usage streak, tracked by real wall-clock date (not sim time) — a
 * habit-forming mechanic distinct from the sim-day trading-performance
 * streak (see Badge.HOT_STREAK_5). Touched once per app load; same-day
 * repeats are a no-op so refreshing the page can't inflate the count.
 */
@Service
public class StreakService {

    /** Streak lengths, in days, that get called out with a milestone toast. */
    public static final List<Integer> MILESTONES = List.of(3, 7, 14, 30, 60, 100);

    public record NextStreak(int current, boolean alreadyTouchedToday) {}
    public record TouchResult(int currentStreak, int longestStreak, Integer milestoneDays) {}

    private final UserRepo userRepo;

    public StreakService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    /** Pure date math: given today, the last active date and the running streak, what's next? */
    public static NextStreak nextStreak(LocalDate today, LocalDate lastActiveDate, int currentStreak) {
        if (today.equals(lastActiveDate)) return new NextStreak(currentStreak, true);
        if (lastActiveDate != null && lastActiveDate.equals(today.minusDays(1))) {
            return new NextStreak(currentStreak + 1, false);
        }
        return new NextStreak(1, false); // first ever touch, or the streak lapsed
    }

    @Transactional
    public TouchResult touch(long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        NextStreak next = nextStreak(today, user.lastActiveDate, user.currentStreak);

        Integer milestone = null;
        if (!next.alreadyTouchedToday()) {
            user.currentStreak = next.current();
            user.lastActiveDate = today;
            user.longestStreak = Math.max(user.longestStreak, user.currentStreak);
            userRepo.save(user);
            if (MILESTONES.contains(user.currentStreak)) milestone = user.currentStreak;
        }
        return new TouchResult(user.currentStreak, user.longestStreak, milestone);
    }
}
