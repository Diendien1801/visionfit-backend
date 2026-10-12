package com.visionfit.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Mirrors the app's `BodyProfile`. Enum names are the wire format, so renaming one breaks the app. */
@Entity
@Table(name = "body_profiles")
public class BodyProfile {

    public enum Sex { MALE, FEMALE }

    public enum ActivityLevel { SEDENTARY, LIGHT, MODERATE, ACTIVE }

    public enum FitnessGoal { FAT_LOSS, MUSCLE_GAIN }

    /** The `uid` claim of the owner's access token; assigned, never generated. */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sex sex;

    @Column(name = "age_years", nullable = false)
    private int ageYears;

    @Column(name = "height_cm", nullable = false)
    private int heightCm;

    @Column(name = "weight_kg", nullable = false)
    private int weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", nullable = false, length = 20)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FitnessGoal goal;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BodyProfile() {
        // for JPA
    }

    public BodyProfile(UUID userId) {
        this.userId = userId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void update(Sex sex, int ageYears, int heightCm, int weightKg, ActivityLevel activityLevel, FitnessGoal goal) {
        this.sex = sex;
        this.ageYears = ageYears;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.activityLevel = activityLevel;
        this.goal = goal;
    }

    public UUID getUserId() {
        return userId;
    }

    public Sex getSex() {
        return sex;
    }

    public int getAgeYears() {
        return ageYears;
    }

    public int getHeightCm() {
        return heightCm;
    }

    public int getWeightKg() {
        return weightKg;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public FitnessGoal getGoal() {
        return goal;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
