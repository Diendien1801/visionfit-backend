package com.visionfit.meal.service;

import com.visionfit.meal.domain.Meal;
import com.visionfit.meal.dto.DayDto;
import com.visionfit.meal.dto.MealDto;
import com.visionfit.meal.dto.SaveMealRequest;
import com.visionfit.meal.dto.StreakDto;
import com.visionfit.meal.dto.UpdateMealRequest;
import com.visionfit.meal.repository.MealRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MealService {

    /** @param created `false` when the job had already been saved and `meal` is that first meal. */
    public record Saved(MealDto meal, boolean created) {
    }

    static final int MAX_STREAK_DAYS = 366;

    private final MealRepository repository;

    public MealService(MealRepository repository) {
        this.repository = repository;
    }

    /**
     * Saving the same meal twice (double tap, retry after a timeout) returns the first one. Two keys
     * find it: the id the app made for this meal, and the analysis job, since one photo is one meal
     * even if the app made a second id for it (e.g. after a restart).
     * Not one transaction on purpose: a failed insert must roll back before the existing meal is read again.
     */
    public Saved save(UUID userId, SaveMealRequest request) {
        Optional<Saved> existing = findSaved(userId, request);
        if (existing.isPresent()) return existing.get();
        try {
            return new Saved(MealDto.from(repository.saveAndFlush(request.toMeal(userId))), true);
        } catch (DataIntegrityViolationException e) {
            // A concurrent save of the same meal won the primary key or the (user_id, job_id) constraint.
            return findSaved(userId, request).orElseThrow(() -> e);
        }
    }

    private Optional<Saved> findSaved(UUID userId, SaveMealRequest request) {
        Optional<Meal> sameId = repository.findWithItemsById(request.id());
        if (sameId.isPresent()) {
            // The id comes from the client: never hand out or overwrite someone else's meal.
            if (!sameId.get().getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This meal id is already taken");
            }
            return Optional.of(new Saved(MealDto.from(sameId.get()), false));
        }
        return findByJob(userId, request.jobId());
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

    /**
     * Every day from `from` to `to`, oldest first, each with its totals and its meals newest first.
     * Days without meals come back with zeros, so the app needs no gap filling and no arithmetic.
     */
    public List<DayDto> days(UUID userId, LocalDate from, LocalDate to) {
        Map<LocalDate, List<Meal>> byDate = repository.findLoggedBetween(userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .collect(Collectors.groupingBy(meal -> meal.getLoggedAt().toLocalDate()));
        return from.datesUntil(to.plusDays(1))
                .map(date -> DayDto.of(date, byDate.getOrDefault(date, List.of())))
                .toList();
    }

    /**
     * Consecutive days with at least one meal, counted back from the day before `today`: today is
     * still in progress, so it neither breaks nor extends the streak. Looks back at most a year.
     */
    public StreakDto streak(UUID userId, LocalDate today) {
        Set<LocalDate> logged = repository.findLoggedTimesBetween(userId, today.minusDays(MAX_STREAK_DAYS).atStartOfDay(), today.atStartOfDay())
                .stream()
                .map(LocalDateTime::toLocalDate)
                .collect(Collectors.toSet());
        int days = 0;
        for (LocalDate date = today.minusDays(1); logged.contains(date); date = date.minusDays(1)) {
            days++;
        }
        return new StreakDto(days);
    }

    private Optional<Saved> findByJob(UUID userId, String jobId) {
        // A null job id would match every manual meal (`job_id IS NULL`).
        if (jobId == null) return Optional.empty();
        return repository.findByUserIdAndJobId(userId, jobId).map(meal -> new Saved(MealDto.from(meal), false));
    }
}
