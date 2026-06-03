package com.modularbank.modules.audit.infrastructure;

import com.modularbank.modules.audit.application.AuditService;
import com.modularbank.modules.audit.domain.AuditEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditRepository auditRepository;

    @Override
    @Transactional
    public void record(UUID userId, String action, Map<String, String> metadata) {
        AuditEntry entry = AuditEntry.builder()
            .userId(userId)
            .action(action)
            .metadata(metadata)
            .build();
        auditRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> getForUser(UUID userId) {
        return auditRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
