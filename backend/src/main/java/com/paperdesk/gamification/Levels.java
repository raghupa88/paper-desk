package com.paperdesk.gamification;

/** XP → level mapping shared by the progress API and the leaderboard. */
public final class Levels {
    private Levels() {}

    private static final double[] THRESHOLDS = {0, 100, 250, 500, 900, 1500, 2500, 4000};
    private static final String[] NAMES = {
        "Observer", "Apprentice", "Associate", "Trader",
        "Senior Trader", "Desk Head", "Rainmaker", "Market Wizard",
    };

    public record Level(int number, String name, double floorXp, Double nextLevelXp) {}

    public static Level forXp(double xp) {
        int idx = 0;
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (xp >= THRESHOLDS[i]) { idx = i; break; }
        }
        Double next = idx + 1 < THRESHOLDS.length ? THRESHOLDS[idx + 1] : null;
        return new Level(idx + 1, NAMES[idx], THRESHOLDS[idx], next);
    }
}
