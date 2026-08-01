package com.modularbank.transfers.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modularbank.transfers.adapter.in.messaging.dto.AccountCreditFailedEvent;
import com.modularbank.transfers.adapter.in.messaging.dto.AccountCreditedEvent;
import com.modularbank.transfers.adapter.in.messaging.dto.AccountDebitFailedEvent;
import com.modularbank.transfers.adapter.in.messaging.dto.AccountDebitedEvent;
import com.modularbank.transfers.adapter.out.messaging.EventEnvelope;
import com.modularbank.transfers.adapter.out.persistence.ProcessedMessage;
import com.modularbank.transfers.adapter.out.persistence.ProcessedMessageRepository;
import com.modularbank.transfers.application.TransferSagaOrchestrator;
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
import java.util.List;
import java.util.Map;

import static com.modularbank.transfers.adapter.out.messaging.EventTypes.*;
import static com.modularbank.transfers.adapter.out.messaging.RabbitTopology.TRANSFERS_SERVICE_ACCOUNT_EVENTS_QUEUE;

/**
 * Inbound adapter for the result events accounts-service (MS1) publishes in
 * response to the commands this service sends it. Dispatches on
 * {@code envelope.eventType()} (see ADR-010) rather than on Java overload
 * resolution — the wire type is always {@code EventEnvelope}.
 *
 * Wrapped with an idempotency check (resilience pattern, see
 * docs/evidencia/paso-3/03-patrones-resiliencia.md): RabbitMQ's at-least-once
 * delivery (amplified by the retry advice chain configured on this queue's
 * listener container factory) means the same message can arrive more than
 * once — {@code envelope.eventId()} is the dedup key.
 */
@Component
@RequiredArgsConstructor
public class AccountEventsListener {

    private static final String CONSUMER = TRANSFERS_SERVICE_ACCOUNT_EVENTS_QUEUE;
    private static final Logger log = LoggerFactory.getLogger(AccountEventsListener.class);

    private final TransferSagaOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;
    private final Tracer tracer;
    private final Propagator propagator;

    @RabbitListener(queues = TRANSFERS_SERVICE_ACCOUNT_EVENTS_QUEUE)
    @Transactional
    public void handle(EventEnvelope envelope) {
        // Continue the distributed trace across the broker hop (Paso 4, see ADR-009 and
        // EventEnvelope's javadoc for why this is manual instead of relying on Spring AMQP's
        // own message observation).
        Map<String, String> tracingContext = envelope.tracingContext() != null ? envelope.tracingContext() : Map.of();
        Span span = propagator.extract(tracingContext, Map::get)
            .name("rabbit.consume " + CONSUMER)
            .kind(Span.Kind.CONSUMER)
            .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            // Business context for structured logs (Paso 4, see ADR-009).
            MDC.put("eventId", envelope.eventId());
            MDC.put("eventType", envelope.eventType());
            for (String key : List.of("accountId", "transferId")) {
                Object value = envelope.data().get(key);
                if (value != null) {
                    MDC.put(key, value.toString());
                }
            }
            try {
                if (processedMessageRepository.existsByEventIdAndConsumer(envelope.eventId(), CONSUMER)) {
                    log.info("Duplicate delivery of {} (eventId={}) for consumer {} — skipping, already processed",
                        envelope.eventType(), envelope.eventId(), CONSUMER);
                    return;
                }

                switch (envelope.eventType()) {
                    case ACCOUNT_DEBITED_EVENT -> orchestrator.onAccountDebited(convert(envelope, AccountDebitedEvent.class));
                    case ACCOUNT_DEBIT_FAILED_EVENT -> orchestrator.onAccountDebitFailed(convert(envelope, AccountDebitFailedEvent.class));
                    case ACCOUNT_CREDITED_EVENT -> orchestrator.onAccountCredited(convert(envelope, AccountCreditedEvent.class));
                    case ACCOUNT_CREDIT_FAILED_EVENT -> orchestrator.onAccountCreditFailed(convert(envelope, AccountCreditFailedEvent.class));
                    default -> log.warn("Unknown eventType {} on {} — ignoring", envelope.eventType(), CONSUMER);
                }

                processedMessageRepository.save(ProcessedMessage.builder()
                    .eventId(envelope.eventId())
                    .consumer(CONSUMER)
                    .build());
            } finally {
                MDC.remove("eventId");
                MDC.remove("eventType");
                MDC.remove("accountId");
                MDC.remove("transferId");
            }
        } finally {
            span.end();
        }
    }

    private <T> T convert(EventEnvelope envelope, Class<T> type) {
        return objectMapper.convertValue(envelope.data(), type);
    }
}
