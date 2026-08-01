package com.modularbank.transfers.adapter.out.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command published to accounts-service (MS1) on {@code accounts.commands} /
 * {@code account.credit}. {@code purpose} is either {@code FORWARD} (crediting
 * the target account as the second saga step) or {@code COMPENSATION}
 * (refunding the source account because the forward credit failed) — MS1
 * doesn't interpret it, it just echoes it back on the resulting event so the
 * orchestrator knows which branch of the saga the result belongs to.
 */
public record CreditAccountCommand(
    UUID transferId,
    UUID accountId,
    UUID userId,
    BigDecimal amount,
    String reference,
    String purpose
) {
}
