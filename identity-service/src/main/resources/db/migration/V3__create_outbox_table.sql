-- Events waiting to go to RabbitMQ (outbox pattern). A row is written in the same transaction as the
-- change it reports, so the two are saved together or not at all; OutboxRelay sends it afterwards.
CREATE TABLE outbox (
    -- Sent as the message id: a consumer that gets the same event twice can tell.
    id          UUID PRIMARY KEY,
    -- The routing key, e.g. user.deleted.
    type        VARCHAR(100) NOT NULL,
    -- The message body as JSON. TEXT, not JSONB: nothing here ever queries inside it.
    payload     TEXT         NOT NULL,
    -- X-Request-Id of the request that caused the event, so consumers log under the same id.
    request_id  VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- NULL until RabbitMQ has confirmed the message.
    sent_at     TIMESTAMPTZ
);

-- The relay asks for the oldest unsent rows every few seconds. Only unsent rows are indexed, so the
-- index stays a few entries long however many events have ever been sent.
CREATE INDEX idx_outbox_unsent ON outbox (created_at) WHERE sent_at IS NULL;
