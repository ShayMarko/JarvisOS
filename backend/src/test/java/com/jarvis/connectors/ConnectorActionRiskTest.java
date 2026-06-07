package com.jarvis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.security.RiskLevel;

/**
 * Verifies the per-action approval gate: consequential connector actions (spend/deploy/publish/send) are
 * HIGH risk so connector_invoke routes them through the Approval Center, while read-only calls stay LOW.
 */
class ConnectorActionRiskTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void moneyAndDeployAndPublishActionsAreHigh() {
        assertThat(new StripeConnector(mapper).actionRisk("create_payment_link")).isEqualTo(RiskLevel.HIGH);
        assertThat(new GumroadConnector(mapper).actionRisk("create_product")).isEqualTo(RiskLevel.HIGH);
        assertThat(new AyrshareConnector(mapper).actionRisk("post")).isEqualTo(RiskLevel.HIGH);
        assertThat(new AyrshareConnector(mapper).actionRisk("post_video")).isEqualTo(RiskLevel.HIGH);
        assertThat(new ResendConnector(mapper).actionRisk("send_email")).isEqualTo(RiskLevel.HIGH);
        assertThat(new PrintfulConnector(mapper).actionRisk("create_product")).isEqualTo(RiskLevel.HIGH);
        assertThat(new CloudflareConnector(mapper, null).actionRisk("deploy_worker")).isEqualTo(RiskLevel.HIGH);
        assertThat(new ShopifyConnector(mapper).actionRisk("create_product")).isEqualTo(RiskLevel.HIGH);
        assertThat(new EtsyConnector(mapper).actionRisk("create_draft_listing")).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void readOnlyActionsStayLow() {
        assertThat(new StripeConnector(mapper).actionRisk("list_products")).isEqualTo(RiskLevel.LOW);
        assertThat(new AyrshareConnector(mapper).actionRisk("history")).isEqualTo(RiskLevel.LOW);
        assertThat(new PrintfulConnector(mapper).actionRisk("catalog")).isEqualTo(RiskLevel.LOW);
        assertThat(new CloudflareConnector(mapper, null).actionRisk("list_zones")).isEqualTo(RiskLevel.LOW);
        assertThat(new ShopifyConnector(mapper).actionRisk("list_products")).isEqualTo(RiskLevel.LOW);
        assertThat(new EtsyConnector(mapper).actionRisk("find_shop")).isEqualTo(RiskLevel.LOW);
    }
}
