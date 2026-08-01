-- Own dedicated database (transfers_db, instance postgres-transfers) — Database-per-Service,
-- same as accounts-service in Paso 1. See ADR-006 for why this stays PostgreSQL.
--
-- status doubles as the Saga state driven by TransferSagaOrchestrator: a row is created
-- PENDING and only reaches a terminal state (COMPLETED / FAILED / REVERSED) once the async
-- debit/credit round-trip with accounts-service over RabbitMQ completes. DEBITED and
-- COMPENSATING are transient in-flight states. See ADR-007 for the full saga/compensation design.

CREATE TABLE transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    source_account_id UUID NOT NULL,
    target_account_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    reference VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transfer_status CHECK (status IN ('PENDING', 'DEBITED', 'COMPLETED', 'COMPENSATING', 'REVERSED', 'FAILED'))
);

CREATE INDEX idx_transfers_user ON transfers(user_id);
CREATE INDEX idx_transfers_source ON transfers(source_account_id);
CREATE INDEX idx_transfers_target ON transfers(target_account_id);
