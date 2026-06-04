package com.jarvis.revenue;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;

/** One RevenueOS ledger entry — income, savings, hours, an asset, or an experiment — for the ROI dashboard. */
@Entity
@Table(name = "revenue_entry")
@Getter
public class RevenueEntry {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RevenueKind kind;

    /** USD for REVENUE/SAVED, hours for HOURS, count (usually 1) for ASSET/EXPERIMENT. */
    private double amount;

    private String note;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    protected RevenueEntry() {
        // for JPA
    }

    public RevenueEntry(String id, RevenueKind kind, double amount, String note) {
        this.id = id;
        this.kind = kind;
        this.amount = amount;
        this.note = note;
        this.occurredAt = Instant.now();
    }
}
