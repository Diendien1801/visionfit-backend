package com.visionfit.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** The uploaded photo of a job, kept apart so polling a job never loads its bytes. */
@Entity
@Table(name = "analysis_photos")
public class AnalysisPhoto {

    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType;

    @Column(nullable = false)
    private byte[] bytes;

    protected AnalysisPhoto() {
        // for JPA
    }

    public AnalysisPhoto(UUID jobId, String mediaType, byte[] bytes) {
        this.jobId = jobId;
        this.mediaType = mediaType;
        this.bytes = bytes;
    }

    public String getMediaType() {
        return mediaType;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
