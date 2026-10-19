package com.visionfit.analysis.repository;

import com.visionfit.analysis.domain.AnalysisPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnalysisPhotoRepository extends JpaRepository<AnalysisPhoto, UUID> {
}
