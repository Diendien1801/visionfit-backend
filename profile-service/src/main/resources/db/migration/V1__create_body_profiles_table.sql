-- One row per user. user_id comes from the access token's `uid` claim; there is no foreign key
-- because users live in identity-service's database.
CREATE TABLE body_profiles (
    user_id        UUID PRIMARY KEY,
    sex            VARCHAR(10) NOT NULL CHECK (sex IN ('MALE', 'FEMALE')),
    age_years      INTEGER     NOT NULL CHECK (age_years BETWEEN 14 AND 90),
    height_cm      INTEGER     NOT NULL CHECK (height_cm BETWEEN 120 AND 220),
    weight_kg      INTEGER     NOT NULL CHECK (weight_kg BETWEEN 30 AND 200),
    activity_level VARCHAR(20) NOT NULL CHECK (activity_level IN ('SEDENTARY', 'LIGHT', 'MODERATE', 'ACTIVE')),
    goal           VARCHAR(20) NOT NULL CHECK (goal IN ('FAT_LOSS', 'MUSCLE_GAIN')),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
