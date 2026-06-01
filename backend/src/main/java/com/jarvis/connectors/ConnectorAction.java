package com.jarvis.connectors;

/** An action a connector exposes (e.g. Gmail "list_recent"). */
public record ConnectorAction(String id, String name, String description) {}
