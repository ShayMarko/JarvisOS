package com.jarvis.observability;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRunRecord, String> {

    List<AgentRunRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
