package com.visionfit.analysis.repository;

import com.visionfit.analysis.domain.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {

    Optional<AnalysisJob> findByIdAndUserId(UUID id, UUID userId);

    List<AnalysisJob> findByStageIn(Collection<AnalysisJob.Stage> stages);

    /** One statement; the photos and detections go with the jobs (ON DELETE CASCADE). */
    @Transactional
    @Modifying
    @Query("delete from AnalysisJob j where j.userId = :userId")
    int deleteByUserId(UUID userId);
}
