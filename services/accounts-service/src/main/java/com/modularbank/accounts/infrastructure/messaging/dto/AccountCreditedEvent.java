package com.modularbank.accounts.infrastructure.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountCreditedEvent(UUID transferId, UUID accountId, BigDecimal amount, String purpose) {
}
