package com.modularbank.shared.infrastructure.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

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
