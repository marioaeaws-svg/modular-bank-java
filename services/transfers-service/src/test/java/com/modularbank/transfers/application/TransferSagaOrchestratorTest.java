package com.modularbank.transfers.application;

import com.modularbank.transfers.adapter.in.messaging.dto.AccountCreditFailedEvent;
import com.modularbank.transfers.adapter.in.messaging.dto.AccountCreditedEvent;
import com.modularbank.transfers.adapter.in.messaging.dto.AccountDebitFailedEvent;
import com.modularbank.transfers.adapter.in.messaging.dto.AccountDebitedEvent;
import com.modularbank.transfers.adapter.out.messaging.dto.CreditAccountCommand;
import com.modularbank.transfers.adapter.out.messaging.dto.DebitAccountCommand;
import com.modularbank.transfers.adapter.out.messaging.dto.TransferCompletedEvent;
import com.modularbank.transfers.adapter.out.messaging.dto.TransferFailedEvent;
import com.modularbank.transfers.adapter.out.persistence.TransferRepository;
import com.modularbank.transfers.application.dto.TransferRequest;
import com.modularbank.transfers.application.port.out.AccountCommandPublisher;
import com.modularbank.transfers.application.port.out.TransferEventPublisher;
import com.modularbank.transfers.domain.Transfer;
import com.modularbank.transfers.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Exercises the saga state machine directly (no Spring context, no broker) —
 * this is where the happy path, the up-front failure path, and the
 * compensation path (including the "compensation itself fails" edge case)
 * are actually verified. The docker-compose evidence in
 * docs/evidencia/paso-2/ shows the same flows end-to-end over a real broker.
 */
class TransferSagaOrchestratorTest {

    private TransferRepository transferRepository;
    private AccountCommandPublisher commandPublisher;
    private TransferEventPublisher eventPublisher;
    private TransferSagaOrchestrator orchestrator;

    private final UUID userId = UUID.randomUUID();
    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID targetAccountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transferRepository = mock(TransferRepository.class);
        commandPublisher = mock(AccountCommandPublisher.class);
        eventPublisher = mock(TransferEventPublisher.class);
        orchestrator = new TransferSagaOrchestrator(transferRepository, commandPublisher, eventPublisher);

        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Transfer pendingTransfer(UUID id) {
        return Transfer.builder()
            .id(id).userId(userId).sourceAccountId(sourceAccountId).targetAccountId(targetAccountId)
            .amount(new BigDecimal("100.00")).reference("ref").status(TransferStatus.PENDING.name())
            .build();
    }

    @Test
    void initiateRejectsSelfTransfer() {
        TransferRequest request = new TransferRequest(sourceAccountId, sourceAccountId, new BigDecimal("10.00"), null);
        assertThatThrownBy(() -> orchestrator.initiate(userId, request))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(commandPublisher);
    }

    @Test
    void initiateCreatesPendingTransferAndPublishesDebitCommand() {
        TransferRequest request = new TransferRequest(sourceAccountId, targetAccountId, new BigDecimal("100.00"), "ref");

        Transfer transfer = orchestrator.initiate(userId, request);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING.name());
        ArgumentCaptor<DebitAccountCommand> captor = ArgumentCaptor.forClass(DebitAccountCommand.class);
        verify(commandPublisher).publishDebit(captor.capture());
        assertThat(captor.getValue().accountId()).isEqualTo(sourceAccountId);
        assertThat(captor.getValue().userId()).isEqualTo(userId);
    }

    @Test
    void happyPathDebitedThenCreditedCompletesAndPublishesTransferCompleted() {
        UUID transferId = UUID.randomUUID();
        Transfer pending = pendingTransfer(transferId);
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(pending));

        orchestrator.onAccountDebited(new AccountDebitedEvent(transferId, sourceAccountId, pending.getAmount()));
        assertThat(pending.getStatus()).isEqualTo(TransferStatus.DEBITED.name());
        verify(commandPublisher).publishCredit(any(CreditAccountCommand.class));

        orchestrator.onAccountCredited(new AccountCreditedEvent(transferId, targetAccountId, pending.getAmount(), "FORWARD"));
        assertThat(pending.getStatus()).isEqualTo(TransferStatus.COMPLETED.name());
        verify(eventPublisher).publishCompleted(any(TransferCompletedEvent.class));
    }

    @Test
    void debitFailedMarksTransferFailedWithoutTouchingTargetAccount() {
        UUID transferId = UUID.randomUUID();
        Transfer pending = pendingTransfer(transferId);
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(pending));

        orchestrator.onAccountDebitFailed(new AccountDebitFailedEvent(transferId, sourceAccountId, pending.getAmount(), "INSUFFICIENT_FUNDS"));

        assertThat(pending.getStatus()).isEqualTo(TransferStatus.FAILED.name());
        assertThat(pending.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        verifyNoInteractions(commandPublisher);
        verify(eventPublisher).publishFailed(any(TransferFailedEvent.class));
    }

    @Test
    void creditFailedAfterDebitTriggersCompensatingRefund() {
        UUID transferId = UUID.randomUUID();
        Transfer debited = pendingTransfer(transferId);
        debited.setStatus(TransferStatus.DEBITED.name());
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(debited));

        orchestrator.onAccountCreditFailed(new AccountCreditFailedEvent(transferId, targetAccountId, debited.getAmount(), "ACCOUNT_NOT_FOUND", "FORWARD"));

        assertThat(debited.getStatus()).isEqualTo(TransferStatus.COMPENSATING.name());
        ArgumentCaptor<CreditAccountCommand> captor = ArgumentCaptor.forClass(CreditAccountCommand.class);
        verify(commandPublisher).publishCredit(captor.capture());
        assertThat(captor.getValue().accountId()).isEqualTo(sourceAccountId);
        assertThat(captor.getValue().purpose()).isEqualTo("COMPENSATION");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void compensationSucceedingReversesTheTransfer() {
        UUID transferId = UUID.randomUUID();
        Transfer compensating = pendingTransfer(transferId);
        compensating.setStatus(TransferStatus.COMPENSATING.name());
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(compensating));

        orchestrator.onAccountCredited(new AccountCreditedEvent(transferId, sourceAccountId, compensating.getAmount(), "COMPENSATION"));

        assertThat(compensating.getStatus()).isEqualTo(TransferStatus.REVERSED.name());
        verify(eventPublisher).publishFailed(any(TransferFailedEvent.class));
    }

    @Test
    void compensationFailingLeavesTransferFailedForManualReconciliation() {
        UUID transferId = UUID.randomUUID();
        Transfer compensating = pendingTransfer(transferId);
        compensating.setStatus(TransferStatus.COMPENSATING.name());
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(compensating));

        orchestrator.onAccountCreditFailed(new AccountCreditFailedEvent(transferId, sourceAccountId, compensating.getAmount(), "ACCOUNT_NOT_FOUND", "COMPENSATION"));

        assertThat(compensating.getStatus()).isEqualTo(TransferStatus.FAILED.name());
        assertThat(compensating.getFailureReason()).contains("COMPENSATION_FAILED").contains("REQUIRES_MANUAL_RECONCILIATION");
        verify(eventPublisher).publishFailed(any(TransferFailedEvent.class));
    }

    @Test
    void staleDebitedEventForAlreadyCompletedTransferIsIgnored() {
        UUID transferId = UUID.randomUUID();
        Transfer completed = pendingTransfer(transferId);
        completed.setStatus(TransferStatus.COMPLETED.name());
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(completed));

        orchestrator.onAccountDebited(new AccountDebitedEvent(transferId, sourceAccountId, completed.getAmount()));

        verifyNoInteractions(commandPublisher);
        assertThat(completed.getStatus()).isEqualTo(TransferStatus.COMPLETED.name());
    }
}
