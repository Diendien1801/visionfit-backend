package com.visionfit.meal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** One dish of a {@link Meal}, mirrors the app's `FoodItem`. Nutrition is per 100 g so totals follow the weight. */
@Entity
@Table(name = "meal_items")
public class MealItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Order the user saw the dishes in. */
    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int grams;

    @Column(name = "kcal_per_100g", nullable = false)
    private double kcalPer100g;

    @Column(name = "protein_per_100g", nullable = false)
    private double proteinPer100g;

    @Column(name = "carbs_per_100g", nullable = false)
    private double carbsPer100g;

    @Column(name = "fat_per_100g", nullable = false)
    private double fatPer100g;

    /** AI confidence 0–100, `null` when the user added the dish by hand. */
    private Integer confidence;

    protected MealItem() {
        // for JPA
    }

    public MealItem(int position, String name, int grams, double kcalPer100g, double proteinPer100g,
                    double carbsPer100g, double fatPer100g, Integer confidence) {
        this.position = position;
        this.name = name;
        this.grams = grams;
        this.kcalPer100g = kcalPer100g;
        this.proteinPer100g = proteinPer100g;
        this.carbsPer100g = carbsPer100g;
        this.fatPer100g = fatPer100g;
        this.confidence = confidence;
    }

    /** Takes the values of `other`, keeping this row's id and position. */
    void copyValuesFrom(MealItem other) {
        this.name = other.name;
        this.grams = other.grams;
        this.kcalPer100g = other.kcalPer100g;
        this.proteinPer100g = other.proteinPer100g;
        this.carbsPer100g = other.carbsPer100g;
        this.fatPer100g = other.fatPer100g;
        this.confidence = other.confidence;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getGrams() {
        return grams;
    }

    public double getKcalPer100g() {
        return kcalPer100g;
    }

    public double getProteinPer100g() {
        return proteinPer100g;
    }

    public double getCarbsPer100g() {
        return carbsPer100g;
    }

    public double getFatPer100g() {
        return fatPer100g;
    }

    public Integer getConfidence() {
        return confidence;
    }
}
