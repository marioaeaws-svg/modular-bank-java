-- Idempotent-consumer ledger, see ADR-010 and docs/evidencia/paso-3/03-patrones-resiliencia.md.
-- Shared by both transfer-event consumers (notifications, audit); the (event_id, consumer)
-- unique constraint tracks each of them independently since both read the same exchange.

CREATE TABLE processed_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(64) NOT NULL,
    consumer VARCHAR(150) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_processed_messages_event_consumer UNIQUE (event_id, consumer)
);
