package com.visionfit.analysis.repository;

import com.visionfit.analysis.domain.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {

    Optional<AnalysisJob> findByIdAndUserId(UUID id, UUID userId);

    List<AnalysisJob> findByStageIn(Collection<AnalysisJob.Stage> stages);
}
