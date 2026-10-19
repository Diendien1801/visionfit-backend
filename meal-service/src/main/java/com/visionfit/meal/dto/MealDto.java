package com.visionfit.meal.dto;

import com.visionfit.meal.domain.Meal;
import com.visionfit.meal.domain.MealItem;
import com.visionfit.meal.domain.Nutrition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A saved meal as the API returns it, with its totals computed here so the app only shows them.
 * The app still builds the title from the dish names.
 */
public record MealDto(
        UUID id,
        String jobId,
        Meal.Type type,
        LocalDateTime loggedAt,
        String photoUrl,
        boolean isEdited,
        int kcal,
        double proteinG,
        double carbsG,
        double fatG,
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
        Nutrition total = meal.nutrition();
        return new MealDto(
                meal.getId(),
                meal.getJobId(),
                meal.getType(),
                meal.getLoggedAt(),
                meal.getPhotoUrl(),
                meal.isEdited(),
                total.kcal(),
                total.proteinG(),
                total.carbsG(),
                total.fatG(),
                meal.getItems().stream().map(Item::from).toList()
        );
    }
}
