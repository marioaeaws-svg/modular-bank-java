package com.modularbank.accounts.infrastructure.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DebitAccountCommand(UUID transferId, UUID accountId, UUID userId, BigDecimal amount, String reference) {
}
