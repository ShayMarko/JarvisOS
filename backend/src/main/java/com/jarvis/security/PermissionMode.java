package com.jarvis.security;

/**
 * The three permission modes from spec §11.1.
 *
 * <ul>
 *   <li>{@code SAFE} — Jarvis only reads/analyses/suggests; no mutations.</li>
 *   <li>{@code ASSISTED} — mutations allowed, but destructive ones (delete)
 *       need explicit confirmation (a placeholder for the Phase 5 Approval Center).</li>
 *   <li>{@code AUTONOMOUS} — mutations allowed within configured scopes without prompting.</li>
 * </ul>
 */
public enum PermissionMode {
    SAFE,
    ASSISTED,
    AUTONOMOUS
}
