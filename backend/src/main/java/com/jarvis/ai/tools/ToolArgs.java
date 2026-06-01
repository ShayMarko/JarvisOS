package com.jarvis.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Small helper for reading a string field out of a tool's JSON arguments. */
final class ToolArgs {

    private ToolArgs() {}

    static String str(ObjectMapper mapper, String json, String key) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json).path(key).asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
