package com.modularbank.accounts.infrastructure.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountCreditFailedEvent(UUID transferId, UUID accountId, BigDecimal amount, String reason, String purpose) {
}
