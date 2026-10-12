package com.visionfit.profile.repository;

import com.visionfit.profile.domain.BodyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BodyProfileRepository extends JpaRepository<BodyProfile, UUID> {
}
