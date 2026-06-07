package com.jarvis.agent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Loads agent definitions from markdown files (CLAUDE.md-style): YAML-ish frontmatter
 * ({@code slug/name/role/category/tools}) plus the prompt body. So agents are editable CONTENT, not Java
 * string literals — tweak a prompt in a text editor, no recompile.
 *
 * <p>Sources: the bundled defaults on the classpath ({@code agents/*.md}), then — if the
 * {@code JARVIS_AGENTS_DIR} env var points at a real folder — any {@code *.md} there OVERRIDE the defaults
 * by slug (the no-recompile path: drop an edited file in that dir). Fail-fast: a malformed file throws at
 * startup with a clear message rather than silently dropping an agent.
 */
public class AgentDefinitionLoader {

    public List<AgentDefinition> load() {
        Map<String, AgentDefinition> bySlug = new LinkedHashMap<>();
        for (AgentDefinition a : loadClasspath()) {
            bySlug.put(a.slug(), a);
        }
        for (AgentDefinition a : loadOverrideDir()) {
            bySlug.put(a.slug(), a);   // external files win
        }
        if (bySlug.isEmpty()) {
            throw new IllegalStateException("No agent definitions found on classpath (agents/*.md). "
                    + "The roster cannot be empty.");
        }
        List<AgentDefinition> out = new ArrayList<>(bySlug.values());
        // Stable, pleasant order for listings: 'general' first, then by category, then name.
        out.sort(Comparator.<AgentDefinition, Boolean>comparing(a -> !"general".equals(a.slug()))
                .thenComparing(AgentDefinition::category)
                .thenComparing(AgentDefinition::name));
        return List.copyOf(out);
    }

    private List<AgentDefinition> loadClasspath() {
        List<AgentDefinition> list = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:agents/*.md");
            for (Resource r : resources) {
                try (var in = r.getInputStream()) {
                    String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    list.add(parse(raw, r.getFilename()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load bundled agent definitions", e);
        }
        return list;
    }

    private List<AgentDefinition> loadOverrideDir() {
        String dir = System.getenv("JARVIS_AGENTS_DIR");
        if (dir == null || dir.isBlank()) {
            return List.of();
        }
        Path base = Path.of(dir);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        List<AgentDefinition> list = new ArrayList<>();
        try (Stream<Path> files = Files.list(base)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".md")).toList()) {
                list.add(parse(Files.readString(p), p.getFileName().toString()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JARVIS_AGENTS_DIR=" + dir, e);
        }
        return list;
    }

    /** Parse one markdown agent file into a definition. Fail-fast on missing required fields. */
    static AgentDefinition parse(String raw, String filename) {
        String text = raw.replace("\r\n", "\n").strip();
        if (!text.startsWith("---")) {
            throw new IllegalStateException("Agent file " + filename + " must start with a '---' frontmatter block.");
        }
        int close = text.indexOf("\n---", 3);
        if (close < 0) {
            throw new IllegalStateException("Agent file " + filename + " has no closing '---' for its frontmatter.");
        }
        String front = text.substring(3, close).strip();
        int afterFence = text.indexOf('\n', close + 1);   // newline after the closing '---' line
        String body = afterFence < 0 ? "" : text.substring(afterFence + 1).strip();

        Map<String, String> fm = new LinkedHashMap<>();
        for (String line : front.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            fm.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        String slug = req(fm, "slug", filename);
        String name = unquote(req(fm, "name", filename));
        String role = unquote(fm.getOrDefault("role", "\"\""));
        String category = fm.getOrDefault("category", "general").trim();
        List<String> tools = parseTools(fm.getOrDefault("tools", "[]"));
        List<String> keywords = parseQuoted(fm.getOrDefault("keywords", ""));
        int routePriority = parsePriority(fm.get("routePriority"));
        if (body.isBlank()) {
            throw new IllegalStateException("Agent file " + filename + " has an empty prompt body.");
        }
        return new AgentDefinition(name, slug, role, body, tools, category, keywords, routePriority);
    }

    /** Extract routing keywords from a quoted array, e.g. {@code ["cpu", "system status", "class "]}.
     *  Quoted so exact strings — including significant trailing spaces — survive verbatim. */
    private static List<String> parseQuoted(String v) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(v);
        while (m.find()) {
            out.add(m.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return List.copyOf(out);
    }

    private static int parsePriority(String v) {
        if (v == null || v.isBlank()) {
            return AgentDefinition.DEFAULT_PRIORITY;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return AgentDefinition.DEFAULT_PRIORITY;
        }
    }

    private static String req(Map<String, String> fm, String key, String filename) {
        String v = fm.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Agent file " + filename + " is missing required frontmatter '" + key + "'.");
        }
        return v;
    }

    private static List<String> parseTools(String v) {
        String s = v.trim();
        if (s.startsWith("[")) {
            s = s.substring(1);
        }
        if (s.endsWith("]")) {
            s = s.substring(0, s.length() - 1);
        }
        List<String> out = new ArrayList<>();
        for (String t : s.split(",")) {
            String tool = t.trim();
            if (!tool.isEmpty()) {
                out.add(tool);
            }
        }
        return List.copyOf(out);
    }

    private static String unquote(String v) {
        String s = v.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }
}
