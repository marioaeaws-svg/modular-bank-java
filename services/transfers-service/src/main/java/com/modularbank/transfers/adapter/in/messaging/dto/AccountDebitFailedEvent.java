package com.modularbank.transfers.adapter.in.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code reason} is one of {@code ACCOUNT_NOT_FOUND}, {@code NOT_OWNER}, {@code INSUFFICIENT_FUNDS}. */
public record AccountDebitFailedEvent(UUID transferId, UUID accountId, BigDecimal amount, String reason) {
}
