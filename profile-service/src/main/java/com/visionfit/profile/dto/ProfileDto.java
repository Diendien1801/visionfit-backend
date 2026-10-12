package com.visionfit.profile.dto;

import com.visionfit.profile.domain.BodyProfile;
import com.visionfit.profile.domain.BodyProfile.ActivityLevel;
import com.visionfit.profile.domain.BodyProfile.FitnessGoal;
import com.visionfit.profile.domain.BodyProfile.Sex;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Request and response body of `/api/v1/profile/me`. Ranges match the app's `BodyProfile`. */
public record ProfileDto(
        @NotNull Sex sex,
        @NotNull @Min(14) @Max(90) Integer ageYears,
        @NotNull @Min(120) @Max(220) Integer heightCm,
        @NotNull @Min(30) @Max(200) Integer weightKg,
        @NotNull ActivityLevel activityLevel,
        @NotNull FitnessGoal goal
) {
    public static ProfileDto from(BodyProfile profile) {
        return new ProfileDto(
                profile.getSex(),
                profile.getAgeYears(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getActivityLevel(),
                profile.getGoal()
        );
    }
}
