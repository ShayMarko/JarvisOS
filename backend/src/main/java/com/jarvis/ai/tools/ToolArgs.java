package com.jarvis.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.Json;

/** Small helper for reading fields out of a tool's JSON arguments (null/blank-tolerant, never throws). */
final class ToolArgs {

    private ToolArgs() {}

    /** Parse the arguments into a JSON tree, tolerating null/blank/garbled input (returns an empty object). */
    static JsonNode root(ObjectMapper mapper, String json) {
        return Json.read(mapper, json);
    }

    static String str(ObjectMapper mapper, String json, String key) {
        return root(mapper, json).path(key).asText("");
    }

    static int intVal(ObjectMapper mapper, String json, String key, int fallback) {
        return root(mapper, json).path(key).asInt(fallback);
    }

    static double doubleVal(ObjectMapper mapper, String json, String key, double fallback) {
        return root(mapper, json).path(key).asDouble(fallback);
    }

    /**
     * Returns the first non-blank value among several candidate keys. Weaker models don't always use
     * the exact argument name from the schema (they'll send "file"/"filename" instead of "path", or
     * "text"/"code" instead of "content"), which would otherwise make the tool fail with an empty
     * value. Accepting these synonyms makes tool calls robust without hard-coding any task logic.
     */
    static String firstStr(ObjectMapper mapper, String json, String... keys) {
        JsonNode node = root(mapper, json);
        for (String key : keys) {
            String v = node.path(key).asText("");
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
