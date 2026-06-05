package com.jarvis.connectors;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.jarvis.common.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

/**
 * Real GitHub connector — calls the GitHub REST API with a personal-access
 * token taken from the Secrets Vault (entry name {@code github-token}).
 *
 * <p>Beyond listing repos/issues, it exposes the actions a dev-workflow agent needs to actually
 * REVIEW and TRIAGE: read a PR with its diff, comment on a PR or issue, and label an issue. These
 * are tools the model chooses — Jarvis decides when a PR is worth reviewing, not a hard-coded rule.
 */
@Component
@RequiredArgsConstructor
public class GitHubConnector implements Connector {

    private static final int MAX_PATCH_CHARS = 6_000;   // keep a PR diff token-sane for the model

    private final RestClient client = RestClient.create("https://api.github.com");
    private final ObjectMapper mapper;


    @Override public String id() { return "github"; }
    @Override public String name() { return "GitHub"; }
    @Override public String category() { return "Developer"; }
    @Override public String requiredSecret() { return "github-token"; }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_repos", "List repos", "List your most recently updated repositories"),
                new ConnectorAction("open_issues", "Open issues", "List open issues assigned to you"),
                new ConnectorAction("list_prs", "List pull requests", "List open PRs in a repo {repo:'owner/name', state?}"),
                new ConnectorAction("get_pr", "Read a PR + diff", "Read a PR with its file diffs {repo, number}"),
                new ConnectorAction("comment_pr", "Comment on a PR", "Post a review comment on a PR {repo, number, body}"),
                new ConnectorAction("list_issues", "List repo issues", "List open issues in a repo {repo, state?}"),
                new ConnectorAction("comment_issue", "Comment on an issue", "Post a comment on an issue {repo, number, body}"),
                new ConnectorAction("label_issue", "Label an issue", "Add labels to an issue {repo, number, labels:[]}"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "list_repos" -> listRepos(token);
            case "open_issues" -> openIssues(token);
            case "list_prs" -> listPrs(a, token);
            case "get_pr" -> getPr(a, token);
            case "comment_pr", "comment_issue" -> comment(a, token);   // PR comments use the issues endpoint
            case "list_issues" -> listIssues(a, token);
            case "label_issue" -> labelIssue(a, token);
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

    private String listPrs(JsonNode a, String token) throws Exception {
        String repo = repo(a);
        String state = a.path("state").asText("open");
        JsonNode arr = mapper.readTree(get("/repos/" + repo + "/pulls?state=" + state + "&per_page=20", token));
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : arr) {
            sb.append("#").append(p.path("number").asInt())
                    .append(" ").append(p.path("title").asText())
                    .append(" by ").append(p.path("user").path("login").asText())
                    .append(" [").append(p.path("head").path("ref").asText()).append("]\n");
        }
        return sb.isEmpty() ? "No " + state + " pull requests in " + repo + "." : sb.toString().trim();
    }

    private String getPr(JsonNode a, String token) throws Exception {
        String repo = repo(a);
        int number = number(a);
        JsonNode pr = mapper.readTree(get("/repos/" + repo + "/pulls/" + number, token));
        JsonNode files = mapper.readTree(get("/repos/" + repo + "/pulls/" + number + "/files?per_page=50", token));

        StringBuilder sb = new StringBuilder();
        sb.append("PR #").append(number).append(": ").append(pr.path("title").asText()).append("\n")
                .append("by ").append(pr.path("user").path("login").asText())
                .append(" — ").append(pr.path("changed_files").asInt()).append(" file(s), +")
                .append(pr.path("additions").asInt()).append("/-").append(pr.path("deletions").asInt()).append("\n");
        String body = pr.path("body").asText("");
        if (!body.isBlank()) {
            sb.append("Description: ").append(body.length() > 800 ? body.substring(0, 800) + "…" : body).append("\n");
        }
        sb.append("\n--- Diff ---\n");
        int budget = MAX_PATCH_CHARS;
        for (JsonNode f : files) {
            String name = f.path("filename").asText();
            String patch = f.path("patch").asText("");
            sb.append("\n## ").append(name).append(" (").append(f.path("status").asText()).append(")\n");
            if (patch.isBlank()) {
                sb.append("(binary or too large — no textual diff)\n");
                continue;
            }
            if (patch.length() > budget) {
                sb.append(patch, 0, Math.max(0, budget)).append("\n…(diff truncated)…\n");
                break;
            }
            sb.append(patch).append("\n");
            budget -= patch.length();
        }
        return sb.toString().trim();
    }

    private String listIssues(JsonNode a, String token) throws Exception {
        String repo = repo(a);
        String state = a.path("state").asText("open");
        JsonNode arr = mapper.readTree(get("/repos/" + repo + "/issues?state=" + state + "&per_page=30", token));
        StringBuilder sb = new StringBuilder();
        for (JsonNode i : arr) {
            if (i.has("pull_request")) {
                continue;   // the issues endpoint also returns PRs — skip them
            }
            sb.append("#").append(i.path("number").asInt())
                    .append(" ").append(i.path("title").asText());
            List<String> labels = new ArrayList<>();
            i.path("labels").forEach(l -> labels.add(l.path("name").asText()));
            if (!labels.isEmpty()) {
                sb.append(" {").append(String.join(", ", labels)).append("}");
            }
            sb.append("\n");
        }
        return sb.isEmpty() ? "No " + state + " issues in " + repo + "." : sb.toString().trim();
    }

    private String comment(JsonNode a, String token) throws Exception {
        String repo = repo(a);
        int number = number(a);
        String body = a.path("body").asText("");
        if (body.isBlank()) {
            return "Error: provide a 'body' for the comment.";
        }
        JsonNode res = mapper.readTree(post("/repos/" + repo + "/issues/" + number + "/comments",
                mapper.createObjectNode().put("body", body).toString(), token));
        return "Comment posted on " + repo + " #" + number + ": " + res.path("html_url").asText();
    }

    private String labelIssue(JsonNode a, String token) throws Exception {
        String repo = repo(a);
        int number = number(a);
        List<String> labels = new ArrayList<>();
        a.path("labels").forEach(l -> labels.add(l.asText()));
        if (labels.isEmpty() && a.has("label")) {
            labels.add(a.path("label").asText());
        }
        if (labels.isEmpty()) {
            return "Error: provide 'labels' (array) or 'label'.";
        }
        var payload = mapper.createObjectNode();
        var arr = payload.putArray("labels");
        labels.forEach(arr::add);
        post("/repos/" + repo + "/issues/" + number + "/labels", payload.toString(), token);
        return "Labeled " + repo + " #" + number + " with " + String.join(", ", labels) + ".";
    }

    private static String repo(JsonNode a) {
        String repo = a.path("repo").asText("");
        if (repo.isBlank()) {
            throw new NotFoundException("Provide 'repo' as 'owner/name'.");
        }
        return repo;
    }

    private static int number(JsonNode a) {
        int n = a.path("number").asInt(a.path("pr").asInt(a.path("issue").asInt(0)));
        if (n <= 0) {
            throw new NotFoundException("Provide the PR/issue 'number'.");
        }
        return n;
    }

    private String get(String uri, String token) {
        return client.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve().body(String.class);
    }

    private String post(String uri, String jsonBody, String token) {
        return client.post().uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .retrieve().body(String.class);
    }
}
