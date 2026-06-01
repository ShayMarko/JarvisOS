package com.jarvis.kb;

/** A ranked retrieval result with its source, for citations. */
public record SearchHit(String documentId, String title, String source, int ordinal, String content, double score) {}
