package com.jarvis.timeline;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimelineRepository extends JpaRepository<TimelineEntry, String> {

    Optional<TimelineEntry> findByDay(String day);
}
