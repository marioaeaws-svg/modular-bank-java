package com.modularbank.accounts.infrastructure.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditAccountCommand(UUID transferId, UUID accountId, UUID userId, BigDecimal amount, String reference, String purpose) {
}
