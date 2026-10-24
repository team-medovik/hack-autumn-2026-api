CREATE TABLE model_settings (
    version BIGINT PRIMARY KEY,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    CONSTRAINT model_settings_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

INSERT INTO model_settings (version, updated_at, updated_by, payload)
VALUES (
    1,
    CURRENT_TIMESTAMP,
    'system',
    '{"factorWeights":{"awaitingPayment":70,"expiringWithoutAutoRenewal":50,"insufficientFunds":40,"awaitingPaymentWithoutFunds":30,"disabledByClient":15,"bonusesExceedMoney":10},"riskThresholds":{"medium":40,"high":70},"finance":{"bonusesAsMoney":true,"referralBalanceAsMoney":false,"balanceLossFactor":0.5},"forecast":{"horizonDays":5,"recalculationIntervalMinutes":5}}'::jsonb
);
