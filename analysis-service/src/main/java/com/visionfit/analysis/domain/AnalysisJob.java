package com.visionfit.analysis.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One photo going through analysis, mirrors the app's `AnalysisJob`. Enum names are the wire
 * format, so renaming one breaks the app.
 */
@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {

    public enum MealType { BREAKFAST, LUNCH, SNACK, DINNER }

    /** There is no "queued" stage: like the app, a job is RECOGNIZING from the moment it exists. */
    public enum Stage {
        RECOGNIZING, CALCULATING, COMPLETED, FAILED;

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED;
        }
    }

    public enum Failure {
        /** The model failed, was unreachable, refused, or answered something unusable. */
        INVALID_AI_RESPONSE,
        NO_FOOD_DETECTED,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The `uid` claim of the owner's access token. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 10)
    private MealType mealType;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @Column(name = "photo_ref", nullable = false, length = 2048)
    private String photoRef;

    @Column(name = "photo_size_bytes", nullable = false)
    private long photoSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Failure failure;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "queue_ms")
    private Long queueMs;

    @Column(name = "recognition_ms")
    private Long recognitionMs;

    @Column(name = "calculation_ms")
    private Long calculationMs;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "job_id", nullable = false)
    @OrderBy("position")
    private List<Detection> detections = new ArrayList<>();

    protected AnalysisJob() {
        // for JPA
    }

    public AnalysisJob(UUID userId, MealType mealType, LocalDateTime capturedAt, String photoRef, long photoSizeBytes, Instant now) {
        this.userId = userId;
        this.mealType = mealType;
        this.capturedAt = capturedAt;
        this.photoRef = photoRef;
        this.photoSizeBytes = photoSizeBytes;
        this.stage = Stage.RECOGNIZING;
        this.createdAt = now;
        this.queuedAt = now;
    }

    /** `false` when a worker already took this run of the job, so it is not analyzed twice. */
    public boolean start(Instant now) {
        if (stage != Stage.RECOGNIZING || queueMs != null) return false;
        queueMs = Duration.between(queuedAt, now).toMillis();
        return true;
    }

    /** The model answered: its dishes are shown while the server checks and stores them. */
    public void recognized(List<Detection> found, long recognitionMs) {
        this.stage = Stage.CALCULATING;
        this.recognitionMs = recognitionMs;
        this.detections.addAll(found);
    }

    public void complete(long calculationMs) {
        this.stage = Stage.COMPLETED;
        this.calculationMs = calculationMs;
    }

    public void fail(Failure failure, Long recognitionMs) {
        this.stage = Stage.FAILED;
        this.failure = failure;
        this.recognitionMs = recognitionMs;
    }

    /** Back to the start: after a retry, or after the service stopped while the job was running. */
    public void requeue(Instant now) {
        stage = Stage.RECOGNIZING;
        failure = null;
        detections.clear();
        queuedAt = now;
        queueMs = null;
        recognitionMs = null;
        calculationMs = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public MealType getMealType() {
        return mealType;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public String getPhotoRef() {
        return photoRef;
    }

    public long getPhotoSizeBytes() {
        return photoSizeBytes;
    }

    public Stage getStage() {
        return stage;
    }

    public Failure getFailure() {
        return failure;
    }

    public Long getQueueMs() {
        return queueMs;
    }

    public Long getRecognitionMs() {
        return recognitionMs;
    }

    public Long getCalculationMs() {
        return calculationMs;
    }

    public List<Detection> getDetections() {
        return detections;
    }
}
