package com.jarvis.brain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * A tiny exact-prompt answer cache (LRU + short TTL). When the user asks the SAME timeless question
 * again, Jarvis returns the prior answer instantly instead of re-running the model — saving a slow
 * local generation (and paid tokens on cloud providers).
 *
 * <p>Deliberately conservative so it can never serve a wrong answer in a conversation:
 * <ul>
 *   <li>Only the Orchestrator stores here, and ONLY for answers produced with NO tools (pure model
 *       knowledge — "explain recursion"), never tool/web/file results that can be time-sensitive.</li>
 *   <li>Context-dependent phrasings ("again", "it", "that", "previous"…) are not cached.</li>
 *   <li>Entries expire after a few minutes, so anything that does change goes stale quickly.</li>
 * </ul>
 */
@Component
public class ResponseCache {

    private static final long TTL_MS = 5 * 60 * 1000;   // 5 minutes
    private static final int MAX_ENTRIES = 200;
    private static final int MIN_LEN = 8;

    private record CacheEntry(String answer, long storedAt, long expiresAt) {}

    /** A cache hit: the stored answer plus how old it is (so the user can be told it isn't live). */
    public record Hit(String answer, long ageSeconds) {}

    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /** Cache hit (answer + age) for this message, or null. */
    public synchronized Hit lookup(String message, long now) {
        String key = key(message);
        if (key == null) {
            return null;
        }
        CacheEntry e = cache.get(key);
        if (e == null) {
            return null;
        }
        if (e.expiresAt() < now) {
            cache.remove(key);
            return null;
        }
        return new Hit(e.answer(), Math.max(0, (now - e.storedAt()) / 1000));
    }

    /** Store a cacheable answer (caller decides cacheability — e.g. no tools were used). */
    public synchronized void put(String message, String answer, long now) {
        String key = key(message);
        if (key == null || answer == null || answer.isBlank()) {
            return;
        }
        cache.put(key, new CacheEntry(answer, now, now + TTL_MS));
    }

    /** Normalized cache key, or null if this message shouldn't be cached. */
    private static String key(String message) {
        if (message == null) {
            return null;
        }
        String m = message.strip().toLowerCase(Locale.ROOT);
        if (m.length() < MIN_LEN || isContextDependent(m)) {
            return null;
        }
        return m.replaceAll("\\s+", " ");
    }

    /** Phrasings whose meaning depends on prior turns — never safe to answer from a global cache. */
    private static boolean isContextDependent(String m) {
        return m.matches(".*\\b(it|that|this|those|these|again|previous|last|above|continue|more)\\b.*");
    }
}
