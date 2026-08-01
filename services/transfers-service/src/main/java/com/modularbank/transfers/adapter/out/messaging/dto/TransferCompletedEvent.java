package com.modularbank.transfers.adapter.out.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published on {@code transfers.events} / {@code transfer.completed} once the
 * saga reaches COMPLETED. Consumed by the monolith remnant's notifications
 * and audit modules — replacing the in-process calls TransferUseCase used to
 * make before this extraction.
 */
public record TransferCompletedEvent(
    UUID transferId,
    UUID userId,
    UUID sourceAccountId,
    UUID targetAccountId,
    BigDecimal amount,
    String reference
) {
}
