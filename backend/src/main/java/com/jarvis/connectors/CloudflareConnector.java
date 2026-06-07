package com.jarvis.connectors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/**
 * Real Cloudflare connector — closes the open hosting gap (micro-APIs previously needed Fly/Render, static
 * sites only had Netlify). Cloudflare Pages hosts static sites; Cloudflare Workers host single-file APIs at
 * the edge for free. API token from the Secrets Vault (entry {@code cloudflare-token}).
 *
 * <p>{@code deploy_worker} reads a single JavaScript file Jarvis built and PUTs it as a live Worker — so the
 * Micro-API lane can actually ship. Pages projects can be created here too (file push then via Git or wrangler).
 */
@Component
@RequiredArgsConstructor
public class CloudflareConnector implements Connector {

    private final RestClient client = RestClient.create("https://api.cloudflare.com/client/v4");
    private final ObjectMapper mapper;
    private final FileSystemService fs;

    @Override public String id() { return "cloudflare"; }
    @Override public String name() { return "Cloudflare"; }
    @Override public String category() { return "Hosting"; }
    @Override public String requiredSecret() { return "cloudflare-token"; }

    @Override
    public com.jarvis.security.RiskLevel actionRisk(String actionId) {
        return ("deploy_worker".equals(actionId) || "create_pages_project".equals(actionId))
                ? com.jarvis.security.RiskLevel.HIGH : com.jarvis.security.RiskLevel.LOW;
    }

    @Override
    public List<ConnectorAction> actions() {
        return List.of(
                new ConnectorAction("list_accounts", "List accounts", "Your Cloudflare accounts (for the accountId)"),
                new ConnectorAction("list_zones", "List DNS zones", "Domains/zones on the account"),
                new ConnectorAction("list_pages_projects", "List Pages projects", "Static-site projects {accountId}"),
                new ConnectorAction("create_pages_project", "Create Pages project",
                        "Create a static-site project {accountId, name, branch?}"),
                new ConnectorAction("deploy_worker", "Deploy a Worker",
                        "Publish a single-file edge API {accountId, name, scriptPath} — hosts a Micro-API for free"));
    }

    @Override
    public String invoke(String actionId, String argumentsJson, String token) throws Exception {
        JsonNode a = Json.read(mapper, argumentsJson);
        return switch (actionId) {
            case "list_accounts" -> listAccounts(token);
            case "list_zones" -> listZones(token);
            case "list_pages_projects" -> listPages(a, token);
            case "create_pages_project" -> createPages(a, token);
            case "deploy_worker" -> deployWorker(a, token);
            default -> throw new NotFoundException("Unknown Cloudflare action '" + actionId + "'");
        };
    }

    private String listAccounts(String token) {
        JsonNode r = read(get("/accounts", token));
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : r.path("result")) {
            sb.append("• ").append(n.path("name").asText()).append(" — id=").append(n.path("id").asText()).append('\n');
        }
        return sb.isEmpty() ? "No Cloudflare accounts found." : sb.toString().strip();
    }

    private String listZones(String token) {
        JsonNode r = read(get("/zones", token));
        StringBuilder sb = new StringBuilder();
        for (JsonNode z : r.path("result")) {
            sb.append("• ").append(z.path("name").asText()).append(" (").append(z.path("status").asText()).append(")\n");
        }
        return sb.isEmpty() ? "No DNS zones." : sb.toString().strip();
    }

    private String listPages(JsonNode a, String token) {
        String acct = requireAccount(a);
        JsonNode r = read(get("/accounts/" + acct + "/pages/projects", token));
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : r.path("result")) {
            sb.append("• ").append(p.path("name").asText()).append(" — ")
              .append(p.path("subdomain").asText("(no subdomain)")).append('\n');
        }
        return sb.isEmpty() ? "No Pages projects." : sb.toString().strip();
    }

    private String createPages(JsonNode a, String token) {
        String acct = requireAccount(a);
        String name = a.path("name").asText("");
        if (name.isBlank()) {
            return "Provide a project 'name'.";
        }
        String branch = a.path("branch").asText("main");
        String body = "{\"name\":\"" + esc(name) + "\",\"production_branch\":\"" + esc(branch) + "\"}";
        JsonNode r = read(client.post().uri("/accounts/{a}/pages/projects", acct)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(String.class));
        if (!r.path("success").asBoolean(false)) {
            return "Cloudflare rejected the project: " + r.path("errors");
        }
        return "✅ Created Pages project \"" + name + "\". Push files via Git or `wrangler pages deploy`.";
    }

    private String deployWorker(JsonNode a, String token) throws Exception {
        String acct = requireAccount(a);
        String name = a.path("name").asText("");
        String scriptPath = a.path("scriptPath").asText("");
        if (name.isBlank() || scriptPath.isBlank()) {
            return "Provide 'accountId', 'name' and 'scriptPath' (a single .js Worker file).";
        }
        Path script = fs.resolveExisting(scriptPath);
        String code = Files.readString(script);
        JsonNode r = read(client.put().uri("/accounts/{a}/workers/scripts/{n}", acct, name)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.parseMediaType("application/javascript"))
                .body(code).retrieve().body(String.class));
        if (!r.path("success").asBoolean(false)) {
            return "Cloudflare rejected the Worker: " + r.path("errors");
        }
        return "✅ Deployed Worker \"" + name + "\". Enable a workers.dev route or map a custom domain in the dashboard.";
    }

    private String requireAccount(JsonNode a) {
        String acct = a.path("accountId").asText("");
        if (acct.isBlank()) {
            throw new NotFoundException("Provide 'accountId' (run list_accounts first).");
        }
        return acct;
    }

    private String get(String path, String token) {
        return client.get().uri(path).header("Authorization", "Bearer " + token).retrieve().body(String.class);
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static String esc(String v) {
        return v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
