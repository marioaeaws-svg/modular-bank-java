package com.modularbank.shared.infrastructure.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCompletedEvent(
    UUID transferId,
    UUID userId,
    UUID sourceAccountId,
    UUID targetAccountId,
    BigDecimal amount,
    String reference
) {
}
