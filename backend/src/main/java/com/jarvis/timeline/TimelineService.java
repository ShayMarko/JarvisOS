package com.jarvis.timeline;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.jarvis.common.Ids;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jarvis.audit.AuditLogEntry;
import com.jarvis.audit.AuditLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Episodic timeline — a per-day roll-up of what Jarvis did, so the user can ask "what did I do / decide
 * last week". Roll-ups are compiled deterministically from the audit log (no tokens) and persisted once
 * per day; recall returns the recent days (compiling any missing days on demand so it works immediately).
 */
@Service
@RequiredArgsConstructor
public class TimelineService {

    private final TimelineRepository repository;
    private final AuditLogRepository audit;

    /** Recent days, newest first — compiling any not-yet-rolled-up day on the fly. */
    public List<TimelineEntry> recent(int days) {
        int n = Math.max(1, Math.min(days, 60));
        List<TimelineEntry> out = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        for (int i = 0; i < n; i++) {
            out.add(getOrRollUp(today.minusDays(i)));
        }
        return out;
    }

    /** Existing entry for the day, or compile + save one now. */
    @Transactional
    public TimelineEntry getOrRollUp(LocalDate day) {
        return repository.findByDay(day.toString()).orElseGet(() -> rollUp(day));
    }

    /** Compile (or recompile) the day's summary from the audit log and upsert it. */
    @Transactional
    public TimelineEntry rollUp(LocalDate day) {
        String summary = summarize(day);
        TimelineEntry existing = repository.findByDay(day.toString()).orElse(null);
        if (existing != null) {
            existing.setSummary(summary);
            return repository.save(existing);
        }
        return repository.save(new TimelineEntry(
                Ids.generate("tl"), day.toString(), summary));
    }

    private String summarize(LocalDate day) {
        ZoneId zone = ZoneId.systemDefault();
        Instant start = day.atStartOfDay(zone).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
        List<AuditLogEntry> entries = audit.findByTimestampBetweenOrderByTimestampAsc(start, end);
        if (entries.isEmpty()) {
            return "Quiet day — nothing recorded.";
        }
        int ok = 0;
        int errors = 0;
        Map<String, Integer> byAction = new LinkedHashMap<>();
        for (AuditLogEntry e : entries) {
            if ("ERROR".equalsIgnoreCase(e.getStatus()) || "FAILED".equalsIgnoreCase(e.getStatus())) {
                errors++;
            } else {
                ok++;
            }
            String label = e.getCommand() != null && !e.getCommand().isBlank() ? e.getCommand() : e.getInputType();
            byAction.merge(label == null ? "?" : label, 1, Integer::sum);
        }
        String highlights = byAction.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(6)
                .map(en -> en.getKey() + " ×" + en.getValue())
                .reduce((a, b) -> a + ", " + b).orElse("—");
        return entries.size() + " actions (" + ok + " ok, " + errors + " errors). Highlights: " + highlights;
    }

    /** Nightly: roll up the day that just ended (and refresh today's running tally). */
    @Scheduled(cron = "${jarvis.timeline.rollup-cron:0 55 23 * * *}")
    void nightlyRollUp() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        rollUp(today.minusDays(1));
        rollUp(today);
    }
}
