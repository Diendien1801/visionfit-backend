package com.visionfit.meal.dto;

import com.visionfit.meal.domain.Meal;
import com.visionfit.meal.domain.MealItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** Body of `POST /api/v1/meals`. Limits match the CHECKs in V1__create_meals_tables.sql. */
public record SaveMealRequest(
        /** `null` for a meal that did not come from an analysis job; never empty, or every such meal would share it. */
        @Size(min = 1, max = 64) String jobId,
        @NotNull Meal.Type type,
        @NotNull LocalDateTime loggedAt,
        @Size(max = 2048) String photoUrl,
        boolean isEdited,
        @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid Item> items
) {
    public record Item(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Min(1) @Max(5000) Integer grams,
            @NotNull @Valid Per100g per100g,
            @Min(0) @Max(100) Integer confidence
    ) {
    }

    public Meal toMeal(UUID userId) {
        List<MealItem> mealItems = IntStream.range(0, items.size())
                .mapToObj(i -> {
                    Item item = items.get(i);
                    Per100g n = item.per100g();
                    return new MealItem(i, item.name().trim(), item.grams(), n.kcal(), n.proteinG(), n.carbsG(), n.fatG(), item.confidence());
                })
                .toList();
        return new Meal(userId, jobId, type, loggedAt, photoUrl, isEdited, mealItems);
    }
}
