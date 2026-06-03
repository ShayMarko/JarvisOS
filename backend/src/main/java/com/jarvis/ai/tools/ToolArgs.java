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

    /**
     * Returns the first non-blank value among several candidate keys. Weaker models don't always use
     * the exact argument name from the schema (they'll send "file"/"filename" instead of "path", or
     * "text"/"code" instead of "content"), which would otherwise make the tool fail with an empty
     * value. Accepting these synonyms makes tool calls robust without hard-coding any task logic.
     */
    static String firstStr(ObjectMapper mapper, String json, String... keys) {
        try {
            var node = mapper.readTree(json == null || json.isBlank() ? "{}" : json);
            for (String key : keys) {
                String v = node.path(key).asText("");
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }
}
