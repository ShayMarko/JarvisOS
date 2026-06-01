package com.jarvis.ai;

/**
 * The advertised interface of a tool the model may call: a name, a description,
 * and a JSON-schema string describing its parameters.
 */
public record ToolSpec(String name, String description, String parametersSchema) {}
