package com.modularbank.accounts.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AmountOperationRequest(
    @NotNull @Positive BigDecimal amount,
    String reference
) {
}
