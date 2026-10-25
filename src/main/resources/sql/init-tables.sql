CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    login VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    provisioning_source VARCHAR(32) NOT NULL,
    refresh_version BIGINT NOT NULL DEFAULT 0,
    password_self_changed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_accounts_login_length CHECK (char_length(login) > 3)
);

CREATE INDEX idx_user_accounts_login ON user_accounts (login);

CREATE TABLE oidc_identities (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL UNIQUE REFERENCES user_accounts(id) ON DELETE CASCADE,
    issuer VARCHAR(2048) NOT NULL,
    subject VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (issuer, subject)
);

CREATE INDEX idx_oidc_identities_issuer_subject ON oidc_identities (issuer, subject);

CREATE TABLE billing_snapshots (
    id VARCHAR(255) PRIMARY KEY,
    taken_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload JSONB NOT NULL,
    file_name VARCHAR(255) NOT NULL DEFAULT 'data.json',
    uploaded_by VARCHAR(255) NOT NULL DEFAULT 'system',
    clients_count INTEGER NOT NULL DEFAULT 0,
    services_count INTEGER NOT NULL DEFAULT 0,
    raw_file BYTEA,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT billing_snapshots_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_billing_snapshots_taken_at ON billing_snapshots (taken_at DESC, id DESC);

CREATE TABLE calculation_date_settings (
    id BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    mode VARCHAR(7) NOT NULL DEFAULT 'current' CHECK (mode IN ('current', 'fixed')),
    fixed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT calculation_date_fixed_at CHECK (mode = 'current' OR fixed_at IS NOT NULL)
);

CREATE TABLE model_settings (
    version BIGINT PRIMARY KEY,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    CONSTRAINT model_settings_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

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
