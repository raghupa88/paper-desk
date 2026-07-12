package com.paperdesk.repo;

import com.paperdesk.domain.CurriculumStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CurriculumStepRepo extends JpaRepository<CurriculumStep, Long> {
    List<CurriculumStep> findByCurriculumIdOrderByStepOrder(Long curriculumId);
}
