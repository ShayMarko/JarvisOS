package com.jarvis.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String inputType, String command, String input, String status, String detail) {
        repository.save(new AuditLogEntry(Instant.now(), inputType, command, input, status, detail));
    }

    public List<AuditLogEntry> recent(int limit) {
        return repository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit));
    }
}
