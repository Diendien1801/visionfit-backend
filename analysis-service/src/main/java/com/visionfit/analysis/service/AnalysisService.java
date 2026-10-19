package com.visionfit.analysis.service;

import com.visionfit.analysis.ai.DishRecognizer;
import com.visionfit.analysis.ai.DishRecognizer.RecognizedDish;
import com.visionfit.analysis.domain.AnalysisJob;
import com.visionfit.analysis.domain.AnalysisJob.Failure;
import com.visionfit.analysis.domain.AnalysisJob.Stage;
import com.visionfit.analysis.domain.AnalysisPhoto;
import com.visionfit.analysis.domain.Detection;
import com.visionfit.analysis.dto.JobDto;
import com.visionfit.analysis.repository.AnalysisJobRepository;
import com.visionfit.analysis.repository.AnalysisPhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Accepts photos and runs each analysis on a background thread, so the app gets its job id at once
 * and polls the job while the model works. Every stage is stored, so a poll always sees the latest.
 */
@Service
public class AnalysisService {

    /** What Claude reads. GIF is left out: nobody photographs a meal as a GIF. */
    public static final Set<String> MEDIA_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AnalysisJobRepository jobs;
    private final AnalysisPhotoRepository photos;
    private final DishRecognizer recognizer;
    private final TaskExecutor executor;
    private final TransactionTemplate tx;

    public AnalysisService(AnalysisJobRepository jobs, AnalysisPhotoRepository photos, DishRecognizer recognizer,
                           TaskExecutor executor, PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.photos = photos;
        this.recognizer = recognizer;
        this.executor = executor;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Stores the job and its photo, then starts the analysis after the commit, so the worker sees both. */
    public JobDto submit(UUID userId, AnalysisJob.MealType mealType, LocalDateTime capturedAt, String photoRef,
                         byte[] photo, String mediaType) {
        JobDto job = tx.execute(status -> {
            AnalysisJob saved = jobs.saveAndFlush(new AnalysisJob(userId, mealType, capturedAt, photoRef, photo.length, Instant.now()));
            photos.save(new AnalysisPhoto(saved.getId(), mediaType, photo));
            return JobDto.from(saved);
        });
        dispatch(job.id());
        return job;
    }

    /** Empty for a job that does not exist or is someone else's: the two are not told apart. */
    public Optional<JobDto> get(UUID userId, UUID jobId) {
        return tx.execute(status -> jobs.findByIdAndUserId(jobId, userId).map(JobDto::from));
    }

    /** Analyzes the stored photo again. Only a failed job can be retried (409 otherwise). */
    public Optional<JobDto> retry(UUID userId, UUID jobId) {
        Optional<JobDto> job = tx.execute(status -> jobs.findByIdAndUserId(jobId, userId).map(found -> {
            if (found.getStage() != Stage.FAILED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a failed analysis can be retried");
            }
            found.requeue(Instant.now());
            return JobDto.from(jobs.saveAndFlush(found));
        }));
        job.ifPresent(retried -> dispatch(retried.id()));
        return job;
    }

    /**
     * Jobs that were running when the service stopped start over.
     * ponytail: assumes one instance; with several, claim jobs with SELECT … FOR UPDATE SKIP LOCKED.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        List<UUID> unfinished = tx.execute(status -> jobs.findByStageIn(List.of(Stage.RECOGNIZING, Stage.CALCULATING)).stream()
                .map(job -> {
                    job.requeue(Instant.now());
                    return job.getId();
                })
                .toList());
        if (!unfinished.isEmpty()) log.info("Resuming {} unfinished analyses", unfinished.size());
        unfinished.forEach(this::dispatch);
    }

    private void dispatch(UUID jobId) {
        executor.execute(() -> {
            try {
                process(jobId);
            } catch (RuntimeException e) {
                log.error("Analysis {} crashed", jobId, e);
                update(jobId, job -> job.fail(Failure.INVALID_AI_RESPONSE, null));
            }
        });
    }

    private void process(UUID jobId) {
        Boolean started = tx.execute(status -> jobs.findById(jobId).map(job -> job.start(Instant.now())).orElse(false));
        if (!Boolean.TRUE.equals(started)) return;
        AnalysisPhoto photo = photos.findById(jobId).orElseThrow();

        long recognitionStart = System.nanoTime();
        List<RecognizedDish> dishes;
        try {
            dishes = recognizer.recognize(photo.getBytes(), photo.getMediaType());
        } catch (Exception e) {
            log.warn("Analysis {}: the model failed: {}", jobId, e.toString());
            long recognitionMs = millisSince(recognitionStart);
            update(jobId, job -> job.fail(Failure.INVALID_AI_RESPONSE, recognitionMs));
            return;
        }
        long recognitionMs = millisSince(recognitionStart);

        // One model call returns names and nutrition together; "calculating" is the server checking and storing them.
        long calculationStart = System.nanoTime();
        List<Detection> detections = toDetections(dishes);
        if (detections.isEmpty()) {
            update(jobId, job -> job.fail(Failure.NO_FOOD_DETECTED, recognitionMs));
            return;
        }
        update(jobId, job -> job.recognized(detections, recognitionMs));
        long calculationMs = millisSince(calculationStart);
        update(jobId, job -> job.complete(calculationMs));
    }

    private void update(UUID jobId, Consumer<AnalysisJob> change) {
        tx.executeWithoutResult(status -> jobs.findById(jobId).ifPresent(change));
    }

    /**
     * Keeps what the app can use: dishes without a name or weight are dropped, and every number is
     * pulled into the ranges meal-service accepts, so any dish found can be saved as it is.
     */
    static List<Detection> toDetections(List<RecognizedDish> dishes) {
        List<Detection> detections = new ArrayList<>();
        for (RecognizedDish dish : dishes == null ? List.<RecognizedDish>of() : dishes) {
            String name = dish.name() == null ? "" : dish.name().strip();
            if (name.isEmpty() || dish.grams() <= 0) continue;
            boolean hasRegion = Double.isFinite(dish.centerX()) && Double.isFinite(dish.centerY()) && Double.isFinite(dish.radius())
                    && dish.radius() > 0;
            detections.add(new Detection(
                    detections.size(),
                    name.length() > 100 ? name.substring(0, 100) : name,
                    Math.min(dish.grams(), 5000),
                    clamp(dish.kcalPer100g(), 900),
                    clamp(dish.proteinPer100g(), 100),
                    clamp(dish.carbsPer100g(), 100),
                    clamp(dish.fatPer100g(), 100),
                    (int) clamp(dish.confidence(), 100),
                    hasRegion ? clamp(dish.centerX(), 1) : null,
                    hasRegion ? clamp(dish.centerY(), 1) : null,
                    hasRegion ? clamp(dish.radius(), 1) : null
            ));
        }
        return detections;
    }

    private static double clamp(double value, double max) {
        return Double.isFinite(value) ? Math.max(0, Math.min(value, max)) : 0;
    }

    private static long millisSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }
}
