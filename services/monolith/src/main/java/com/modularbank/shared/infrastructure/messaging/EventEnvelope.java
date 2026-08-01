package com.modularbank.shared.infrastructure.messaging;

import java.time.Instant;
import java.util.Map;

/**
 * Wire-level envelope for every AMQP message this service receives — see
 * ADR-010. {@code eventId} is the deduplication key for idempotent consumers
 * (see NotificationsTransferEventsListener / AuditTransferEventsListener);
 * {@code eventType} carries the business type as a string so the RabbitMQ
 * type mapper only ever needs to know about this one wrapper class.
 *
 * {@code tracingContext} carries the W3C trace context injected by
 * transfers-service (see its EventEnvelopeFactory) so a distributed trace
 * continues across the broker hop into this consumer (Paso 4, see ADR-009) —
 * done explicitly here rather than via raw AMQP headers/Spring AMQP's own
 * observation, see EventEnvelope's javadoc in transfers-service for why.
 */
public record EventEnvelope(
    String eventId,
    String eventType,
    String eventVersion,
    Instant occurredAt,
    String producer,
    Map<String, Object> data,
    Map<String, String> tracingContext
) {
}
