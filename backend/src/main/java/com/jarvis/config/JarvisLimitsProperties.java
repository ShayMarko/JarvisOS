package com.jarvis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunable limits/sizes, bound from the {@code jarvis.limits} block of
 * application.yml. Centralised here (rather than scattered as in-code constants)
 * so they're easy to find and adjust as the system grows.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.limits")
public class JarvisLimitsProperties {

    /** How many prior chat turns the Brain replays for conversation continuity. */
    private int conversationHistoryTurns = 10;

    /** Largest file (bytes) the internal text viewer/editor will load. */
    private long fileMaxTextBytes = 2 * 1024 * 1024;

    /** Max results returned by an Explorer name search. */
    private int fileSearchMaxResults = 200;

    /** Knowledge Base chunking: characters per chunk and overlap between chunks. */
    private int kbChunkSize = 1000;
    private int kbChunkOverlap = 150;

    /** Max files indexed when pointing the KB at a folder. */
    private int kbMaxFilesPerFolder = 50;

    /** Default number of passages a KB search returns. */
    private int kbSearchTopK = 5;

    /** Sandbox command timeout (seconds) and captured-output cap (chars). */
    private int sandboxTimeoutSeconds = 15;
    private int sandboxMaxOutputChars = 100_000;

    /** When true (and Docker is installed), run sandbox commands inside an isolated,
     *  network-less container; otherwise fall back to a throwaway temp-dir process. */
    private boolean sandboxDocker = false;
    private String sandboxImage = "alpine:3";

    /** fetch_url: response cap (chars) and request timeout (seconds). */
    private int webFetchMaxChars = 2000;
    private int webFetchTimeoutSeconds = 12;

    /** Trailing characters of a secret kept for masked display (e.g. ••••1234). */
    private int secretHintLength = 4;
}
