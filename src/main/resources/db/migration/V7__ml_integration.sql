ALTER TABLE generator_consumers ADD COLUMN published_sequence BIGINT;
UPDATE generator_consumers SET published_sequence = applied_sequence
WHERE published_projection IS NOT NULL AND published_revision = source_revision
  AND tail_complete AND applied_sequence = received_sequence;

CREATE TABLE ml_assessment_audit (
    snapshot_id VARCHAR(512) NOT NULL,
    prediction_at TIMESTAMPTZ NOT NULL,
    service_id BIGINT NOT NULL,
    settings_hash VARCHAR(64) NOT NULL,
    model_version VARCHAR(255) NOT NULL,
    algorithm_score DOUBLE PRECISION,
    ml_probability DOUBLE PRECISION,
    ml_adjustment DOUBLE PRECISION NOT NULL,
    final_score DOUBLE PRECISION,
    mode VARCHAR(32) NOT NULL,
    fallback_reason VARCHAR(100),
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(snapshot_id, prediction_at, service_id, settings_hash, model_version)
);
