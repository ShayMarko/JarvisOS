package com.jarvis.common;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * Anchors relative time references in free text to the date they were written, so a memory like
 * "started about a month ago" stays meaningful months later — it becomes
 * "started about a month ago (as of 2026-06-14)", which any reader can resolve to a real date.
 *
 * <p>Deterministic and model-agnostic: applied when a memory is saved, so it works the same whether
 * the brain runs on Claude or a local model. It only stamps text that actually contains a relative
 * reference and doesn't already carry an explicit year or an "(as of …)" anchor.
 */
public final class RelativeDates {

    private RelativeDates() {}

    /** Phrases that are meaningless without knowing when they were said. */
    private static final Pattern RELATIVE = Pattern.compile(
            "\\b(ago|yesterday|today|tonight|tomorrow|recently|currently|"
            + "this\\s+(morning|afternoon|evening|week|month|year)|"
            + "last\\s+(night|week|month|year)|next\\s+(week|month|year)|"
            + "(a|one|two|three|few|several|couple)\\s+(day|days|week|weeks|month|months|year|years)\\s+ago|"
            + "\\d+\\s+(day|days|week|weeks|month|months|year|years)\\s+ago)\\b",
            Pattern.CASE_INSENSITIVE);

    /** A 4-digit year already present → treat the text as already absolute. */
    private static final Pattern HAS_YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");

    public static String anchor(String content) {
        return anchor(content, LocalDate.now(ZoneId.systemDefault()));
    }

    /** Visible for testing — anchor against an explicit "today". */
    public static String anchor(String content, LocalDate today) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (content.contains("(as of ") || HAS_YEAR.matcher(content).find()) {
            return content;   // already dated — don't double-stamp
        }
        if (!RELATIVE.matcher(content).find()) {
            return content;   // nothing relative to anchor
        }
        return content + " (as of " + today + ")";
    }
}
