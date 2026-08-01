package com.modularbank.shared.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modularbank.modules.notifications.application.NotificationsService;
import com.modularbank.modules.notifications.domain.NotificationType;
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

/**
 * The listener is the replacement for TransferUseCase's in-process
 * notificationsService.send(...) call (removed along with the transfers
 * module in Paso 2) — this verifies it still notifies the right user with
 * the right type for both a completed and a failed transfer, and that a
 * redelivered message (same eventId) is skipped instead of double-notifying
 * (idempotent-consumer resilience pattern, see ADR-010 / Paso 3 evidence).
 */
class NotificationsTransferEventsListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private EventEnvelope envelope(String eventType, Object data) {
        Map<String, Object> asMap = objectMapper.convertValue(data, Map.class);
        return new EventEnvelope(UUID.randomUUID().toString(), eventType, "1.0", Instant.now(), "transfers-service", asMap, Map.of());
    }

    @Test
    void onCompletedSendsTransferSentNotification() {
        NotificationsService notificationsService = mock(NotificationsService.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        NotificationsTransferEventsListener listener = new NotificationsTransferEventsListener(notificationsService, objectMapper, processedMessageRepository, Tracer.NOOP, Propagator.NOOP);
        UUID userId = UUID.randomUUID();

        listener.handle(envelope("TransferCompletedEvent", new TransferCompletedEvent(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("250.00"), "ref")));

        verify(notificationsService).send(eq(userId), eq(NotificationType.TRANSFER_SENT), any());
        verify(processedMessageRepository).save(any());
    }

    @Test
    void onFailedSendsTransferFailedNotification() {
        NotificationsService notificationsService = mock(NotificationsService.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        NotificationsTransferEventsListener listener = new NotificationsTransferEventsListener(notificationsService, objectMapper, processedMessageRepository, Tracer.NOOP, Propagator.NOOP);
        UUID userId = UUID.randomUUID();

        listener.handle(envelope("TransferFailedEvent", new TransferFailedEvent(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("250.00"), "ref", "INSUFFICIENT_FUNDS")));

        verify(notificationsService).send(eq(userId), eq(NotificationType.TRANSFER_FAILED), any());
    }

    @Test
    void duplicateEventIdIsSkipped() {
        NotificationsService notificationsService = mock(NotificationsService.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        NotificationsTransferEventsListener listener = new NotificationsTransferEventsListener(notificationsService, objectMapper, processedMessageRepository, Tracer.NOOP, Propagator.NOOP);
        UUID userId = UUID.randomUUID();
        EventEnvelope env = envelope("TransferCompletedEvent", new TransferCompletedEvent(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("250.00"), "ref"));
        when(processedMessageRepository.existsByEventIdAndConsumer(env.eventId(), "monolith.notifications.transfer-events")).thenReturn(true);

        listener.handle(env);

        verifyNoInteractions(notificationsService);
        verify(processedMessageRepository, never()).save(any());
    }
}
