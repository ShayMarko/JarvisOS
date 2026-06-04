package com.jarvis.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    List<AuditLogEntry> findAllByOrderByTimestampDesc(Pageable pageable);

    /** All entries within a day window, oldest first — for the episodic timeline roll-up. */
    List<AuditLogEntry> findByTimestampBetweenOrderByTimestampAsc(Instant start, Instant end);
}
