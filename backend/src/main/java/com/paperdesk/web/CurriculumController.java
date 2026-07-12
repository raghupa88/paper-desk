package com.paperdesk.web;

import com.paperdesk.config.SecurityConfig;
import com.paperdesk.domain.Cohort;
import com.paperdesk.domain.CohortMember;
import com.paperdesk.domain.Curriculum;
import com.paperdesk.domain.CurriculumStep;
import com.paperdesk.gamification.Mission;
import com.paperdesk.repo.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guided curricula: an instructor sequences existing missions (Mission enum)
 * into an ordered, assignable syllabus for a cohort -- students see a locked
 * path rather than a loose scenario picker, unlocking one step at a time as
 * each mission is completed in any of their accounts.
 *
 * Progress (complete/unlocked) is always computed against the *calling
 * user's own* accounts -- there's no separate per-student breakdown for the
 * instructor here (unlike the grading/challenge endpoints). An instructor
 * viewing a curriculum sees their own progress against it, same as a
 * student; the instructor-facing action this endpoint supports is
 * authoring/publishing the path, not monitoring individual students.
 */
@RestController
@RequestMapping("/api/cohorts/{cohortId}/curricula")
public class CurriculumController {

    public record CreateCurriculumRequest(String name, String description, List<String> missionCodes) {}

    private final CurriculumRepo curriculumRepo;
    private final CurriculumStepRepo stepRepo;
    private final CohortRepo cohortRepo;
    private final CohortMemberRepo memberRepo;
    private final AccountRepo accountRepo;
    private final MissionCompletionRepo completionRepo;

    public CurriculumController(CurriculumRepo curriculumRepo, CurriculumStepRepo stepRepo, CohortRepo cohortRepo,
                                CohortMemberRepo memberRepo, AccountRepo accountRepo,
                                MissionCompletionRepo completionRepo) {
        this.curriculumRepo = curriculumRepo;
        this.stepRepo = stepRepo;
        this.cohortRepo = cohortRepo;
        this.memberRepo = memberRepo;
        this.accountRepo = accountRepo;
        this.completionRepo = completionRepo;
    }

    @PostMapping
    public Map<String, Object> create(@PathVariable long cohortId, @RequestBody CreateCurriculumRequest req) {
        requireInstructor(cohortId);
        if (req.missionCodes() == null || req.missionCodes().isEmpty()) {
            throw new IllegalArgumentException("A curriculum needs at least one mission step");
        }
        // Validates every code against the real catalog before anything is persisted.
        for (String code : req.missionCodes()) Mission.valueOf(code);

        Curriculum curriculum = new Curriculum();
        curriculum.cohortId = cohortId;
        curriculum.name = req.name();
        curriculum.description = req.description();
        curriculumRepo.save(curriculum);

        int order = 0;
        for (String code : req.missionCodes()) {
            CurriculumStep step = new CurriculumStep();
            step.curriculumId = curriculum.id;
            step.missionCode = code;
            step.stepOrder = order++;
            stepRepo.save(step);
        }
        return curriculumJson(curriculum);
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable long cohortId) {
        requireMember(cohortId);
        return curriculumRepo.findByCohortIdOrderByIdDesc(cohortId).stream()
                .map(this::curriculumJson).toList();
    }

    private Map<String, Object> curriculumJson(Curriculum curriculum) {
        long userId = SecurityConfig.currentUserId();
        List<Long> myAccountIds = accountRepo.findByUserId(userId).stream().map(a -> a.id).toList();

        List<Map<String, Object>> steps = new ArrayList<>();
        boolean previousComplete = true;
        for (CurriculumStep step : stepRepo.findByCurriculumIdOrderByStepOrder(curriculum.id)) {
            Mission mission = Mission.valueOf(step.missionCode);
            boolean complete = myAccountIds.stream()
                    .anyMatch(accountId -> completionRepo.existsByAccountIdAndCode(accountId, step.missionCode));

            Map<String, Object> s = new LinkedHashMap<>();
            s.put("stepOrder", step.stepOrder);
            s.put("missionCode", step.missionCode);
            s.put("title", mission.title);
            s.put("description", mission.description);
            s.put("xp", mission.xp);
            s.put("complete", complete);
            s.put("unlocked", previousComplete);
            steps.add(s);

            previousComplete = complete;
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("curriculumId", curriculum.id);
        m.put("cohortId", curriculum.cohortId);
        m.put("name", curriculum.name);
        m.put("description", curriculum.description);
        m.put("steps", steps);
        return m;
    }

    private Cohort requireInstructor(long cohortId) {
        Cohort cohort = cohortRepo.findById(cohortId).orElseThrow();
        if (!cohort.instructorId.equals(SecurityConfig.currentUserId())) {
            throw new SecurityException("Instructor of this cohort required");
        }
        return cohort;
    }

    private Cohort requireMember(long cohortId) {
        long userId = SecurityConfig.currentUserId();
        Cohort cohort = cohortRepo.findById(cohortId).orElseThrow();
        boolean allowed = cohort.instructorId.equals(userId)
                || memberRepo.findByCohortIdAndUserId(cohortId, userId).isPresent();
        if (!allowed) throw new SecurityException("Not a member of this cohort");
        return cohort;
    }
}
