package com.jarvis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.security.RiskLevel;

/** Lemon Squeezy connector: identity, risk gating, and the create_checkout arg guard (no network). */
class LemonSqueezyConnectorTest {

    private final LemonSqueezyConnector c = new LemonSqueezyConnector(new ObjectMapper());

    @Test
    void identityAndSecret() {
        assertThat(c.id()).isEqualTo("lemonsqueezy");
        assertThat(c.requiredSecret()).isEqualTo("lemonsqueezy-token");
        assertThat(c.actions()).extracting(ConnectorAction::id)
                .contains("list_stores", "list_products", "recent_revenue", "create_checkout");
    }

    @Test
    void onlyCheckoutIsHighRisk() {
        assertThat(c.actionRisk("create_checkout")).isEqualTo(RiskLevel.HIGH);
        assertThat(c.actionRisk("list_stores")).isEqualTo(RiskLevel.LOW);
        assertThat(c.actionRisk("recent_revenue")).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void createCheckoutGuardsMissingIdsBeforeAnyCall() throws Exception {
        String r = c.invoke("create_checkout", "{}", "fake-key");
        assertThat(r).contains("storeId").contains("variantId");
    }
}
