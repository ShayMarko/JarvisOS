package com.jarvis.connectors;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Read-only MySQL connector — lets Jarvis query the user's own MySQL DB. The credential (Secrets Vault
 * entry {@code mysql-url}) is the full JDBC URL incl. creds, e.g.
 * {@code jdbc:mysql://user:pass@host:3306/dbname}. READ-ONLY by guard: only SELECT/SHOW/DESCRIBE/EXPLAIN/WITH
 * run, the connection is opened read-only, and multi-statement input is refused — so Jarvis can explore
 * data but never mutate it.
 */
@Component
@RequiredArgsConstructor
public class MySqlConnector implements Connector {

    private static final Set<String> READ_VERBS = Set.of("SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "WITH");
    private static final int MAX_ROWS = 50;
    private static final int MAX_CHARS = 4000;

    private final ObjectMapper mapper;

    @Override public String id() { return "mysql"; }
    @Override public String name() { return "MySQL"; }
    @Override public String category() { return "Database"; }
    @Override public String requiredSecret() { return "mysql-url"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_tables", "List tables", "List tables in the database {}"),
                new ConnectorAction("query", "Run a read query", "Run a read-only SQL query {sql, limit?}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String url) throws Exception {
        JsonNode a = mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        return switch (actionId) {
            case "list_tables" -> runRead(url, "SHOW TABLES", MAX_ROWS);
            case "query" -> query(a, url);
            default -> throw new NotFoundException("Unknown MySQL action '" + actionId + "'");
        };
    }

    private String query(JsonNode a, String url) throws Exception {
        String sql = a.path("sql").asText(a.path("query").asText("")).trim();
        if (sql.isBlank()) {
            return "Error: provide a 'sql' query.";
        }
        String oneStatement = sql.endsWith(";") ? sql.substring(0, sql.length() - 1).trim() : sql;
        if (oneStatement.contains(";")) {
            return "Refused: only a single read-only statement is allowed.";
        }
        String verb = oneStatement.split("\\s+", 2)[0].toUpperCase();
        if (!READ_VERBS.contains(verb)) {
            return "Refused: this connector is READ-ONLY (allowed: " + String.join(", ", READ_VERBS) + ").";
        }
        int limit = Math.min(MAX_ROWS, Math.max(1, a.path("limit").asInt(MAX_ROWS)));
        return runRead(url, oneStatement, limit);
    }

    private String runRead(String url, String sql, int limit) throws Exception {
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setReadOnly(true);
            try (Statement st = conn.createStatement()) {
                st.setMaxRows(limit);
                try (ResultSet rs = st.executeQuery(sql)) {
                    return format(rs, limit);
                }
            }
        }
    }

    private static String format(ResultSet rs, int limit) throws Exception {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= cols; i++) {
            header.append(i > 1 ? " | " : "").append(md.getColumnLabel(i));
        }
        StringBuilder sb = new StringBuilder(header).append('\n');
        int rows = 0;
        while (rs.next() && rows < limit && sb.length() < MAX_CHARS) {
            for (int i = 1; i <= cols; i++) {
                String v = rs.getString(i);
                sb.append(i > 1 ? " | " : "").append(v == null ? "NULL" : v);
            }
            sb.append('\n');
            rows++;
        }
        sb.append("(").append(rows).append(" row").append(rows == 1 ? "" : "s").append(")");
        return sb.toString();
    }
}
