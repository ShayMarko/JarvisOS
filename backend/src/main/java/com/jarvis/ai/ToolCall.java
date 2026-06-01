package com.jarvis.ai;

/** A request from the model to run a tool with JSON arguments. */
public record ToolCall(String id, String name, String argumentsJson) {}
