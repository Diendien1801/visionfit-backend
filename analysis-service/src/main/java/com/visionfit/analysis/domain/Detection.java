package com.visionfit.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One dish found on the photo, mirrors the app's `DetectedFood`. Nutrition is per 100 g so the
 * app can rescale it when the user corrects the weight. The region is `null` when unknown.
 */
@Entity
@Table(name = "analysis_detections")
public class Detection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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

    @Column(nullable = false)
    private int confidence;

    @Column(name = "center_x")
    private Double centerX;

    @Column(name = "center_y")
    private Double centerY;

    @Column
    private Double radius;

    protected Detection() {
        // for JPA
    }

    public Detection(int position, String name, int grams, double kcalPer100g, double proteinPer100g, double carbsPer100g,
                     double fatPer100g, int confidence, Double centerX, Double centerY, Double radius) {
        this.position = position;
        this.name = name;
        this.grams = grams;
        this.kcalPer100g = kcalPer100g;
        this.proteinPer100g = proteinPer100g;
        this.carbsPer100g = carbsPer100g;
        this.fatPer100g = fatPer100g;
        this.confidence = confidence;
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
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

    public int getConfidence() {
        return confidence;
    }

    public Double getCenterX() {
        return centerX;
    }

    public Double getCenterY() {
        return centerY;
    }

    public Double getRadius() {
        return radius;
    }
}
