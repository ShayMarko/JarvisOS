package com.jarvis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.security.RiskLevel;

/** Identity, risk gating, and pre-network arg guards for the Phase-F connectors (no live calls). */
class PhaseFConnectorsTest {

    private final ObjectMapper m = new ObjectMapper();

    @Test
    void twilioIsHighRiskAndGuardsArgs() throws Exception {
        TwilioConnector c = new TwilioConnector(m);
        assertThat(c.id()).isEqualTo("twilio");
        assertThat(c.requiredSecret()).isEqualTo("twilio-token");
        assertThat(c.actionRisk("send_sms")).isEqualTo(RiskLevel.HIGH);
        assertThat(c.invoke("send_sms", "{}", "AC:tok")).contains("to");
        assertThat(c.invoke("send_sms", "{\"to\":\"1\",\"from\":\"2\",\"body\":\"hi\"}", "no-colon"))
                .contains("AccountSID");
    }

    @Test
    void redditIsKeylessAndGuardsArgs() throws Exception {
        RedditConnector c = new RedditConnector(m);
        assertThat(c.id()).isEqualTo("reddit");
        assertThat(c.requiredSecret()).isNull();
        assertThat(c.invoke("subreddit_hot", "{}", null)).contains("subreddit");
        assertThat(c.invoke("search", "{}", null)).contains("query");
    }

    @Test
    void calcomIdentity() {
        CalcomConnector c = new CalcomConnector(m);
        assertThat(c.id()).isEqualTo("calcom");
        assertThat(c.requiredSecret()).isEqualTo("calcom-token");
        assertThat(c.actions()).extracting(ConnectorAction::id)
                .containsExactlyInAnyOrder("list_bookings", "list_event_types");
    }

    @Test
    void youtubeGuardsArgs() throws Exception {
        YouTubeConnector c = new YouTubeConnector(m);
        assertThat(c.id()).isEqualTo("youtube");
        assertThat(c.requiredSecret()).isEqualTo("youtube-token");
        assertThat(c.invoke("search", "{}", "key")).contains("query");
        assertThat(c.invoke("channel_stats", "{}", "key")).contains("channelId");
    }
}
