package com.jarvis.connectors;

import java.util.List;

/** Public projection of a connector + its computed health, for the API/UI. */
public record ConnectorInfo(
        String id,
        String name,
        String category,
        String requiredSecret,
        ConnectorStatus status,
        List<ConnectorAction> actions
) {}
