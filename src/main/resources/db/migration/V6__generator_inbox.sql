CREATE TABLE generator_consumers (
    source_id VARCHAR(100) PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL UNIQUE,
    received_sequence BIGINT NOT NULL DEFAULT 0 CHECK (received_sequence >= 0),
    applied_sequence BIGINT NOT NULL DEFAULT 0 CHECK (applied_sequence >= 0 AND applied_sequence <= received_sequence),
    source_revision BIGINT NOT NULL DEFAULT -1,
    source_as_of TIMESTAMPTZ,
    tail_complete BOOLEAN NOT NULL DEFAULT FALSE,
    projection JSONB NOT NULL DEFAULT '{"accounts":[],"services":[],"transactions":[]}',
    published_projection JSONB,
    published_at TIMESTAMPTZ,
    published_revision BIGINT,
    failure_count INTEGER NOT NULL DEFAULT 0,
    retry_after TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(64),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE generator_inbox (
    run_id VARCHAR(255) NOT NULL REFERENCES generator_consumers(run_id),
    sequence BIGINT NOT NULL CHECK (sequence > 0),
    event_id VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL CHECK (recorded_at >= occurred_at),
    payload JSONB NOT NULL,
    applied_at TIMESTAMPTZ,
    PRIMARY KEY (run_id, sequence),
    UNIQUE (run_id, event_id)
);

CREATE INDEX generator_inbox_pending ON generator_inbox (run_id, sequence) WHERE applied_at IS NULL;
