package com.jarvis.agent;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;

import lombok.RequiredArgsConstructor;

/**
 * Picks the best agent for a request (the Brain's routing step), reaching the FULL roster.
 *
 * <p>Two tiers: a fast keyword table for obvious cases, and — when the keywords don't pin a
 * specialist — a cheap LLM call that chooses from the whole roster by each agent's role. This
 * makes routing agentic (every specialist is reachable, chosen by reasoning) while spending a
 * routing call only on ambiguous requests. Falls back to keywords/General on mock or any error,
 * so it always works offline.
 */
@Component
@RequiredArgsConstructor
public class AgentSelector {

    private static final Logger log = LoggerFactory.getLogger(AgentSelector.class);

    /** An ordered routing rule: if the message contains any keyword, route to {@code slug}. */
    private record Rule(String slug, List<String> keywords) {}

    private static final List<Rule> RULES = List.of(
            new Rule("system", List.of("cpu", "ram", "system status", "resource", "health")),
            new Rule("knowledge", List.of("remember", "memory", "preference", "know about me")),
            new Rule("email", List.of("email", "inbox", "mail", "reply to")),
            new Rule("calendar", List.of("calendar", "schedule", "meeting", "event", "agenda")),
            new Rule("data", List.of("analyse", "analyze", "data", "csv", "spreadsheet")),
            new Rule("trading", List.of("trading", "trade setup", "market", "stock", "crypto", "ticker",
                    "price of", "wyckoff", "support and resistance", "candle", "swing trade", "btcusdt")),
            new Rule("notion", List.of("notion", "notion template", "notion templates")),
            new Rule("productbuilder", List.of("boilerplate", "starter kit", "saas starter", "sellable", "sell it",
                    "sell this", "product to sell", "package for sale", "gumroad", "digital product")),
            new Rule("appfactory", List.of("app factory", "launch an app", "launch a product", "productize",
                    "app to sell", "sell an app", "mvp to sell", "passive income app", "validate and build")),
            new Rule("microapi", List.of("rapidapi", "rapid api", "micro-api", "micro api", "api to sell",
                    "sell an api", "publish an api", "per-call api", "api on rapidapi")),
            new Rule("seosite", List.of("seo site", "content site", "affiliate site", "niche site",
                    "blog to monetize", "monetize a blog", "content website", "affiliate blog")),
            new Rule("scout", List.of("opportunity", "opportunities", "product idea", "product ideas", "what should i build",
                    "what to build", "trend", "trending", "niche to", "find a niche", "market gap", "demand for", "scout")),
            new Rule("analyst", List.of("which product", "best seller", "best-seller", "what's selling", "whats selling",
                    "double down", "underperform", "roi", "return on investment", "should i drop", "analyse my sales",
                    "analyze my sales", "portfolio analysis", "revenue breakdown")),
            new Rule("author", List.of("ebook", "e-book", "write a book", "write me a book", "author a book",
                    "my book", "the book", "a chapter", "kdp", "manuscript", "ghostwrite")),
            new Rule("devflow", List.of("pull request", "pull-request", "review pr", "review the pr", "review my pr",
                    "list prs", "open prs", "triage", "github issue", "github issues", "merge request")),
            new Rule("code", List.of("code", "function", "bug", "class ", "compile", "stack trace",
                    "build an app", "build a app", "build me an app", "build the app", "build a ", "create an app",
                    "spring boot", "react app", "backend", "frontend", "rest api", "web app", "implement")),
            new Rule("research", List.of("document", "docs", "according to", "knowledge base",
                    "on the web", "web search", "research", "look up", "summarise", "summarize", "find")),
            new Rule("files", List.of("file", "files", "folder", "directory", "read", "open", "explorer")));

    private final AgentRegistry registry;
    private final LanguageModel model;
    private final JarvisAiProperties ai;

    /**
     * Smart selection: a keyword-matched specialist wins immediately (free); otherwise — when the
     * request would fall back to General and a real model is active — ask the model to pick the
     * best specialist from the roster.
     */
    public AgentDefinition select(String message) {
        AgentDefinition keyword = byKeyword(message);
        if (!"general".equals(keyword.slug())) {
            return keyword;   // keywords already pinned a specialist — no model call needed
        }
        if (isMock()) {
            return keyword;
        }
        String slug = llmPick(message);
        return slug == null ? keyword : registry.find(slug).orElse(keyword);
    }

    /** Keyword-only routing (no model call) — the fast/offline path and the planner fallback. */
    public AgentDefinition byKeyword(String message) {
        String m = message == null ? "" : message.toLowerCase();
        // Build intent OUTRANKS domain keywords. "create a fullstack app for reminders with email
        // + calendar" is a BUILD task for the code agent (which has write_file + the headless build
        // contract) — not an email/calendar task. Without this, a domain word mentioned in passing
        // hijacks routing to a specialist that can't write files and just narrates code.
        if (looksLikeBuild(m)) {
            Optional<AgentDefinition> code = registry.find("code");
            if (code.isPresent()) {
                return code.get();
            }
        }
        return RULES.stream()
                .filter(rule -> rule.keywords().stream().anyMatch(m::contains))
                .map(rule -> registry.find(rule.slug()))
                .filter(Optional::isPresent).map(Optional::get)
                .findFirst()
                .orElseGet(registry::general);
    }

    /** A build/create verb paired with a software-artifact noun ⇒ this is a "build me X" request. */
    private static boolean looksLikeBuild(String m) {
        boolean verb = containsAny(m, "build", "create", "make", "develop", "implement",
                "scaffold", "generate", "code up", "write a ", "set up a", "put together");
        boolean noun = containsAny(m, "app", "application", "fullstack", "full-stack", "full stack",
                "website", "web app", "webapp", "web site", "api", "backend", "front end", "frontend",
                "service", "microservice", "dashboard", "bot", "game", "cli", "script", "program",
                "platform", "project", "system", "tool", "crud", "rest api");
        return verb && noun;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    /** Resolve a model-provided agent slug (e.g. from the planner), else keyword-route the text. */
    public AgentDefinition resolve(String slug, String fallbackText) {
        if (slug != null && !slug.isBlank()) {
            Optional<AgentDefinition> a = registry.find(slug.trim().toLowerCase());
            if (a.isPresent()) {
                return a.get();
            }
        }
        return byKeyword(fallbackText);
    }

    /** "slug: role" for every agent — the menu the model (planner or router) chooses from. */
    public String roster() {
        return registry.all().stream()
                .map(a -> a.slug() + ": " + a.role())
                .collect(Collectors.joining("\n"));
    }

    private String llmPick(String message) {
        try {
            String system = "You route the user's request to the single best specialist agent. "
                    + "Reply with ONLY that agent's slug (one token, lowercase), nothing else.\nAgents:\n" + roster();
            ModelResponse r = model.generate(
                    List.of(ChatMessage.system(system), ChatMessage.user(message)), List.of(), routerModel());
            if (r == null || r.text() == null) {
                return null;
            }
            String[] tokens = r.text().trim().toLowerCase().split("[^a-z0-9-]+");
            // Trust only a short, slug-like reply. Many slugs (email/data/code/files/...) are common
            // words, so a chatty reply would false-match — better to fall back to keywords/General
            // than to mis-route to the wrong specialist (a weak model returns prose; a strong one
            // returns just the slug as instructed).
            if (tokens.length == 0 || tokens.length > 3) {
                return null;
            }
            for (String tok : tokens) {
                if (!tok.isBlank() && registry.find(tok).isPresent()) {
                    return tok;
                }
            }
            return null;
        } catch (RuntimeException e) {
            log.debug("LLM agent routing failed, using keyword fallback: {}", e.getMessage());
            return null;
        }
    }

    private boolean isMock() {
        String p = ai.getProvider() == null ? "" : ai.getProvider().toLowerCase();
        return !(p.equals("ollama")
                || ((p.equals("claude") || p.equals("anthropic")) && hasKey(ai.getAnthropicApiKey()))
                || (p.equals("openai") && hasKey(ai.getOpenaiApiKey())));
    }

    private static boolean hasKey(String k) {
        return k != null && !k.isBlank();
    }

    /** Cheap model for routing (same tier as the planner); null ⇒ provider's default. */
    private String routerModel() {
        String p = ai.getProvider() == null ? "" : ai.getProvider().toLowerCase();
        return switch (p) {
            case "claude", "anthropic" -> ai.getPlannerModelClaude();
            case "openai" -> ai.getPlannerModelOpenai();
            default -> null;
        };
    }
}
