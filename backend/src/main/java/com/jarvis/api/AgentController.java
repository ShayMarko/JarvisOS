package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.agent.AgentDefinition;
import com.jarvis.agent.AgentRegistry;

/** Lists the registered agents (spec §7). */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRegistry registry;


    @GetMapping
    public List<AgentDefinition> agents() {
        return registry.all();
    }
}
