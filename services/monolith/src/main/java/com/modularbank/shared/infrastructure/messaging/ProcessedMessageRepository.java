package com.modularbank.shared.infrastructure.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {
    boolean existsByEventIdAndConsumer(String eventId, String consumer);
}
