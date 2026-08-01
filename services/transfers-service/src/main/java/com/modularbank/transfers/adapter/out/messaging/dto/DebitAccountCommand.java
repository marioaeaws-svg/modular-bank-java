package com.modularbank.transfers.adapter.out.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command published to accounts-service (MS1) on {@code accounts.commands} /
 * {@code account.debit}. Includes {@code userId} so MS1 can enforce account
 * ownership itself — transfers-service never queries accounts-service
 * synchronously, not even for authorization.
 */
public record DebitAccountCommand(
    UUID transferId,
    UUID accountId,
    UUID userId,
    BigDecimal amount,
    String reference
) {
}
