package com.jarvis.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

class NotionConnectorTest {

    private final NotionConnector notion = new NotionConnector(new ObjectMapper());

    @Test
    void exposesReadAndWriteActions() {
        List<String> ids = notion.actions().stream().map(ConnectorAction::id).toList();
        assertThat(ids).contains("search", "get_page", "create_page", "append_text", "query_database");
    }

    @Test
    void createPageNeedsTitleAndParentBeforeAnyNetworkCall() throws Exception {
        assertThat(notion.invoke("create_page", "{\"parent_page_id\":\"p\"}", "tok")).contains("title");
        assertThat(notion.invoke("create_page", "{\"title\":\"My Template\"}", "tok")).contains("parent");
    }

    @Test
    void appendNeedsPageAndText() throws Exception {
        assertThat(notion.invoke("append_text", "{\"text\":\"hi\"}", "tok")).contains("page_id");
    }

    @Test
    void unknownActionIsRejected() {
        assertThatThrownBy(() -> notion.invoke("nope", "{}", "tok")).isInstanceOf(NotFoundException.class);
    }
}
