package com.modularbank.accounts.infrastructure.messaging;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent-consumer ledger (resilience pattern, see ADR-010 /
 * docs/evidencia/paso-3/03-patrones-resiliencia.md): one row per
 * (eventId, consumer) pair ever successfully processed. A redelivered
 * message (broker retry, at-least-once delivery) is detected here and
 * skipped instead of re-applying its effect (critical here — crediting the
 * same command twice would double the money moved).
 */
@Entity
@Table(name = "processed_messages", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "consumer"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String consumer;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
