package com.modularbank.transfers.adapter.in.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code purpose} echoes the {@code CreditAccountCommand} that triggered it: {@code FORWARD} or {@code COMPENSATION}. */
public record AccountCreditedEvent(UUID transferId, UUID accountId, BigDecimal amount, String purpose) {
}
