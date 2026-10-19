package com.visionfit.analysis.controller;

import com.visionfit.analysis.domain.AnalysisJob;
import com.visionfit.analysis.dto.JobDto;
import com.visionfit.analysis.service.AnalysisService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/** The caller's own analysis jobs. The user id always comes from the token, never from the request. */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /** 202: the job exists and the analysis runs in the background; poll `GET /{id}` for the result. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobDto> submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam MultipartFile photo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime capturedAt,
            @RequestParam AnalysisJob.MealType mealType,
            @RequestParam String photoRef
    ) throws IOException {
        if (photo.isEmpty() || !AnalysisService.MEDIA_TYPES.contains(photo.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photo must be a JPEG, PNG or WebP image");
        }
        if (photoRef.isBlank() || photoRef.length() > 2048) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photoRef must be 1 to 2048 characters");
        }
        JobDto job = analysisService.submit(userId(jwt), mealType, capturedAt, photoRef, photo.getBytes(), photo.getContentType());
        return ResponseEntity.accepted().body(job);
    }

    /** 404 for a job that does not exist or is someone else's. */
    @GetMapping("/{id}")
    public ResponseEntity<JobDto> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.of(analysisService.get(userId(jwt), id));
    }

    /** 202 when the analysis starts again, 409 when the job has not failed, 404 when there is no such job. */
    @PostMapping("/{id}/retry")
    public ResponseEntity<JobDto> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return analysisService.retry(userId(jwt), id)
                .map(job -> ResponseEntity.accepted().body(job))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Safe to parse: SecurityConfig rejects tokens whose `uid` is not a UUID. */
    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("uid"));
    }
}
