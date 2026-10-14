package com.visionfit.meal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Nutrition per 100 g, as the app's `NutrientsPer100g`. 900 kcal is pure fat, the most any food has. */
public record Per100g(
        @NotNull @DecimalMin("0") @DecimalMax("900") Double kcal,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double proteinG,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double carbsG,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double fatG
) {
}
