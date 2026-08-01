package com.modularbank.shared.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modularbank.modules.audit.application.AuditService;
import com.modularbank.shared.infrastructure.messaging.dto.TransferCompletedEvent;
import com.modularbank.shared.infrastructure.messaging.dto.TransferFailedEvent;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuditTransferEventsListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private EventEnvelope envelope(String eventType, Object data) {
        Map<String, Object> asMap = objectMapper.convertValue(data, Map.class);
        return new EventEnvelope(UUID.randomUUID().toString(), eventType, "1.0", Instant.now(), "transfers-service", asMap, Map.of());
    }

    @Test
    void onCompletedRecordsTransferExecuted() {
        AuditService auditService = mock(AuditService.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        AuditTransferEventsListener listener = new AuditTransferEventsListener(auditService, objectMapper, processedMessageRepository, Tracer.NOOP, Propagator.NOOP);
        UUID userId = UUID.randomUUID();

        listener.handle(envelope("TransferCompletedEvent", new TransferCompletedEvent(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("250.00"), "ref")));

        verify(auditService).record(eq(userId), eq("TRANSFER_EXECUTED"), any());
        verify(processedMessageRepository).save(any());
    }

    @Test
    void onFailedRecordsTransferFailed() {
        AuditService auditService = mock(AuditService.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        AuditTransferEventsListener listener = new AuditTransferEventsListener(auditService, objectMapper, processedMessageRepository, Tracer.NOOP, Propagator.NOOP);
        UUID userId = UUID.randomUUID();

        listener.handle(envelope("TransferFailedEvent", new TransferFailedEvent(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("250.00"), "ref", "INSUFFICIENT_FUNDS")));

        verify(auditService).record(eq(userId), eq("TRANSFER_FAILED"), any());
    }

    @Test
    void duplicateEventIdIsSkipped() {
        AuditService auditService = mock(AuditService.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        AuditTransferEventsListener listener = new AuditTransferEventsListener(auditService, objectMapper, processedMessageRepository, Tracer.NOOP, Propagator.NOOP);
        UUID userId = UUID.randomUUID();
        EventEnvelope env = envelope("TransferCompletedEvent", new TransferCompletedEvent(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("250.00"), "ref"));
        when(processedMessageRepository.existsByEventIdAndConsumer(env.eventId(), "monolith.audit.transfer-events")).thenReturn(true);

        listener.handle(env);

        verifyNoInteractions(auditService);
        verify(processedMessageRepository, never()).save(any());
    }
}
