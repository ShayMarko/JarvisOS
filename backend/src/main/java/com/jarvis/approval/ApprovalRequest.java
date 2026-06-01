package com.jarvis.approval;

import java.time.Instant;

import com.jarvis.security.RiskLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * A pending (or decided) request for human approval before a sensitive action
 * runs (spec §11.2 Approval Center). This row is the durable audit trail; the
 * actual deferred action lives in memory until the decision is made.
 */
@Entity
@Table(name = "approval_request")
@Getter
public class ApprovalRequest {

    @Id
    private String id;

    /** What kind of action this is, e.g. "terminal" — used for remembered decisions. */
    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(nullable = false)
    private String title;

    /** Why Jarvis wants to do this. */
    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    /** A preview of what will run (the command, the diff, the recipient, …). */
    @Column(length = 4000)
    private String preview;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Setter
    @Column(name = "decided_at")
    private Instant decidedAt;

    @Setter
    @Column(name = "result_summary", length = 4000)
    private String resultSummary;

    protected ApprovalRequest() {
        // for JPA
    }

    public ApprovalRequest(String id, String actionType, String title, String description,
                           RiskLevel riskLevel, String preview) {
        this.id = id;
        this.actionType = actionType;
        this.title = title;
        this.description = description;
        this.riskLevel = riskLevel;
        this.preview = preview;
        this.status = ApprovalStatus.PENDING;
        this.createdAt = Instant.now();
    }
}
