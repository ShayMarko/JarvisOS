package com.jarvis.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Registry of permanent agents (spec §7.1, full roster) and a factory for
 * temporary agents (spec §7.2). Each agent is data: a system prompt + the set
 * of tools it's allowed to use. Agents whose dedicated tools don't exist yet
 * are given the closest available tools, so the roster is complete and grows as
 * new capabilities/connectors are added.
 */
@Component
public class AgentRegistry {

    private final Map<String, AgentDefinition> bySlug = new LinkedHashMap<>();
    private final AtomicInteger tempCounter = new AtomicInteger();

    public AgentRegistry() {
        // --- Core ---
        add("General Assistant", "general", "Handles general requests and routes work to capabilities.",
                "You are Jarvis, a helpful local AI assistant on the user's Mac. Use tools to act (files, apps, "
                + "clipboard, screenshots, Spotlight, web, connectors), remember durable facts with memory_write, then answer concisely.",
                List.of("list_files", "read_file", "write_file", "search_files", "kb_search", "web_search", "fetch_url",
                        "system_status", "memory_search", "memory_write", "connector_invoke", "open_app",
                        "reveal_in_finder", "clipboard_read", "clipboard_write", "screenshot", "spotlight_search",
                        "image_convert", "say", "list_projects", "open_project", "daily_digest",
                        "create_pdf", "create_docx", "create_diagram", "ocr_image", "mcp_list", "mcp_call",
                        "backup_create", "backup_list"), "general");

        // --- Engineering ---
        add("Product / Spec Agent", "product", "Writes specs, user stories and acceptance criteria.",
                "You are the Product/Spec Agent. Produce clear specs, user stories and acceptance criteria; save them as files.",
                List.of("write_file", "read_file", "kb_search", "memory_search", "web_search"), "dev");
        add("Backend Agent", "backend", "Writes backend services, APIs and persistence.",
                "You are the Backend Agent. Implement and explain backend code using the file tools.",
                List.of("read_file", "write_file", "search_files", "list_files"), "dev");
        add("Frontend Agent", "frontend", "Builds client UI, components and state.",
                "You are the Frontend Agent. Implement and explain client/UI code using the file tools.",
                List.of("read_file", "write_file", "search_files", "list_files"), "dev");
        add("Test Agent", "test", "Writes and reasons about unit/integration/E2E tests.",
                "You are the Test Agent. Write and explain tests for the code in the Explorer.",
                List.of("read_file", "write_file", "search_files"), "dev");
        add("Code Review Agent", "review", "Reviews code for quality, naming and security smells.",
                "You are the Code Review Agent. Review code for correctness, clarity and security smells.",
                List.of("read_file", "search_files"), "dev");
        add("Debug Agent", "debug", "Finds and fixes bugs, reads logs, reproduces issues.",
                "You are the Debug Agent. Investigate failures by reading code and logs, then propose fixes.",
                List.of("read_file", "search_files", "list_files"), "dev");
        add("Code & Bug Fix Agent", "codefix", "Focuses on fixing bugs and writing code.",
                "You are the Code & Bug Fix Agent. Read the relevant files and implement fixes.",
                List.of("read_file", "write_file", "search_files", "list_files"), "dev");
        add("Code Agent", "code", "Reads code and helps with development tasks.",
                "You are the Code Agent. Read the relevant files and help with code questions and fixes.",
                List.of("read_file", "search_files", "list_files"), "dev");
        add("UI Screenshot QA Agent", "uiqa", "Compares UI screenshots against requirements.",
                "You are the UI Screenshot QA Agent. Capture screenshots and compare the UI against requirements.",
                List.of("screenshot", "read_file"), "dev");
        add("DevOps / Cloud Agent", "devops", "Docker, CI/CD, deployment, cloud.",
                "You are the DevOps/Cloud Agent. Help with build, CI/CD, containers and deployment; open the user's projects in their IDE.",
                List.of("read_file", "write_file", "connector_invoke", "list_projects", "open_project"), "dev");

        // --- Files / system / data ---
        add("File Agent", "files", "Browses, reads, writes and searches the user's files.",
                "You are the File Agent. Help with files in the Jarvis Explorer; reveal them in Finder when useful.",
                List.of("list_files", "read_file", "write_file", "search_files", "reveal_in_finder"), "files");
        add("System Agent", "system", "Reports on machine health and resources.",
                "You are the System Agent. Report CPU, memory and disk status clearly.",
                List.of("system_status"), "monitoring");
        add("Data Analyst Agent", "data", "Analyses files and data the user points to.",
                "You are the Data Analyst. Read and analyse the requested files and explain the findings.",
                List.of("read_file", "search_files"), "data");
        add("Backup & Sync Agent", "backup", "Backs up and restores files and config.",
                "You are the Backup & Sync Agent. Help copy, back up and restore files in the Explorer. "
                + "Use backup_create to snapshot and backup_list to review existing snapshots.",
                List.of("list_files", "read_file", "write_file", "backup_create", "backup_list"), "files");

        // --- Knowledge / research ---
        add("Research Agent", "research", "Finds and summarises information from the web and local sources.",
                "You are the Research Agent. Search the web, the Knowledge Base, files and memory; summarise with sources.",
                List.of("web_search", "fetch_url", "kb_search", "search_files", "memory_search"), "research");
        add("Knowledge Librarian", "knowledge", "Organises and recalls the user's knowledge and notes.",
                "You are the Knowledge Librarian. Use the Knowledge Base, memory and file search to recall what the user knows, and memory_write to remember durable facts.",
                List.of("kb_search", "memory_search", "memory_write", "search_files"), "memory");
        add("Browser Automation Agent", "browser", "Navigates the web and extracts page content.",
                "You are the Browser Automation Agent. Fetch and read web pages and search the web.",
                List.of("fetch_url", "web_search", "screenshot"), "research");
        add("Trading News Agent", "trading", "Checks news and articles for fundamental trading.",
                "You are the Trading News Agent. Search the web for relevant financial news and summarise it.",
                List.of("web_search", "fetch_url"), "research");
        add("Meeting Assistant Agent", "meeting", "Transcribes and summarises meetings; extracts action items.",
                "You are the Meeting Assistant. Summarise notes and extract action items; save them.",
                List.of("read_file", "write_file", "memory_write"), "research");
        add("Finance / Budget Agent", "finance", "Manages expenses, invoices and budgets.",
                "You are the Finance/Budget Agent. Help track expenses and budgets from the user's files and the web.",
                List.of("read_file", "search_files", "web_search"), "data");

        // --- Communications ---
        add("Email Agent", "email", "Reads, summarises and drafts email.",
                "You are the Email Agent. Read and summarise mail and draft replies via the email connector.",
                List.of("connector_invoke", "memory_search"), "connectors");
        add("Calendar & Schedule Agent", "calendar", "Manages calendar, meetings and reminders.",
                "You are the Calendar Agent. Check the calendar and help schedule via the calendar connector.",
                List.of("connector_invoke"), "connectors");
        add("Digital Marketing Agent", "marketing", "Marketing, campaigns, copy and funnels.",
                "You are the Digital Marketing Agent. Draft marketing copy and research the market.",
                List.of("web_search", "write_file"), "connectors");
        add("Social Media Agent", "social", "Posts, schedules and analyses engagement.",
                "You are the Social Media Agent. Draft posts and act via connectors.",
                List.of("connector_invoke", "write_file"), "connectors");
        add("Customer Support Agent", "support", "Answers customers, FAQs and tickets.",
                "You are the Customer Support Agent. Answer using the Knowledge Base and connectors.",
                List.of("connector_invoke", "kb_search"), "connectors");
        add("Connector Health Agent", "connector-health", "Checks connector/service health.",
                "You are the Connector Health Agent. Report on connector status and the system.",
                List.of("connector_invoke", "system_status"), "connectors");
        add("Connector Agent", "connectors", "Acts on external services (Gmail, GitHub, Calendar, Slack).",
                "You are the Connector Agent. Use connector_invoke to read from and act on external services.",
                List.of("connector_invoke", "memory_search"), "connectors");
        add("MCP / Integrations Agent", "mcp", "Uses external MCP servers (filesystem, databases, browser, …).",
                "You are the MCP Integrations Agent. Call mcp_list to discover connected MCP servers and their tools, then mcp_call to use them.",
                List.of("mcp_list", "mcp_call", "connector_invoke"), "connectors");

        // --- Governance / ops ---
        add("Security / Permission Agent", "security", "Assesses risk, permissions and secrets.",
                "You are the Security Agent. Assess command/action risk and report on system and permissions.",
                List.of("system_status", "memory_search"), "security");
        add("Privacy Agent", "privacy", "Spots sensitive data before it leaves the machine.",
                "You are the Privacy Agent. Identify sensitive information before any external call.",
                List.of("memory_search"), "security");
        add("Workflow Automation Agent", "workflow", "Builds automations and recurring jobs.",
                "You are the Workflow Automation Agent. Help design automations using connectors and commands.",
                List.of("connector_invoke"), "workflows");
        add("Home / IoT Agent", "iot", "Controls smart-home devices (via Home Assistant, later).",
                "You are the Home/IoT Agent. Control smart-home devices through connectors.",
                List.of("connector_invoke"), "connectors");
        add("Model Router Agent", "model-router", "Chooses models and weighs cost/quality/latency.",
                "You are the Model Router Agent. Reason about which model fits a task by cost, quality and latency.",
                List.of("system_status"), "monitoring");
        add("Evaluator / Critic Agent", "evaluator", "Checks output quality before it returns to the user.",
                "You are the Evaluator/Critic Agent. Critically assess a draft answer for correctness and completeness.",
                List.of("read_file"), "monitoring");
    }

    private void add(String name, String slug, String role, String systemPrompt, List<String> tools, String category) {
        bySlug.put(slug, new AgentDefinition(name, slug, role, systemPrompt, tools, category));
    }

    /**
     * Grant extra tools (e.g. plugin-contributed ones) to the General agent so they're
     * reachable through normal routing. Rebuilds the definition with a deduped tool list.
     */
    public synchronized void grantToolsToGeneral(List<String> toolNames) {
        AgentDefinition g = bySlug.get("general");
        if (g == null || toolNames == null || toolNames.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(g.toolNames());
        merged.addAll(toolNames);
        bySlug.put("general", new AgentDefinition(g.name(), g.slug(), g.role(),
                g.systemPrompt(), List.copyOf(merged), g.category()));
    }

    /** Remove tools from the General agent (when a plugin is uninstalled). */
    public synchronized void revokeToolsFromGeneral(List<String> toolNames) {
        AgentDefinition g = bySlug.get("general");
        if (g == null || toolNames == null || toolNames.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(g.toolNames());
        toolNames.forEach(merged::remove);
        bySlug.put("general", new AgentDefinition(g.name(), g.slug(), g.role(),
                g.systemPrompt(), List.copyOf(merged), g.category()));
    }

    public List<AgentDefinition> all() {
        return List.copyOf(bySlug.values());
    }

    public Optional<AgentDefinition> find(String slug) {
        return Optional.ofNullable(bySlug.get(slug));
    }

    public AgentDefinition general() {
        return bySlug.get("general");
    }

    /** Create an ephemeral specialist for a one-off subtask (spec §7.2). */
    public AgentDefinition createTemporary(String role, List<String> toolNames) {
        int n = tempCounter.incrementAndGet();
        return new AgentDefinition("Temporary Agent #" + n, "temp-" + n, role,
                "You are an ephemeral specialist agent. " + role, toolNames, "temporary");
    }
}
