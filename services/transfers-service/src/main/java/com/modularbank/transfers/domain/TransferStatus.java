package com.modularbank.transfers.domain;

/**
 * Saga states for a Transfer, in the order the orchestrator drives them
 * through the happy path (PENDING -> DEBITED -> COMPLETED) and the two
 * failure paths:
 *  - debit fails up front: PENDING -> FAILED (nothing to compensate, no
 *    money ever left the source account).
 *  - credit fails after a successful debit: DEBITED -> COMPENSATING ->
 *    REVERSED (money refunded to the source account) — or, in the rare case
 *    the refund itself fails, DEBITED -> COMPENSATING -> FAILED, which is
 *    the one state that requires manual/ops reconciliation (see ADR-007).
 */
public enum TransferStatus {
    PENDING,
    DEBITED,
    COMPLETED,
    COMPENSATING,
    REVERSED,
    FAILED
}
