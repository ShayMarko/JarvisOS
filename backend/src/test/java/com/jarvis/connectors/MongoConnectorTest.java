package com.jarvis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class MongoConnectorTest {

    private final MongoConnector mongo = new MongoConnector(new ObjectMapper());

    @Test
    void exposesReadActions() {
        List<String> ids = mongo.actions().stream().map(ConnectorAction::id).toList();
        assertThat(ids).contains("list_collections", "find", "count");
    }

    @Test
    void requiresADatabaseInUriOrArgs() throws Exception {
        // URI without a db and no 'database' arg → guarded before any client is opened
        assertThat(mongo.invoke("find", "{\"collection\":\"x\"}", "mongodb://localhost:27017"))
                .contains("no database");
    }
}
