package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.plugin.PluginRegistry;

/** Plugin / Extension surface (spec §8). */
@RestController
@RequestMapping("/api/plugins")
@RequiredArgsConstructor
public class PluginController {

    private final PluginRegistry plugins;


    @GetMapping
    public Map<String, Object> surface() {
        return plugins.surface();
    }
}
