package com.modularbank.accounts.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.modularbank.accounts.infrastructure.messaging.EventTypes.EVENT_VERSION_1_0;
import static com.modularbank.accounts.infrastructure.messaging.EventTypes.PRODUCER;

/**
 * Builds the outbound envelope, including injecting the current W3C trace
 * context (if any) into {@code tracingContext} — see EventEnvelope's javadoc
 * for why this is done manually instead of relying on Spring AMQP's own
 * message observation (Paso 4, see ADR-009).
 */
@Component
@RequiredArgsConstructor
public class EventEnvelopeFactory {

    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final Propagator propagator;

    @SuppressWarnings("unchecked")
    public EventEnvelope wrap(String eventType, Object data) {
        Map<String, Object> asMap = objectMapper.convertValue(data, Map.class);
        Map<String, String> tracingContext = new HashMap<>();
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            propagator.inject(currentSpan.context(), tracingContext, Map::put);
        }
        return new EventEnvelope(UUID.randomUUID().toString(), eventType, EVENT_VERSION_1_0, Instant.now(), PRODUCER, asMap, tracingContext);
    }
}
