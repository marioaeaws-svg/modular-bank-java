package com.modularbank.transfers.adapter.in.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code reason} is currently only {@code ACCOUNT_NOT_FOUND} (credit itself has no
 * balance invariant to violate). {@code purpose} echoes the triggering command:
 * {@code FORWARD} means the forward credit failed and must be compensated;
 * {@code COMPENSATION} means the refund itself failed — the one case the saga
 * cannot resolve automatically (see ADR-007).
 */
public record AccountCreditFailedEvent(UUID transferId, UUID accountId, BigDecimal amount, String reason, String purpose) {
}
