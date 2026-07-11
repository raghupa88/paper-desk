package com.paperdesk.web;

import com.paperdesk.gamification.MissionEvaluationService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionEvaluationService missions;
    private final AccountGuard guard;

    public MissionController(MissionEvaluationService missions, AccountGuard guard) {
        this.missions = missions;
        this.guard = guard;
    }

    /** Every mission with live step-completion status — drives the Progress tab's mission cards. */
    @GetMapping("/{accountId}")
    public List<Map<String, Object>> missions(@PathVariable long accountId) {
        guard.owned(accountId);
        return missions.evaluate(accountId).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r.mission().name());
            m.put("title", r.mission().title);
            m.put("description", r.mission().description);
            m.put("xp", r.mission().xp);
            m.put("completed", r.completed());
            m.put("steps", r.steps().stream().map(s -> Map.of("description", s.description(), "done", s.done())).toList());
            return m;
        }).toList();
    }
}
