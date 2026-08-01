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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

import static com.modularbank.transfers.adapter.out.messaging.RabbitTopology.CREDIT_PURPOSE_COMPENSATION;
import static com.modularbank.transfers.adapter.out.messaging.RabbitTopology.CREDIT_PURPOSE_FORWARD;

/**
 * Saga orchestrator (orchestration, not choreography — see ADR-007): this
 * class is the single place that knows the full debit -> credit workflow and
 * decides what happens next after each async result. accounts-service (MS1)
 * never talks to the monolith remnant directly and has no notion of a
 * "transfer" — it only reacts to debit/credit commands one at a time.
 *
 * Every handler re-checks the transfer's current status before acting
 * ("PENDING" for debit results, "DEBITED"/"COMPENSATING" for credit results).
 * That guard is what makes redelivery of a message (retry, redelivery after a
 * crash) safe to ignore instead of double-applying a state transition —
 * a first step toward the resilience patterns formalized in Paso 3.
 */
@Service
@RequiredArgsConstructor
public class TransferSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TransferSagaOrchestrator.class);

    private final TransferRepository transferRepository;
    private final AccountCommandPublisher commandPublisher;
    private final TransferEventPublisher eventPublisher;

    @Transactional
    public Transfer initiate(UUID userId, TransferRequest request) {
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Source and target accounts must be different");
        }

        Transfer transfer = Transfer.builder()
            .userId(userId)
            .sourceAccountId(request.sourceAccountId())
            .targetAccountId(request.targetAccountId())
            .amount(request.amount())
            .reference(request.reference())
            .status(TransferStatus.PENDING.name())
            .build();
        transfer = transferRepository.save(transfer);
        log.info("Transfer {} initiated PENDING: {} -> {} amount {}", transfer.getId(),
            transfer.getSourceAccountId(), transfer.getTargetAccountId(), transfer.getAmount());

        commandPublisher.publishDebit(new DebitAccountCommand(
            transfer.getId(), transfer.getSourceAccountId(), userId, transfer.getAmount(), transfer.getReference()));

        return transfer;
    }

    @Transactional
    public void onAccountDebited(AccountDebitedEvent event) {
        Transfer transfer = transferRepository.findById(event.transferId()).orElse(null);
        if (transfer == null || transfer.getStatus().equals(TransferStatus.DEBITED.name())
            || !transfer.getStatus().equals(TransferStatus.PENDING.name())) {
            log.warn("Ignoring AccountDebitedEvent for transfer {} in status {}", event.transferId(),
                transfer == null ? "UNKNOWN" : transfer.getStatus());
            return;
        }

        transfer.setStatus(TransferStatus.DEBITED.name());
        transferRepository.save(transfer);

        commandPublisher.publishCredit(new CreditAccountCommand(
            transfer.getId(), transfer.getTargetAccountId(), transfer.getUserId(), transfer.getAmount(),
            transfer.getReference(), CREDIT_PURPOSE_FORWARD));
    }

    @Transactional
    public void onAccountDebitFailed(AccountDebitFailedEvent event) {
        Transfer transfer = transferRepository.findById(event.transferId()).orElse(null);
        if (transfer == null || !transfer.getStatus().equals(TransferStatus.PENDING.name())) {
            return;
        }

        transfer.setStatus(TransferStatus.FAILED.name());
        transfer.setFailureReason(event.reason());
        transferRepository.save(transfer);

        eventPublisher.publishFailed(toFailedEvent(transfer));
    }

    @Transactional
    public void onAccountCredited(AccountCreditedEvent event) {
        Transfer transfer = transferRepository.findById(event.transferId()).orElse(null);
        if (transfer == null) {
            return;
        }

        if (CREDIT_PURPOSE_FORWARD.equals(event.purpose()) && transfer.getStatus().equals(TransferStatus.DEBITED.name())) {
            transfer.setStatus(TransferStatus.COMPLETED.name());
            transferRepository.save(transfer);
            log.info("Transfer {} COMPLETED", transfer.getId());
            eventPublisher.publishCompleted(toCompletedEvent(transfer));
        } else if (CREDIT_PURPOSE_COMPENSATION.equals(event.purpose())
            && transfer.getStatus().equals(TransferStatus.COMPENSATING.name())) {
            transfer.setStatus(TransferStatus.REVERSED.name());
            transferRepository.save(transfer);
            eventPublisher.publishFailed(toFailedEvent(transfer));
        }
    }

    @Transactional
    public void onAccountCreditFailed(AccountCreditFailedEvent event) {
        Transfer transfer = transferRepository.findById(event.transferId()).orElse(null);
        if (transfer == null) {
            return;
        }

        if (CREDIT_PURPOSE_FORWARD.equals(event.purpose()) && transfer.getStatus().equals(TransferStatus.DEBITED.name())) {
            // Compensating transaction: the target credit failed after the source was
            // already debited, so refund the source account before failing the transfer.
            transfer.setStatus(TransferStatus.COMPENSATING.name());
            transfer.setFailureReason("TARGET_CREDIT_FAILED:" + event.reason());
            transferRepository.save(transfer);

            commandPublisher.publishCredit(new CreditAccountCommand(
                transfer.getId(), transfer.getSourceAccountId(), transfer.getUserId(), transfer.getAmount(),
                transfer.getReference(), CREDIT_PURPOSE_COMPENSATION));
        } else if (CREDIT_PURPOSE_COMPENSATION.equals(event.purpose())
            && transfer.getStatus().equals(TransferStatus.COMPENSATING.name())) {
            // The refund itself failed. The saga cannot resolve this automatically:
            // money has left the source account and landed nowhere. This is flagged
            // FAILED with an explicit marker for manual/ops reconciliation (see ADR-007).
            transfer.setStatus(TransferStatus.FAILED.name());
            transfer.setFailureReason("COMPENSATION_FAILED:" + event.reason() + ":REQUIRES_MANUAL_RECONCILIATION");
            transferRepository.save(transfer);
            log.error("Saga compensation failed for transfer {} — manual reconciliation required", transfer.getId());
            eventPublisher.publishFailed(toFailedEvent(transfer));
        }
    }

    @Transactional(readOnly = true)
    public List<Transfer> getHistory(UUID userId, UUID accountId) {
        return transferRepository.findHistory(userId, accountId);
    }

    @Transactional(readOnly = true)
    public Transfer getById(UUID userId, UUID transferId) {
        Transfer transfer = transferRepository.findById(transferId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found"));
        if (!transfer.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return transfer;
    }

    private TransferCompletedEvent toCompletedEvent(Transfer transfer) {
        return new TransferCompletedEvent(transfer.getId(), transfer.getUserId(), transfer.getSourceAccountId(),
            transfer.getTargetAccountId(), transfer.getAmount(), transfer.getReference());
    }

    private TransferFailedEvent toFailedEvent(Transfer transfer) {
        return new TransferFailedEvent(transfer.getId(), transfer.getUserId(), transfer.getSourceAccountId(),
            transfer.getTargetAccountId(), transfer.getAmount(), transfer.getReference(), transfer.getFailureReason());
    }
}
