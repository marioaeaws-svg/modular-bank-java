package com.modularbank.transfers.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Saga aggregate: one row per transfer, its {@code status} column doubles as
 * the orchestrator's saga state (see TransferSagaOrchestrator). Unlike the
 * Paso 1 monolith's Transfer (always created already COMPLETED, since debit
 * and credit were in-process), this row is created PENDING and only reaches
 * a terminal state once the async debit/credit round-trip with
 * accounts-service (MS1) over RabbitMQ finishes.
 */
@Entity
@Table(name = "transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "target_account_id", nullable = false)
    private UUID targetAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    private String reference;

    @Column(nullable = false)
    @Builder.Default
    private String status = TransferStatus.PENDING.name();

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
