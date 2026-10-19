package com.visionfit.meal.domain;

import java.util.Collection;
import java.util.function.Function;

/**
 * What a dish, a meal or a day adds up to. The one place the app's numbers come from: the app
 * shows these, it does not recompute them.
 *
 * Calories are whole numbers rounded per dish, then summed, so a meal always equals the sum of
 * the dishes the user sees. Macros stay unrounded until displayed.
 */
public record Nutrition(int kcal, double proteinG, double carbsG, double fatG) {

    public static final Nutrition ZERO = new Nutrition(0, 0, 0, 0);

    public Nutrition plus(Nutrition other) {
        return new Nutrition(kcal + other.kcal, proteinG + other.proteinG, carbsG + other.carbsG, fatG + other.fatG);
    }

    public static <T> Nutrition sum(Collection<T> parts, Function<T, Nutrition> nutrition) {
        return parts.stream().map(nutrition).reduce(ZERO, Nutrition::plus);
    }
}
