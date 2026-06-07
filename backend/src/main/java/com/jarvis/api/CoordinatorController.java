package com.jarvis.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.coordinator.CoordinatorService;

import lombok.RequiredArgsConstructor;

/**
 * The autonomous Coordinator API. GET reports the goal + whether the loop is armed; POST /tick runs one
 * coordination pass on demand (pick the next funnel action and dispatch it) — handy for headless triggers
 * and for trying it before arming the cron.
 */
@RestController
@RequestMapping("/api/coordinator")
@RequiredArgsConstructor
public class CoordinatorController {

    private final CoordinatorService coordinator;

    @GetMapping
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", coordinator.isEnabled());
        out.put("goal", coordinator.goal());
        out.put("portfolioSize", coordinator.portfolioSize());
        return out;
    }

    @PostMapping("/tick")
    public Map<String, Object> tick() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", coordinator.tick());
        return out;
    }
}
