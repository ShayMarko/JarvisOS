package com.jarvis.connectors;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base for connectors that talk to a JSON HTTP API. Holds the shared {@link ObjectMapper} and the two
 * helpers every such connector was hand-rolling: a lenient response parser ({@link #read}) and a URL-encoder
 * ({@link #enc}). Subclasses still own their own {@code RestClient} + auth scheme (bearer / api-key / custom
 * header), since those genuinely differ — but the boilerplate no longer gets copy-pasted per connector.
 */
public abstract class AbstractRestConnector implements Connector {

    protected final ObjectMapper mapper;

    protected AbstractRestConnector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Parse a JSON response body into a tree; never throws — returns an empty object on null/garbage. */
    protected JsonNode read(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    /** URL-encode a query/form value (UTF-8). */
    protected static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
