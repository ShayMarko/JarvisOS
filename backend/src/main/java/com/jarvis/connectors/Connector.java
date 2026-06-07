package com.jarvis.connectors;

import java.util.List;

/**
 * A connection to an external service (spec §9). Per the spec's security rule,
 * connectors never receive secrets through the LLM — credentials come only via
 * the Secrets Vault, keyed by {@link #requiredSecret()}.
 */
public interface Connector {

    String id();

    String name();

    String category();

    /** The Secrets Vault entry name this connector needs, or null if none. */
    String requiredSecret();

    List<ConnectorAction> actions();

    /**
     * Risk of a specific action. Read-only calls are LOW (autonomous); real external writes that spend money,
     * deploy live, send messages or publish should return HIGH so they route through the Approval Center (the
     * bell, with Approve/Decline) before running. Default LOW — connectors override for consequential actions.
     */
    default com.jarvis.security.RiskLevel actionRisk(String actionId) {
        return com.jarvis.security.RiskLevel.LOW;
    }

    /** Run an action against the live API. {@code credential} is the decrypted token from the vault. */
    String invoke(String actionId, String argumentsJson, String credential) throws Exception;
}
