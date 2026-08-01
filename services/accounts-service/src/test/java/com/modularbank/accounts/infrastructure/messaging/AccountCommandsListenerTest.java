package com.modularbank.accounts.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modularbank.accounts.domain.Account;
import com.modularbank.accounts.infrastructure.AccountRepository;
import com.modularbank.accounts.infrastructure.messaging.dto.*;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-level: verifies the async command handler enforces the same
 * invariants the Paso 1 synchronous internal endpoints used to (ownership,
 * existence, sufficient funds), emits the matching result event, and skips
 * a duplicate delivery of the same eventId (idempotent-consumer resilience
 * pattern, see ADR-010 / docs/evidencia/paso-3/03-patrones-resiliencia.md)
 * — without needing a running broker.
 */
class AccountCommandsListenerTest {

    private AccountRepository accountRepository;
    private AccountEventPublisher eventPublisher;
    private ProcessedMessageRepository processedMessageRepository;
    private AccountCommandsListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID transferId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        eventPublisher = mock(AccountEventPublisher.class);
        processedMessageRepository = mock(ProcessedMessageRepository.class);
        listener = new AccountCommandsListener(accountRepository, eventPublisher, objectMapper, processedMessageRepository,
            Tracer.NOOP, Propagator.NOOP);
    }

    private Account account(UUID owner) {
        return Account.builder().id(accountId).userId(owner).accountNumber("ACC123").balance(new BigDecimal("500.0000")).build();
    }

    private EventEnvelope envelope(String eventType, Object data) {
        Map<String, Object> asMap = objectMapper.convertValue(data, Map.class);
        return new EventEnvelope(UUID.randomUUID().toString(), eventType, "1.0", Instant.now(), "transfers-service", asMap, Map.of());
    }

    @Test
    void debitAccountNotFoundPublishesDebitFailed() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
        EventEnvelope env = envelope("DebitAccountCommand", new DebitAccountCommand(transferId, accountId, ownerId, new BigDecimal("10.00"), "ref"));

        listener.handle(env);

        verify(eventPublisher).publishDebitFailed(new AccountDebitFailedEvent(transferId, accountId, new BigDecimal("10.00"), "ACCOUNT_NOT_FOUND"));
        verify(accountRepository, never()).debitIfSufficient(any(), any());
        verify(processedMessageRepository).save(argThat(m -> m.getEventId().equals(env.eventId()) && m.getConsumer().equals("accounts-service.commands")));
    }

    @Test
    void debitByNonOwnerPublishesNotOwner() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(ownerId)));
        UUID someoneElse = UUID.randomUUID();
        EventEnvelope env = envelope("DebitAccountCommand", new DebitAccountCommand(transferId, accountId, someoneElse, new BigDecimal("10.00"), "ref"));

        listener.handle(env);

        verify(eventPublisher).publishDebitFailed(new AccountDebitFailedEvent(transferId, accountId, new BigDecimal("10.00"), "NOT_OWNER"));
        verify(accountRepository, never()).debitIfSufficient(any(), any());
    }

    @Test
    void debitInsufficientFundsPublishesInsufficientFunds() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(ownerId)));
        when(accountRepository.debitIfSufficient(eq(accountId), any())).thenReturn(0);
        EventEnvelope env = envelope("DebitAccountCommand", new DebitAccountCommand(transferId, accountId, ownerId, new BigDecimal("1000.00"), "ref"));

        listener.handle(env);

        verify(eventPublisher).publishDebitFailed(new AccountDebitFailedEvent(transferId, accountId, new BigDecimal("1000.00"), "INSUFFICIENT_FUNDS"));
    }

    @Test
    void debitSufficientFundsPublishesDebited() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(ownerId)));
        when(accountRepository.debitIfSufficient(eq(accountId), any())).thenReturn(1);
        EventEnvelope env = envelope("DebitAccountCommand", new DebitAccountCommand(transferId, accountId, ownerId, new BigDecimal("100.00"), "ref"));

        listener.handle(env);

        verify(eventPublisher).publishDebited(new AccountDebitedEvent(transferId, accountId, new BigDecimal("100.00")));
    }

    @Test
    void creditUnknownAccountPublishesCreditFailedEchoingPurpose() {
        when(accountRepository.existsById(accountId)).thenReturn(false);
        EventEnvelope env = envelope("CreditAccountCommand", new CreditAccountCommand(transferId, accountId, ownerId, new BigDecimal("50.00"), "ref", "COMPENSATION"));

        listener.handle(env);

        verify(eventPublisher).publishCreditFailed(new AccountCreditFailedEvent(transferId, accountId, new BigDecimal("50.00"), "ACCOUNT_NOT_FOUND", "COMPENSATION"));
        verify(accountRepository, never()).credit(any(), any());
    }

    @Test
    void creditKnownAccountPublishesCreditedEchoingPurpose() {
        when(accountRepository.existsById(accountId)).thenReturn(true);
        EventEnvelope env = envelope("CreditAccountCommand", new CreditAccountCommand(transferId, accountId, ownerId, new BigDecimal("50.00"), "ref", "FORWARD"));

        listener.handle(env);

        verify(accountRepository).credit(accountId, new BigDecimal("50.00"));
        verify(eventPublisher).publishCredited(new AccountCreditedEvent(transferId, accountId, new BigDecimal("50.00"), "FORWARD"));
    }

    @Test
    void duplicateEventIdIsSkippedWithoutReapplyingEffect() {
        when(accountRepository.existsById(accountId)).thenReturn(true);
        EventEnvelope env = envelope("CreditAccountCommand", new CreditAccountCommand(transferId, accountId, ownerId, new BigDecimal("50.00"), "ref", "FORWARD"));
        when(processedMessageRepository.existsByEventIdAndConsumer(env.eventId(), "accounts-service.commands")).thenReturn(true);

        listener.handle(env);

        verify(accountRepository, never()).credit(any(), any());
        verifyNoInteractions(eventPublisher);
        verify(processedMessageRepository, never()).save(any());
    }
}
