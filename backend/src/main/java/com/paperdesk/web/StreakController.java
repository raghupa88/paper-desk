package com.paperdesk.web;

import com.paperdesk.config.SecurityConfig;
import com.paperdesk.gamification.StreakService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/streak")
public class StreakController {

    private final StreakService streaks;

    public StreakController(StreakService streaks) {
        this.streaks = streaks;
    }

    /** Called once per app load. Idempotent for repeat calls on the same real-world day. */
    @PostMapping("/touch")
    public StreakService.TouchResult touch() {
        return streaks.touch(SecurityConfig.currentUserId());
    }
}
