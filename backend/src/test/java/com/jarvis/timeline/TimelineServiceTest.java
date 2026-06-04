package com.jarvis.timeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jarvis.audit.AuditLogEntry;
import com.jarvis.audit.AuditLogRepository;

class TimelineServiceTest {

    private AuditLogRepository audit;
    private TimelineRepository timelineRepo;
    private TimelineService service;

    @BeforeEach
    void setUp() {
        audit = mock(AuditLogRepository.class);
        timelineRepo = mock(TimelineRepository.class);
        when(timelineRepo.findByDay(anyString())).thenReturn(Optional.empty());
        when(timelineRepo.save(any(TimelineEntry.class))).thenAnswer(i -> i.getArgument(0));
        service = new TimelineService(timelineRepo, audit);
    }

    @Test
    void rollsUpADayFromTheAuditLog() {
        when(audit.findByTimestampBetweenOrderByTimestampAsc(any(), any())).thenReturn(List.of(
                new AuditLogEntry(Instant.now(), "command", "/today", "today", "OK", null),
                new AuditLogEntry(Instant.now(), "command", "/today", "today", "OK", null),
                new AuditLogEntry(Instant.now(), "brain", "code", "build app", "ERROR", "boom")));
        TimelineEntry e = service.rollUp(java.time.LocalDate.of(2026, 6, 3));
        assertThat(e.getDay()).isEqualTo("2026-06-03");
        assertThat(e.getSummary()).contains("3 actions").contains("2 ok").contains("1 errors").contains("/today ×2");
    }

    @Test
    void quietDayWhenNothingLogged() {
        when(audit.findByTimestampBetweenOrderByTimestampAsc(any(), any())).thenReturn(List.of());
        assertThat(service.rollUp(java.time.LocalDate.of(2026, 6, 1)).getSummary()).contains("Quiet day");
    }

    @Test
    void recentReturnsRequestedNumberOfDays() {
        when(audit.findByTimestampBetweenOrderByTimestampAsc(any(), any())).thenReturn(List.of());
        assertThat(service.recent(5)).hasSize(5);
    }
}
