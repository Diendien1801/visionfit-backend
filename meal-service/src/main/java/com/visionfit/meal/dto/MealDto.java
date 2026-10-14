package com.visionfit.meal.dto;

import com.visionfit.meal.domain.Meal;
import com.visionfit.meal.domain.MealItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A saved meal as the API returns it. The app derives the title and the totals from the items. */
public record MealDto(
        UUID id,
        String jobId,
        Meal.Type type,
        LocalDateTime loggedAt,
        String photoUrl,
        boolean isEdited,
        List<Item> items
) {
    public record Item(UUID id, String name, int grams, Per100g per100g, Integer confidence) {

        static Item from(MealItem item) {
            return new Item(
                    item.getId(),
                    item.getName(),
                    item.getGrams(),
                    new Per100g(item.getKcalPer100g(), item.getProteinPer100g(), item.getCarbsPer100g(), item.getFatPer100g()),
                    item.getConfidence()
            );
        }
    }

    public static MealDto from(Meal meal) {
        return new MealDto(
                meal.getId(),
                meal.getJobId(),
                meal.getType(),
                meal.getLoggedAt(),
                meal.getPhotoUrl(),
                meal.isEdited(),
                meal.getItems().stream().map(Item::from).toList()
        );
    }
}
