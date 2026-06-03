# audit — Migration Guide

## Public interface
`AuditService.record(UUID userId, String action, Map<String,String> metadata)`

## Consumers
- `transfers` module (on transfer executed)
- `accounts` module (on account created)
- `auth` module (on register/login)

## To extract as microservice
1. Create `audit-service` with the same DB schema
2. Replace `AuditServiceImpl` with `AuditHttpClient`
3. Audit calls must be fire-and-forget — audit failures must NOT roll back business transactions
4. Consider an outbox pattern or message queue for guaranteed delivery
