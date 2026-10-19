package com.visionfit.analysis.dto;

import com.visionfit.analysis.domain.AnalysisJob;
import com.visionfit.analysis.domain.Detection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** An analysis job as the app reads it. Field names match the app's `AnalysisJobDto`. */
public record JobDto(
        UUID id,
        AnalysisJob.MealType mealType,
        LocalDateTime capturedAt,
        String photoRef,
        long photoSizeBytes,
        AnalysisJob.Stage stage,
        AnalysisJob.Failure failure,
        Timings timings,
        List<DetectionDto> detections
) {
    /** Each step's duration, `null` until the step has run. The upload is timed by the app. */
    public record Timings(Long queueMs, Long recognitionMs, Long calculationMs) {
    }

    public record Per100g(double kcal, double proteinG, double carbsG, double fatG) {
    }

    /** Fractions of the photo: centers from its top-left corner, radius relative to its width. */
    public record Region(double centerX, double centerY, double radius) {
    }

    public record DetectionDto(UUID id, String name, int grams, Per100g per100g, int confidence, Region region) {

        static DetectionDto from(Detection d) {
            Region region = d.getCenterX() == null || d.getCenterY() == null || d.getRadius() == null
                    ? null
                    : new Region(d.getCenterX(), d.getCenterY(), d.getRadius());
            return new DetectionDto(
                    d.getId(),
                    d.getName(),
                    d.getGrams(),
                    new Per100g(d.getKcalPer100g(), d.getProteinPer100g(), d.getCarbsPer100g(), d.getFatPer100g()),
                    d.getConfidence(),
                    region
            );
        }
    }

    public static JobDto from(AnalysisJob job) {
        return new JobDto(
                job.getId(),
                job.getMealType(),
                job.getCapturedAt(),
                job.getPhotoRef(),
                job.getPhotoSizeBytes(),
                job.getStage(),
                job.getFailure(),
                new Timings(job.getQueueMs(), job.getRecognitionMs(), job.getCalculationMs()),
                job.getDetections().stream().map(DetectionDto::from).toList()
        );
    }
}
