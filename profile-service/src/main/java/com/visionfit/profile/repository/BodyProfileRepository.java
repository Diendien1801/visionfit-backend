package com.visionfit.profile.repository;

import com.visionfit.profile.domain.BodyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface BodyProfileRepository extends JpaRepository<BodyProfile, UUID> {

    @Transactional
    @Modifying
    @Query("delete from BodyProfile p where p.userId = :userId")
    int deleteByUserId(UUID userId);
}
