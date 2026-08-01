package com.modularbank.transfers.adapter.in.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountDebitedEvent(UUID transferId, UUID accountId, BigDecimal amount) {
}
