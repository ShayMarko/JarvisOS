package com.jarvis.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

class TelegramConnectorTest {

    private final TelegramConnector tg = new TelegramConnector(new ObjectMapper());

    @Test
    void exposesMessagingActions() {
        List<String> ids = tg.actions().stream().map(ConnectorAction::id).toList();
        assertThat(ids).contains("get_me", "send_message", "get_updates");
    }

    @Test
    void sendRequiresChatIdBeforeAnyNetworkCall() throws Exception {
        assertThat(tg.invoke("send_message", "{\"text\":\"hi\"}", "tok")).contains("chat_id");
    }

    @Test
    void sendRequiresTextBeforeAnyNetworkCall() throws Exception {
        assertThat(tg.invoke("send_message", "{\"chat_id\":\"123\"}", "tok")).contains("text");
    }

    @Test
    void unknownActionIsRejected() {
        assertThatThrownBy(() -> tg.invoke("nope", "{}", "tok")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void identifiesAsTheTelegramConnector() {
        assertThat(tg.id()).isEqualTo("telegram");
        assertThat(tg.requiredSecret()).isEqualTo("telegram-bot-token");
    }
}
