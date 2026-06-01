package com.jarvis.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.audit.AuditService;
import com.jarvis.error.Exceptions.ConflictException;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.secrets.SecretsVaultService;

class ConnectorRegistryTest {

    private final SecretsVaultService vault = mock(SecretsVaultService.class);
    private final Connector github = new GitHubConnector(new ObjectMapper());

    private ConnectorRegistry registry() {
        return new ConnectorRegistry(List.of(github), vault, mock(AuditService.class));
    }

    @Test
    void disconnectedWhenNoCredential() {
        when(vault.has("github-token")).thenReturn(false);
        assertThat(registry().list().get(0).status()).isEqualTo(ConnectorStatus.DISCONNECTED);
    }

    @Test
    void connectedWhenCredentialPresent() {
        when(vault.has("github-token")).thenReturn(true);
        assertThat(registry().list().get(0).status()).isEqualTo(ConnectorStatus.CONNECTED);
    }

    @Test
    void invokingDisconnectedConnectorIsRejected() {
        when(vault.has("github-token")).thenReturn(false);
        assertThatThrownBy(() -> registry().invoke("github", "list_repos", "{}"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void unknownConnectorThrows() {
        assertThatThrownBy(() -> registry().invoke("nope", "x", "{}")).isInstanceOf(NotFoundException.class);
    }
}
