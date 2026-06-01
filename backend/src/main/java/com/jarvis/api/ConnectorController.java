package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.connectors.ConnectorInfo;
import com.jarvis.connectors.ConnectorRegistry;

/** Connectors / MCPs endpoints (spec §9). */
@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorRegistry registry;


    public record InvokeRequest(String args) {}

    public record InvokeResponse(String result) {}

    @GetMapping
    public List<ConnectorInfo> list() {
        return registry.list();
    }

    @PostMapping("/{id}/actions/{actionId}")
    public InvokeResponse invoke(@PathVariable String id, @PathVariable String actionId,
                                 @RequestBody(required = false) InvokeRequest body) {
        String args = body != null && body.args() != null ? body.args() : "{}";
        return new InvokeResponse(registry.invoke(id, actionId, args));
    }
}
