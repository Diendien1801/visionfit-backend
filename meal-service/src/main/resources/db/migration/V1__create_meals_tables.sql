-- Confirmed meals only; meals still being analyzed belong to analysis-service.
-- user_id comes from the access token's `uid` claim; there is no foreign key
-- because users live in identity-service's database.
CREATE TABLE meals (
    id         UUID PRIMARY KEY,
    user_id    UUID          NOT NULL,
    -- The analysis job the meal came from. Unique per user so saving the same job twice
    -- returns the first meal instead of a duplicate. NULLs never collide.
    job_id     VARCHAR(64),
    type       VARCHAR(10)   NOT NULL CHECK (type IN ('BREAKFAST', 'LUNCH', 'SNACK', 'DINNER')),
    -- The user's local wall-clock time, like the app's LocalDateTime: no time zone.
    logged_at  TIMESTAMP     NOT NULL,
    photo_url  VARCHAR(2048),
    is_edited  BOOLEAN       NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_meals_user_job UNIQUE (user_id, job_id)
);

CREATE INDEX idx_meals_user_logged_at ON meals (user_id, logged_at);

-- Nutrition is stored per 100 g, like the app, so the totals always follow the weight.
CREATE TABLE meal_items (
    id               UUID PRIMARY KEY,
    meal_id          UUID             NOT NULL REFERENCES meals (id) ON DELETE CASCADE,
    position         INTEGER          NOT NULL,
    name             VARCHAR(100)     NOT NULL,
    grams            INTEGER          NOT NULL CHECK (grams BETWEEN 1 AND 5000),
    kcal_per_100g    DOUBLE PRECISION NOT NULL CHECK (kcal_per_100g BETWEEN 0 AND 900),
    protein_per_100g DOUBLE PRECISION NOT NULL CHECK (protein_per_100g BETWEEN 0 AND 100),
    carbs_per_100g   DOUBLE PRECISION NOT NULL CHECK (carbs_per_100g BETWEEN 0 AND 100),
    fat_per_100g     DOUBLE PRECISION NOT NULL CHECK (fat_per_100g BETWEEN 0 AND 100),
    confidence       INTEGER          CHECK (confidence BETWEEN 0 AND 100),
    UNIQUE (meal_id, position)
);
