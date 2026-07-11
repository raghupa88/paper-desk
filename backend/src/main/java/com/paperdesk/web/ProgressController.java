package com.paperdesk.web;

import com.paperdesk.domain.Account;
import com.paperdesk.domain.Achievement;
import com.paperdesk.gamification.Badge;
import com.paperdesk.gamification.Levels;
import com.paperdesk.repo.AchievementRepo;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final AchievementRepo achievementRepo;
    private final AccountGuard guard;

    public ProgressController(AchievementRepo achievementRepo, AccountGuard guard) {
        this.achievementRepo = achievementRepo;
        this.guard = guard;
    }

    /** XP, level and the full badge catalog with earned status — drives the Progress tab. */
    @GetMapping("/{accountId}")
    public Map<String, Object> progress(@PathVariable long accountId) {
        Account account = guard.owned(accountId);
        Map<String, Achievement> earned = achievementRepo.findByAccountId(accountId).stream()
                .collect(Collectors.toMap(a -> a.code, a -> a));
        Levels.Level level = Levels.forXp(account.xp);

        List<Map<String, Object>> badges = new ArrayList<>();
        for (Badge b : Badge.values()) {
            Achievement a = earned.get(b.name());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", b.name());
            m.put("title", b.title);
            m.put("description", b.description);
            m.put("xp", b.xp);
            m.put("earned", a != null);
            m.put("earnedSimDate", a == null || a.simDate == null ? null : a.simDate.toString());
            badges.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accountId", accountId);
        out.put("xp", account.xp);
        out.put("level", level.number());
        out.put("levelName", level.name());
        out.put("levelFloorXp", level.floorXp());
        out.put("nextLevelXp", level.nextLevelXp());
        out.put("earnedCount", earned.size());
        out.put("badges", badges);
        return out;
    }
}
