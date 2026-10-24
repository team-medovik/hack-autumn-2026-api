CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    login VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    provisioning_source VARCHAR(32) NOT NULL,
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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT billing_snapshots_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_billing_snapshots_taken_at ON billing_snapshots (taken_at DESC, id DESC);

CREATE TABLE model_settings (
    version BIGINT PRIMARY KEY,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    CONSTRAINT model_settings_payload_object CHECK (jsonb_typeof(payload) = 'object')
);
