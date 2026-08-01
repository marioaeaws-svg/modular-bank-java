package com.modularbank.shared.infrastructure.messaging;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent-consumer ledger (resilience pattern, see ADR-010 /
 * docs/evidencia/paso-3/03-patrones-resiliencia.md): one row per
 * (eventId, consumer) pair ever successfully processed. Notifications and
 * audit are two independent consumers of the same {@code transfers.events}
 * exchange (pub/sub, not competing consumers), so a duplicate delivery is
 * tracked and skipped separately per consumer, not globally per event.
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
