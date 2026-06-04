package com.jarvis.connectors;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import lombok.RequiredArgsConstructor;

/**
 * Read-only MongoDB connector — query the user's own Mongo. The credential (Secrets Vault entry
 * {@code mongo-uri}) is the full connection string incl. the default database, e.g.
 * {@code mongodb://user:pass@host:27017/dbname}. Exposes list_collections / find / count only — no
 * writes — so Jarvis can explore the data safely.
 */
@Component
@RequiredArgsConstructor
public class MongoConnector implements Connector {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_CHARS = 4000;

    private final ObjectMapper mapper;

    @Override public String id() { return "mongo"; }
    @Override public String name() { return "MongoDB"; }
    @Override public String category() { return "Database"; }
    @Override public String requiredSecret() { return "mongo-uri"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_collections", "List collections", "List collections {database?}"),
                new ConnectorAction("find", "Find documents", "Find docs {collection, filter?, limit?, database?}"),
                new ConnectorAction("count", "Count documents", "Count docs {collection, filter?, database?}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String uri) throws Exception {
        JsonNode a = mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        ConnectionString cs = new ConnectionString(uri);
        String dbName = a.path("database").asText(cs.getDatabase() == null ? "" : cs.getDatabase());
        if (dbName.isBlank()) {
            return "Error: no database in the mongo-uri — pass 'database' or include it in the URI.";
        }
        try (MongoClient client = MongoClients.create(cs)) {
            MongoDatabase db = client.getDatabase(dbName);
            return switch (actionId) {
                case "list_collections" -> listCollections(db);
                case "find" -> find(db, a);
                case "count" -> count(db, a);
                default -> throw new NotFoundException("Unknown Mongo action '" + actionId + "'");
            };
        }
    }

    private String listCollections(MongoDatabase db) {
        List<String> names = new ArrayList<>();
        db.listCollectionNames().forEach(names::add);
        return names.isEmpty() ? "No collections." : String.join("\n", names);
    }

    private String find(MongoDatabase db, JsonNode a) {
        String collection = a.path("collection").asText("");
        if (collection.isBlank()) {
            return "Error: provide a 'collection'.";
        }
        Document filter = parseFilter(a.path("filter"));
        int limit = Math.min(MAX_LIMIT, Math.max(1, a.path("limit").asInt(DEFAULT_LIMIT)));
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Document doc : db.getCollection(collection).find(filter).limit(limit)) {
            if (sb.length() >= MAX_CHARS) {
                break;
            }
            sb.append(doc.toJson()).append('\n');
            n++;
        }
        return n == 0 ? "No matching documents." : sb.append("(").append(n).append(" doc(s))").toString();
    }

    private String count(MongoDatabase db, JsonNode a) {
        String collection = a.path("collection").asText("");
        if (collection.isBlank()) {
            return "Error: provide a 'collection'.";
        }
        long c = db.getCollection(collection).countDocuments(parseFilter(a.path("filter")));
        return collection + ": " + c + " document(s)" + (a.path("filter").isMissingNode() ? "" : " matching the filter");
    }

    /** A Mongo filter from a JSON object/string (or empty {} when absent). */
    private Document parseFilter(JsonNode filter) {
        if (filter == null || filter.isMissingNode() || filter.isNull()) {
            return new Document();
        }
        String json = filter.isTextual() ? filter.asText() : filter.toString();
        return json.isBlank() ? new Document() : Document.parse(json);
    }
}
