package com.modularbank.accounts.infrastructure.messaging;

import java.time.Instant;
import java.util.Map;

/**
 * Wire-level envelope for every AMQP message this service sends or receives —
 * see ADR-010. {@code eventId} is the deduplication key for idempotent
 * consumers (see AccountCommandsListener); {@code eventType} carries the
 * business type as a string so the RabbitMQ type mapper only ever needs to
 * know about this one wrapper class, not one per DTO.
 *
 * {@code tracingContext} carries the W3C trace context (injected via
 * Micrometer's {@code Propagator}, see EventEnvelopeFactory) so a distributed
 * trace continues across the broker hop (Paso 4, see ADR-009) — it lives in
 * this JSON field rather than raw AMQP headers because Spring AMQP's own
 * automatic observation (as shipped in this Spring Boot version) creates a
 * span per hop but does not continue the parent's trace, so propagation is
 * done explicitly here instead of relying on it. May be null/absent for
 * messages published outside a traced operation (e.g. manual ops/seed
 * publishes) — consumers must treat that as "no parent, start a new trace".
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
