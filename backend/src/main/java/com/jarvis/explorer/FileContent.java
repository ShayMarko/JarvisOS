package com.jarvis.explorer;

/** The text content of a file in the Jarvis Explorer, for the internal viewer/editor. */
public record FileContent(String path, String content) {}
