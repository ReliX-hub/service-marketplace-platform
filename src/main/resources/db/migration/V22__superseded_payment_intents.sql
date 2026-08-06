-- Preserve replaced PaymentIntent identities so late failure callbacks can be
-- acknowledged without mutating the current attempt, while late success remains
-- a fail-closed reconciliation error.
CREATE TABLE superseded_payment_intents (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    payment_intent_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_superseded_payment_intent UNIQUE (payment_intent_id)
);

CREATE INDEX idx_superseded_payment_intents_payment
    ON superseded_payment_intents(payment_id);
