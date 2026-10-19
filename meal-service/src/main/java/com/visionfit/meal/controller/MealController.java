package com.visionfit.meal.controller;

import com.visionfit.meal.dto.DayDto;
import com.visionfit.meal.dto.MealDto;
import com.visionfit.meal.dto.SaveMealRequest;
import com.visionfit.meal.dto.StreakDto;
import com.visionfit.meal.dto.UpdateMealRequest;
import com.visionfit.meal.service.MealService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** The caller's own meal diary. The user id always comes from the token, never from the request. */
@RestController
@RequestMapping("/api/v1/meals")
public class MealController {

    /** Covers the dashboard's 60-day streak plus today, and keeps one response small. */
    static final long MAX_RANGE_DAYS = 92;

    private final MealService mealService;

    public MealController(MealService mealService) {
        this.mealService = mealService;
    }

    /** 201 for a new meal, 200 with the first meal when this job was already saved. */
    @PostMapping
    public ResponseEntity<MealDto> save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveMealRequest body) {
        MealService.Saved saved = mealService.save(userId(jwt), body);
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(saved.meal());
    }

    /** 404 for a meal that does not exist or is someone else's: the two are not told apart. */
    @PutMapping("/{id}")
    public ResponseEntity<MealDto> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMealRequest body
    ) {
        return ResponseEntity.of(mealService.update(userId(jwt), id, body));
    }

    /** 204 once deleted, 404 when there is no such meal (already deleted, or someone else's). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return mealService.delete(userId(jwt), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Every day of the range with its totals and meals. Both dates inclusive; `from = to` for a single day. */
    @GetMapping
    public List<DayDto> days(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from..to must span 1 to " + MAX_RANGE_DAYS + " days");
        }
        return mealService.days(userId(jwt), from, to);
    }

    /**
     * `today` comes from the app: meal times are the user's wall-clock time, so only the app knows
     * which day "today" is for this user.
     */
    @GetMapping("/streak")
    public StreakDto streak(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today
    ) {
        return mealService.streak(userId(jwt), today);
    }

    /** Safe to parse: SecurityConfig rejects tokens whose `uid` is not a UUID. */
    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("uid"));
    }
}
