package com.jarvis.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per meaningful Jarvis action. The spec requires a full Audit Log;
 * in Phase 1 we record every command/input that passes through the engine.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant timestamp;

    /** e.g. SLASH_COMMAND, AI_REQUEST. */
    private String inputType;

    /** The slash command invoked, if any (e.g. "/help"). */
    private String command;

    /** Raw user input. */
    @Column(length = 2000)
    private String input;

    /** OK, ERROR, PENDING_APPROVAL ... */
    private String status;

    @Column(length = 2000)
    private String detail;

    protected AuditLogEntry() {
        // for JPA
    }

    public AuditLogEntry(Instant timestamp, String inputType, String command,
                         String input, String status, String detail) {
        this.timestamp = timestamp;
        this.inputType = inputType;
        this.command = command;
        this.input = input;
        this.status = status;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public String getInputType() { return inputType; }
    public String getCommand() { return command; }
    public String getInput() { return input; }
    public String getStatus() { return status; }
    public String getDetail() { return detail; }
}
