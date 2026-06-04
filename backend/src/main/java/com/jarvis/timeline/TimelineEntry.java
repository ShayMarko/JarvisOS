package com.jarvis.timeline;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** A per-day episodic roll-up — "what happened on this day" — for the queryable timeline. */
@Entity
@Table(name = "timeline_entry")
@Getter
public class TimelineEntry {

    @Id
    private String id;

    /** The day this rolls up, ISO yyyy-MM-dd (unique). */
    @Column(nullable = false, unique = true)
    private String day;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at")
    private Instant createdAt;

    protected TimelineEntry() {
        // for JPA
    }

    public TimelineEntry(String id, String day, String summary) {
        this.id = id;
        this.day = day;
        this.summary = summary;
        this.createdAt = Instant.now();
    }
}
