package com.modularbank.shared.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modularbank.modules.audit.application.AuditService;
import com.modularbank.shared.infrastructure.messaging.dto.TransferCompletedEvent;
import com.modularbank.shared.infrastructure.messaging.dto.TransferFailedEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

import static com.modularbank.shared.infrastructure.messaging.EventTypes.TRANSFER_COMPLETED_EVENT;
import static com.modularbank.shared.infrastructure.messaging.EventTypes.TRANSFER_FAILED_EVENT;
import static com.modularbank.shared.infrastructure.messaging.RabbitTopology.AUDIT_QUEUE;

/**
 * Replaces the direct {@code auditService.record(...)} call
 * {@code TransferUseCase} used to make in-process before transfers was
 * extracted to its own microservice (Paso 2).
 *
 * Dispatches on {@code envelope.eventType()} (see ADR-010) and is guarded by
 * an idempotency check keyed on this consumer's own name (see
 * docs/evidencia/paso-3/03-patrones-resiliencia.md).
 */
@Component
@RequiredArgsConstructor
public class AuditTransferEventsListener {

    private static final String CONSUMER = AUDIT_QUEUE;
    private static final Logger log = LoggerFactory.getLogger(AuditTransferEventsListener.class);

    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;
    private final Tracer tracer;
    private final Propagator propagator;

    @RabbitListener(queues = AUDIT_QUEUE)
    @Transactional
    public void handle(EventEnvelope envelope) {
        // Continue the distributed trace across the broker hop (Paso 4, see ADR-009).
        Map<String, String> tracingContext = envelope.tracingContext() != null ? envelope.tracingContext() : Map.of();
        Span span = propagator.extract(tracingContext, Map::get)
            .name("rabbit.consume " + CONSUMER)
            .kind(Span.Kind.CONSUMER)
            .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            // Business context for structured logs (Paso 4, see ADR-009).
            MDC.put("eventId", envelope.eventId());
            MDC.put("eventType", envelope.eventType());
            Object transferId = envelope.data().get("transferId");
            if (transferId != null) {
                MDC.put("transferId", transferId.toString());
            }
            try {
                if (processedMessageRepository.existsByEventIdAndConsumer(envelope.eventId(), CONSUMER)) {
                    log.info("Duplicate delivery of {} (eventId={}) for consumer {} — skipping, already processed",
                        envelope.eventType(), envelope.eventId(), CONSUMER);
                    return;
                }

                switch (envelope.eventType()) {
                    case TRANSFER_COMPLETED_EVENT -> onCompleted(convert(envelope, TransferCompletedEvent.class));
                    case TRANSFER_FAILED_EVENT -> onFailed(convert(envelope, TransferFailedEvent.class));
                    default -> log.warn("Unknown eventType {} on {} — ignoring", envelope.eventType(), CONSUMER);
                }

                processedMessageRepository.save(ProcessedMessage.builder()
                    .eventId(envelope.eventId())
                    .consumer(CONSUMER)
                    .build());
            } finally {
                MDC.remove("eventId");
                MDC.remove("eventType");
                MDC.remove("transferId");
            }
        } finally {
            span.end();
        }
    }

    private void onCompleted(TransferCompletedEvent event) {
        auditService.record(event.userId(), "TRANSFER_EXECUTED", Map.of(
            "transferId", event.transferId().toString(),
            "amount", event.amount().toPlainString()
        ));
        log.info("Recorded TRANSFER_EXECUTED audit entry for user {} transfer {}", event.userId(), event.transferId());
    }

    private void onFailed(TransferFailedEvent event) {
        auditService.record(event.userId(), "TRANSFER_FAILED", Map.of(
            "transferId", event.transferId().toString(),
            "amount", event.amount().toPlainString(),
            "reason", event.reason() != null ? event.reason() : "UNKNOWN"
        ));
    }

    private <T> T convert(EventEnvelope envelope, Class<T> type) {
        return objectMapper.convertValue(envelope.data(), type);
    }
}
