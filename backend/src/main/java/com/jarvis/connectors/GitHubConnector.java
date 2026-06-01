package com.jarvis.connectors;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

/**
 * Real GitHub connector — calls the GitHub REST API with a personal-access
 * token taken from the Secrets Vault (entry name {@code github-token}).
 */
@Component
public class GitHubConnector implements Connector {

    private final RestClient client = RestClient.create("https://api.github.com");
    private final ObjectMapper mapper;

    public GitHubConnector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String id() { return "github"; }
    @Override public String name() { return "GitHub"; }
    @Override public String category() { return "Developer"; }
    @Override public String requiredSecret() { return "github-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_repos", "List repos", "List your most recently updated repositories"),
                new ConnectorAction("open_issues", "Open issues", "List open issues assigned to you"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        return switch (actionId) {
            case "list_repos" -> listRepos(token);
            case "open_issues" -> openIssues(token);
            default -> throw new NotFoundException("Unknown GitHub action '" + actionId + "'");
        };
    }

    private String listRepos(String token) throws Exception {
        JsonNode arr = mapper.readTree(get("/user/repos?per_page=10&sort=updated", token));
        StringBuilder sb = new StringBuilder();
        for (JsonNode r : arr) {
            sb.append(r.path("full_name").asText())
                    .append(" (").append(r.path("language").asText("?"))
                    .append(", ★").append(r.path("stargazers_count").asInt()).append(")\n");
        }
        return sb.isEmpty() ? "No repositories." : sb.toString().trim();
    }

    private String openIssues(String token) throws Exception {
        JsonNode arr = mapper.readTree(get("/issues?state=open&per_page=10&filter=assigned", token));
        StringBuilder sb = new StringBuilder();
        for (JsonNode i : arr) {
            sb.append("#").append(i.path("number").asInt())
                    .append(" ").append(i.path("title").asText())
                    .append(" (").append(i.path("repository").path("full_name").asText()).append(")\n");
        }
        return sb.isEmpty() ? "No open issues assigned to you." : sb.toString().trim();
    }

    private String get(String uri, String token) {
        return client.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve().body(String.class);
    }
}
