package com.modularbank.transfers.adapter.out.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published on {@code transfers.events} / {@code transfer.failed} whenever
 * the saga ends in FAILED or REVERSED. {@code reason} carries enough detail
 * (e.g. {@code INSUFFICIENT_FUNDS}, {@code NOT_OWNER}, or a
 * {@code COMPENSATION_FAILED:...} marker requiring manual reconciliation) for
 * the audit trail to be meaningful without re-deriving it from logs.
 */
public record TransferFailedEvent(
    UUID transferId,
    UUID userId,
    UUID sourceAccountId,
    UUID targetAccountId,
    BigDecimal amount,
    String reference,
    String reason
) {
}
