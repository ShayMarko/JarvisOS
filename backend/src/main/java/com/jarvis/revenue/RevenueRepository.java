package com.jarvis.revenue;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RevenueRepository extends JpaRepository<RevenueEntry, String> {

    List<RevenueEntry> findByOccurredAtAfter(Instant since);
}
