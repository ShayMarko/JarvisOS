package com.jarvis.connectors;

/**
 * Health of a connector (spec §9, Connector Health Agent).
 * <ul>
 *   <li>{@code CONNECTED} — a credential exists in the Secrets Vault; live API calls are made.</li>
 *   <li>{@code DISCONNECTED} — no credential; the connector cannot run until one is stored.</li>
 *   <li>{@code ERROR} — the connector reported a failure on last use.</li>
 * </ul>
 */
public enum ConnectorStatus {
    CONNECTED,
    DISCONNECTED,
    ERROR
}
