package com.visionfit.meal.dto;

import com.visionfit.meal.domain.Meal;
import com.visionfit.meal.domain.MealItem;
import com.visionfit.meal.dto.SaveMealRequest.Item;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of `PUT /api/v1/meals/{id}`: the meal type and the complete new list of dishes.
 * The job, time and photo of a saved meal never change, and the server marks it as edited.
 */
public record UpdateMealRequest(
        @NotNull Meal.Type type,
        @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid Item> items
) {
    public List<MealItem> toMealItems() {
        return SaveMealRequest.toMealItems(items);
    }
}
