-- One row per photo the user sends for analysis. user_id comes from the access token's `uid`
-- claim; there is no foreign key because users live in identity-service's database.
CREATE TABLE analysis_jobs (
    id               UUID PRIMARY KEY,
    user_id          UUID          NOT NULL,
    meal_type        VARCHAR(10)   NOT NULL CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'SNACK', 'DINNER')),
    -- The user's local wall-clock time, like the app's LocalDateTime: no time zone.
    captured_at      TIMESTAMP     NOT NULL,
    -- The app's own reference to the photo (bundled://…, later a file path), returned as sent.
    photo_ref        VARCHAR(2048) NOT NULL,
    photo_size_bytes BIGINT        NOT NULL,
    stage            VARCHAR(12)   NOT NULL CHECK (stage IN ('RECOGNIZING', 'CALCULATING', 'COMPLETED', 'FAILED')),
    failure          VARCHAR(30)   CHECK (failure IN ('INVALID_AI_RESPONSE', 'NO_FOOD_DETECTED')),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    -- When the job last entered the queue (creation or retry); queue_ms is measured from here.
    queued_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    -- NULL until a worker picks the job up.
    queue_ms         BIGINT,
    recognition_ms   BIGINT,
    calculation_ms   BIGINT
);

CREATE INDEX idx_analysis_jobs_user ON analysis_jobs (user_id, captured_at);

-- The uploaded photo, in its own table so reading a job (polled every second) never loads it.
-- Kept so a failed job can be analyzed again without a new upload.
-- ponytail: photos in the database; move them to object storage (S3/MinIO) once real cameras send many.
CREATE TABLE analysis_photos (
    job_id     UUID PRIMARY KEY REFERENCES analysis_jobs (id) ON DELETE CASCADE,
    media_type VARCHAR(20) NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
    bytes      BYTEA       NOT NULL
);

-- Dishes the model found. Ranges match meal-service's meal_items, so every dish can be saved as is.
CREATE TABLE analysis_detections (
    id               UUID PRIMARY KEY,
    job_id           UUID             NOT NULL REFERENCES analysis_jobs (id) ON DELETE CASCADE,
    position         INTEGER          NOT NULL,
    name             VARCHAR(100)     NOT NULL,
    grams            INTEGER          NOT NULL CHECK (grams BETWEEN 1 AND 5000),
    kcal_per_100g    DOUBLE PRECISION NOT NULL CHECK (kcal_per_100g BETWEEN 0 AND 900),
    protein_per_100g DOUBLE PRECISION NOT NULL CHECK (protein_per_100g BETWEEN 0 AND 100),
    carbs_per_100g   DOUBLE PRECISION NOT NULL CHECK (carbs_per_100g BETWEEN 0 AND 100),
    fat_per_100g     DOUBLE PRECISION NOT NULL CHECK (fat_per_100g BETWEEN 0 AND 100),
    confidence       INTEGER          NOT NULL CHECK (confidence BETWEEN 0 AND 100),
    -- Where the dish sits on the photo, as fractions of the image (center and radius); NULL when unknown.
    center_x         DOUBLE PRECISION CHECK (center_x BETWEEN 0 AND 1),
    center_y         DOUBLE PRECISION CHECK (center_y BETWEEN 0 AND 1),
    radius           DOUBLE PRECISION CHECK (radius BETWEEN 0 AND 1),
    UNIQUE (job_id, position)
);
