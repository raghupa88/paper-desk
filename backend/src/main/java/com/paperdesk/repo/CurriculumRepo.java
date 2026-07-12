package com.paperdesk.repo;

import com.paperdesk.domain.Curriculum;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CurriculumRepo extends JpaRepository<Curriculum, Long> {
    List<Curriculum> findByCohortIdOrderByIdDesc(Long cohortId);
}
