package com.visionfit.meal.dto;

/** Consecutive days with at least one meal, counted back from the day before `today`. */
public record StreakDto(int days) {
}
