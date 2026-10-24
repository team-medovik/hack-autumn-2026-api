CREATE TABLE billing_snapshots (
    id VARCHAR(255) PRIMARY KEY,
    taken_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT billing_snapshots_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_billing_snapshots_taken_at ON billing_snapshots (taken_at DESC, id DESC);
