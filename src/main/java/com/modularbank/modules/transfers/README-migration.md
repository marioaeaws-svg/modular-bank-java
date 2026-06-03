# transfers — Migration Guide

## Public interface
Transfers has no public interface — it is the top-level orchestrator.

## Consumers
None (exposed only via REST API)

## To extract as microservice
1. Create `transfers-service` with the same DB schema
2. It becomes a Saga orchestrator calling accounts-service, notifications-service, audit-service via HTTP
3. Implement compensation logic:
   - Step 1: debit source account
   - Step 2: credit target account → if fails, compensate step 1
   - Step 3: save transfer record → if this fails after debit+credit succeeded, there is no automatic rollback across services. Use the Outbox Pattern or idempotency keys to ensure the record is eventually persisted without re-debiting.
   - Step 4: notify (fire-and-forget)
   - Step 5: audit (fire-and-forget)
