package com.visionfit.identity.repository;

import com.visionfit.identity.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Oldest first, so consumers see a user's events in the order they happened. */
    List<OutboxEvent> findTop50BySentAtIsNullOrderByCreatedAtAsc();
}
