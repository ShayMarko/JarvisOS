package com.jarvis.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

class MySqlConnectorTest {

    private final MySqlConnector sql = new MySqlConnector(new ObjectMapper());

    @Test
    void exposesReadActions() {
        List<String> ids = sql.actions().stream().map(ConnectorAction::id).toList();
        assertThat(ids).contains("list_tables", "query");
    }

    @Test
    void refusesNonReadStatements() throws Exception {
        assertThat(sql.invoke("query", "{\"sql\":\"DELETE FROM users\"}", "jdbc:mysql://x")).contains("READ-ONLY");
        assertThat(sql.invoke("query", "{\"sql\":\"UPDATE t SET a=1\"}", "jdbc:mysql://x")).contains("READ-ONLY");
    }

    @Test
    void refusesMultipleStatements() throws Exception {
        assertThat(sql.invoke("query", "{\"sql\":\"SELECT 1; DROP TABLE t\"}", "jdbc:mysql://x"))
                .contains("single read-only statement");
    }

    @Test
    void requiresSql() throws Exception {
        assertThat(sql.invoke("query", "{}", "jdbc:mysql://x")).contains("sql");
    }

    @Test
    void unknownActionIsRejected() {
        assertThatThrownBy(() -> sql.invoke("nope", "{}", "jdbc:mysql://x")).isInstanceOf(NotFoundException.class);
    }
}
