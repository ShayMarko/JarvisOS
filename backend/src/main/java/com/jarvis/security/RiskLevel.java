package com.jarvis.security;

/** Risk level of an action, used by the Approval Center and risk classifier (spec §11.2, §18.1). */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
