# notifications — Migration Guide

## Public interface
`NotificationsService.send(UUID userId, NotificationType type, Map<String,String> payload)`

## Consumers
- `transfers` module (on transfer sent/received)
- `auth` module (on login)

## To extract as microservice
1. Create `notifications-service` with the same DB schema
2. Replace `NotificationsServiceImpl` with `NotificationsHttpClient`:
   - POST /internal/notifications with body {userId, type, payload}
   - This call becomes fire-and-forget (non-blocking)
3. The transfer transaction no longer rolls back if notification fails
   — consider an outbox pattern if reliability is required
