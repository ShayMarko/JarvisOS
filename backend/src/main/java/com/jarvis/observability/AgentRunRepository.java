package com.jarvis.observability;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRunRecord, String> {

    List<AgentRunRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Runs since a moment — used to sum this month's AI cost for the ROI dashboard. */
    List<AgentRunRecord> findByCreatedAtAfter(Instant since);
}
