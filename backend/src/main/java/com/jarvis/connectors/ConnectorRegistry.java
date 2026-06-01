package com.jarvis.connectors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.audit.AuditService;
import com.jarvis.error.Exceptions.ConflictException;
import com.jarvis.error.Exceptions.ConnectorException;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.secrets.SecretsVaultService;

/**
 * Indexes connectors, derives each one's health from the Secrets Vault, and is
 * the single entry point for invoking connector actions (spec §9). It pulls
 * credentials from the vault — connectors never see the LLM and the LLM never
 * sees the secret.
 */
@Component
public class ConnectorRegistry {

    private final Map<String, Connector> byId = new LinkedHashMap<>();
    private final SecretsVaultService vault;
    private final AuditService audit;

    public ConnectorRegistry(List<Connector> connectors, SecretsVaultService vault, AuditService audit) {
        connectors.forEach(c -> byId.put(c.id(), c));
        this.vault = vault;
        this.audit = audit;
    }

    public List<ConnectorInfo> list() {
        return byId.values().stream().map(this::toInfo).toList();
    }

    public String invoke(String connectorId, String actionId, String argumentsJson) {
        Connector connector = byId.get(connectorId);
        if (connector == null) {
            throw new NotFoundException("No connector '" + connectorId + "'");
        }
        if (statusOf(connector) != ConnectorStatus.CONNECTED) {
            throw new ConflictException("Connector '" + connector.name() + "' is not connected — store a \""
                    + connector.requiredSecret() + "\" credential in the Secrets Vault first.");
        }
        String credential = vault.revealByName(connector.requiredSecret());
        audit.record("CONNECTOR", connectorId + ":" + actionId, connector.name(), "OK", "live");
        try {
            return connector.invoke(actionId, argumentsJson, credential);
        } catch (NotFoundException | ConflictException e) {
            throw e;
        } catch (Exception e) {
            audit.record("CONNECTOR", connectorId + ":" + actionId, connector.name(), "ERROR", e.getMessage());
            throw new ConnectorException(connector.name() + " call failed: " + e.getMessage());
        }
    }

    public String healthSummary() {
        long connected = byId.values().stream().filter(c -> statusOf(c) == ConnectorStatus.CONNECTED).count();
        return connected + "/" + byId.size() + " connected";
    }

    private ConnectorStatus statusOf(Connector c) {
        if (c.requiredSecret() == null) {
            return ConnectorStatus.CONNECTED;
        }
        return vault.has(c.requiredSecret()) ? ConnectorStatus.CONNECTED : ConnectorStatus.DISCONNECTED;
    }

    private ConnectorInfo toInfo(Connector c) {
        return new ConnectorInfo(c.id(), c.name(), c.category(), c.requiredSecret(), statusOf(c), c.actions());
    }
}
