package com.visionfit.meal.repository;

import com.visionfit.meal.domain.Meal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every finder loads the items in the same query, so no N+1 and no lazy loading after the transaction. */
public interface MealRepository extends JpaRepository<Meal, UUID> {

    /** Empty for another user's meal as well, so nobody learns which meal ids exist. */
    @EntityGraph(attributePaths = "items")
    Optional<Meal> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "items")
    Optional<Meal> findByUserIdAndJobId(UUID userId, String jobId);

    /** Meals logged in [start, end), newest first. */
    @EntityGraph(attributePaths = "items")
    @Query("select m from Meal m where m.userId = :userId and m.loggedAt >= :start and m.loggedAt < :end order by m.loggedAt desc")
    List<Meal> findLoggedBetween(UUID userId, LocalDateTime start, LocalDateTime end);
}
