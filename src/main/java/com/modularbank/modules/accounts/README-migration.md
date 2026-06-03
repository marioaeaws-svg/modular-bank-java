# accounts — Migration Guide

## Public interface
```java
AccountsService.createAccount(UUID userId) → AccountSummary
AccountsService.getBalance(UUID accountId) → Money
AccountsService.debit(UUID accountId, Money amount, String reference)
AccountsService.credit(UUID accountId, Money amount, String reference)
AccountsService.findByOwner(UUID userId) → List<AccountSummary>
```

## Consumers
- `transfers` module (debit, credit, getBalance)

## To extract as microservice
1. Create `accounts-service` with the same DB schema
2. Replace `AccountsServiceImpl` with `AccountsHttpClient`:
   - GET  /internal/accounts/{id}/balance
   - POST /internal/accounts/{id}/debit  { amount, reference }
   - POST /internal/accounts/{id}/credit { amount, reference }
3. Critical: debit + credit are no longer in the same DB transaction as the transfer record.
   The `transfers` module must implement a Saga with compensation:
   - If credit fails after debit succeeds → POST /internal/accounts/{id}/credit (compensate)
   - Consider the Outbox Pattern for guaranteed delivery
