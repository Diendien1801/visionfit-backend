package com.visionfit.meal.service;

import com.visionfit.meal.domain.Meal;
import com.visionfit.meal.dto.MealDto;
import com.visionfit.meal.dto.SaveMealRequest;
import com.visionfit.meal.dto.UpdateMealRequest;
import com.visionfit.meal.repository.MealRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MealService {

    /** @param created `false` when the job had already been saved and `meal` is that first meal. */
    public record Saved(MealDto meal, boolean created) {
    }

    private final MealRepository repository;

    public MealService(MealRepository repository) {
        this.repository = repository;
    }

    /**
     * Saving the same analysis job twice (double tap, retry after a timeout) returns the first meal.
     * Not one transaction on purpose: a failed insert must roll back before the existing meal is read again.
     */
    public Saved save(UUID userId, SaveMealRequest request) {
        Optional<Saved> existing = findByJob(userId, request.jobId());
        if (existing.isPresent()) return existing.get();
        try {
            return new Saved(MealDto.from(repository.saveAndFlush(request.toMeal(userId))), true);
        } catch (DataIntegrityViolationException e) {
            // A concurrent save of the same job won the (user_id, job_id) unique constraint.
            return findByJob(userId, request.jobId()).orElseThrow(() -> e);
        }
    }

    /**
     * Replaces the type and the dishes of the caller's own meal. Empty when the meal does not
     * exist or belongs to someone else. Flushed before mapping, so new dishes come back with ids.
     */
    @Transactional
    public Optional<MealDto> update(UUID userId, UUID mealId, UpdateMealRequest request) {
        return repository.findByIdAndUserId(mealId, userId).map(meal -> {
            meal.update(request.type(), request.toMealItems());
            return MealDto.from(repository.saveAndFlush(meal));
        });
    }

    /** `false` when there was no such meal of this user, e.g. it was already deleted. */
    @Transactional
    public boolean delete(UUID userId, UUID mealId) {
        Optional<Meal> meal = repository.findByIdAndUserId(mealId, userId);
        meal.ifPresent(repository::delete);
        return meal.isPresent();
    }

    /** Meals logged from the start of `from` to the end of `to`, newest first. */
    public List<MealDto> list(UUID userId, LocalDate from, LocalDate to) {
        return repository.findLoggedBetween(userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .map(MealDto::from)
                .toList();
    }

    private Optional<Saved> findByJob(UUID userId, String jobId) {
        // A null job id would match every manual meal (`job_id IS NULL`).
        if (jobId == null) return Optional.empty();
        return repository.findByUserIdAndJobId(userId, jobId).map(meal -> new Saved(MealDto.from(meal), false));
    }
}
