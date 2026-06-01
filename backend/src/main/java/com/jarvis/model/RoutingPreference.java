package com.jarvis.model;

/** How the Model Router trades off privacy, quality and cost (spec §6). */
public enum RoutingPreference {
    BALANCED,
    PRIVATE,  // local models only — nothing leaves the machine
    QUALITY,  // best quality regardless of cost
    CHEAP     // lowest cost
}
