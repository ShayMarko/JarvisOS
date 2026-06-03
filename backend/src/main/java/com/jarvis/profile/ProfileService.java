package com.jarvis.profile;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jarvis.audit.AuditService;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/**
 * The user's "About Me" profile — a single editable Markdown doc ({@code about-me.md} at
 * the Jarvis root) that BOTH the user and Jarvis can read and update. The user edits it in
 * the Profile window (or the Files explorer); Jarvis reads it into every conversation
 * (see {@code ContextBuilder}) and can append durable facts via the {@code update_profile}
 * tool. Distinct from the Memory store (discrete searchable facts) — this is the one
 * authoritative "who I am" document.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    static final String FILE = "about-me.md";
    private static final String LEARNED_HEADER = "## What Jarvis has learned";
    /** Cap for the always-injected identity block — enough for who-they-are, lean on tokens. */
    private static final int IDENTITY_MAX_CHARS = 900;

    private static final String TEMPLATE = """
            # About Me

            _This is your profile. Both you and Jarvis can read and edit it. Jarvis reads it at
            the start of every conversation, so keep it current. Edit freely._

            ## Identity
            - Name:
            - Role / what I do:
            - Location / timezone:

            ## Preferences
            - How I like Jarvis to talk to me:
            - Tools / apps I use:
            - Things to always do / never do:

            ## Projects & focus
            -

            ## Goals
            -

            """ + LEARNED_HEADER + "\n";

    private final FileSystemService fs;
    private final AuditService audit;

    /** Current profile text; seeds the template on first read if the file doesn't exist yet. */
    public String read() {
        try {
            return fs.readText(FILE).content();
        } catch (NotFoundException e) {
            fs.writeText(FILE, TEMPLATE);
            return TEMPLATE;
        }
    }

    /** Replace the whole profile (the user's Save in the Profile window / Files editor). */
    public String write(String content) {
        fs.writeText(FILE, content == null ? "" : content);
        audit.record("PROFILE", "profile:write", FILE, "OK", null);
        return read();
    }

    /**
     * Return the paragraphs of the profile most relevant to a query, so the agent can pull
     * just the bits it needs (token-cheap) instead of the whole — possibly long — profile.
     * Lightweight keyword overlap over blank-line-separated paragraphs; no embeddings needed.
     */
    public String search(String query, int maxChars) {
        String content = read();
        String q = query == null ? "" : query.toLowerCase();
        Set<String> terms = Arrays.stream(q.split("[^a-z0-9]+"))
                .filter(t -> t.length() > 2).collect(Collectors.toSet());
        // Split into paragraphs (blank-line separated), keeping section headers with their text.
        String[] paras = content.split("\\n\\s*\\n");
        if (terms.isEmpty()) {
            // No usable query terms → return the top of the profile (identity/preferences).
            return clip(content, maxChars);
        }
        List<String> ranked = Arrays.stream(paras)
                .map(String::strip)
                .filter(p -> !p.isBlank())
                .sorted(Comparator.comparingInt((String p) -> -score(p, terms)))
                .filter(p -> score(p, terms) > 0)
                .limit(6)
                .toList();
        if (ranked.isEmpty()) {
            // Poor/own query terms didn't match — return the top of the profile (identity/
            // preferences) rather than nothing, so personal questions still get answered.
            return clip(content, maxChars);
        }
        return clip(String.join("\n\n", ranked), maxChars);
    }

    /**
     * A compact identity block — the Identity + Snapshot sections — small enough to inject into EVERY
     * conversation so Jarvis always knows who it's talking to (name, location, languages), without
     * shipping the whole long (and partly sensitive) profile each turn. Deeper details stay on-demand
     * via {@code profile_search}. Returns "" if there's no usable profile yet.
     */
    public String compactIdentity() {
        String content = read();
        if (content == null || content.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendSection(sb, content, "Identity");   // name first — the thing identity questions need
        appendSection(sb, content, "Snapshot");
        String out = sb.toString().strip();
        if (out.isBlank()) {
            out = content.strip();   // unconventional profile → fall back to the top of it
        }
        return clip(out, IDENTITY_MAX_CHARS);
    }

    private static void appendSection(StringBuilder sb, String content, String header) {
        String body = section(content, header);
        if (!body.isBlank()) {
            sb.append("## ").append(header).append('\n').append(body).append("\n\n");
        }
    }

    /** The text under a {@code ## <header>} heading, up to the next {@code ## } (or end). "" if absent. */
    private static String section(String content, String header) {
        String marker = "## " + header;
        int start = content.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int from = start + marker.length();
        int next = content.indexOf("\n## ", from);
        return (next < 0 ? content.substring(from) : content.substring(from, next)).strip();
    }

    private static int score(String paragraph, Set<String> terms) {
        String p = paragraph.toLowerCase();
        int s = 0;
        for (String t : terms) {
            if (p.contains(t)) {
                s++;
            }
        }
        return s;
    }

    private static String clip(String s, int maxChars) {
        return s.length() <= maxChars ? s : s.substring(0, maxChars) + "\n…(truncated)";
    }

    /** Append a durable fact under the "What Jarvis has learned" section (the agent's write path). */
    public String appendLearned(String fact) {
        String f = fact == null ? "" : fact.strip();
        if (f.isEmpty()) {
            return read();
        }
        String current = read();
        StringBuilder sb = new StringBuilder(current);
        if (!current.contains(LEARNED_HEADER)) {
            sb.append("\n").append(LEARNED_HEADER).append("\n");
        }
        if (sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append("- ").append(f).append('\n');
        fs.writeText(FILE, sb.toString());
        audit.record("PROFILE", "profile:learn", f, "OK", null);
        return sb.toString();
    }
}
