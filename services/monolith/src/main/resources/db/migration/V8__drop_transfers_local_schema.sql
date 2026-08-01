-- Contract phase of the expand-migrate-contract strategy (same pattern as V7 for accounts):
-- transfers.transfers has been recreated as its own table in transfers-service's own
-- database (transfers_db, instance postgres-transfers) — see
-- docs/evidencia/paso-2/05-patron-consistencia-distribuida.md. The monolith no longer
-- runs the transfers module (extracted to transfers-service in Paso 2); TransferUseCase
-- and its schema are gone, replaced by the async notifications/audit listeners on
-- transfers.events. In a real rollout this migration would only ship after a
-- bake/verification period confirming the cutover was correct.

DROP TABLE IF EXISTS transfers.transfers;
DROP SCHEMA IF EXISTS transfers;
