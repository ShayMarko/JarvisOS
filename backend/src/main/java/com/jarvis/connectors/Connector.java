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

    /** Run an action against the live API. {@code credential} is the decrypted token from the vault. */
    String invoke(String actionId, String argumentsJson, String credential) throws Exception;
}
