package com.visionfit.meal.dto;

import com.visionfit.meal.domain.Meal;
import com.visionfit.meal.domain.Nutrition;

import java.time.LocalDate;
import java.util.List;

/**
 * One day of the diary: what was eaten in total and the meals behind it, newest first.
 * Days without meals are returned too, with zeros, so the app never has to fill gaps.
 */
public record DayDto(
        LocalDate date,
        int kcal,
        double proteinG,
        double carbsG,
        double fatG,
        int mealCount,
        List<MealDto> meals
) {
    /** @param meals the day's meals, already newest first. */
    public static DayDto of(LocalDate date, List<Meal> meals) {
        Nutrition total = Nutrition.sum(meals, Meal::nutrition);
        return new DayDto(
                date,
                total.kcal(),
                total.proteinG(),
                total.carbsG(),
                total.fatG(),
                meals.size(),
                meals.stream().map(MealDto::from).toList()
        );
    }
}
