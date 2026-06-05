package com.jarvis.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Parse raw JSON args into a tree, tolerating null/blank/garbled input (returns {} — never throws). */
public final class Json {

    private Json() {
    }

    public static JsonNode read(ObjectMapper mapper, String raw) {
        try {
            return mapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
