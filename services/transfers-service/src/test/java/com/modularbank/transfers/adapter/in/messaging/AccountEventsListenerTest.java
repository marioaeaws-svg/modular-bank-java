package com.modularbank.transfers.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modularbank.transfers.adapter.in.messaging.dto.AccountDebitedEvent;
import com.modularbank.transfers.adapter.out.messaging.EventEnvelope;
import com.modularbank.transfers.adapter.out.persistence.ProcessedMessageRepository;
import com.modularbank.transfers.application.TransferSagaOrchestrator;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the inbound adapter dispatches on envelope.eventType() and skips
 * a redelivered eventId instead of re-running the saga transition twice
 * (idempotent-consumer resilience pattern, see ADR-010 / Paso 3 evidence).
 */
class AccountEventsListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private EventEnvelope envelope(String eventType, Object data) {
        Map<String, Object> asMap = objectMapper.convertValue(data, Map.class);
        return new EventEnvelope(UUID.randomUUID().toString(), eventType, "1.0", Instant.now(), "accounts-service", asMap, Map.of());
    }

    @Test
    void dispatchesAccountDebitedEventToOrchestrator() {
        TransferSagaOrchestrator orchestrator = mock(TransferSagaOrchestrator.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        AccountEventsListener listener = new AccountEventsListener(orchestrator, objectMapper, processedMessageRepository,
            Tracer.NOOP, Propagator.NOOP);

        AccountDebitedEvent event = new AccountDebitedEvent(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"));
        listener.handle(envelope("AccountDebitedEvent", event));

        verify(orchestrator).onAccountDebited(event);
        verify(processedMessageRepository).save(any());
    }

    @Test
    void duplicateEventIdIsSkippedWithoutReprocessing() {
        TransferSagaOrchestrator orchestrator = mock(TransferSagaOrchestrator.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        AccountEventsListener listener = new AccountEventsListener(orchestrator, objectMapper, processedMessageRepository,
            Tracer.NOOP, Propagator.NOOP);

        AccountDebitedEvent event = new AccountDebitedEvent(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"));
        EventEnvelope env = envelope("AccountDebitedEvent", event);
        when(processedMessageRepository.existsByEventIdAndConsumer(env.eventId(), "transfers-service.account-events")).thenReturn(true);

        listener.handle(env);

        verifyNoInteractions(orchestrator);
        verify(processedMessageRepository, never()).save(any());
    }
}
