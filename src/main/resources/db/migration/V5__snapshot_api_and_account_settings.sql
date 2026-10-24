ALTER TABLE billing_snapshots
    ADD COLUMN file_name VARCHAR(255) NOT NULL DEFAULT 'data.json',
    ADD COLUMN uploaded_by VARCHAR(255) NOT NULL DEFAULT 'system',
    ADD COLUMN clients_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN services_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN raw_file BYTEA;

UPDATE billing_snapshots
SET clients_count = CASE WHEN jsonb_typeof(payload -> 'accounts') = 'array'
    THEN jsonb_array_length(payload -> 'accounts') ELSE 0 END,
    services_count = CASE WHEN jsonb_typeof(payload -> 'services') = 'array'
    THEN jsonb_array_length(payload -> 'services') ELSE 0 END;

CREATE TABLE calculation_date_settings (
    id BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    mode VARCHAR(7) NOT NULL DEFAULT 'current' CHECK (mode IN ('current', 'fixed')),
    fixed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT calculation_date_fixed_at CHECK (mode = 'current' OR fixed_at IS NOT NULL)
);

INSERT INTO calculation_date_settings (id, mode) VALUES (TRUE, 'current');

ALTER TABLE user_accounts ADD COLUMN refresh_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_accounts ADD COLUMN password_self_changed BOOLEAN NOT NULL DEFAULT FALSE;
