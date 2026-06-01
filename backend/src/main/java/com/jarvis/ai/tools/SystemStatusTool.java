package com.jarvis.ai.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.system.SystemMonitorService;

import lombok.RequiredArgsConstructor;

/** Reports current CPU/memory/disk status. */
@Component
@RequiredArgsConstructor
public class SystemStatusTool implements Tool {

    private final SystemMonitorService monitor;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("system_status", "Get current CPU/memory/disk status.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(String args) {
        Map<String, Object> s = monitor.snapshot();
        Map<String, Object> cpu = (Map<String, Object>) s.get("cpu");
        Map<String, Object> mem = (Map<String, Object>) s.get("memory");
        long usedGb = (long) mem.get("usedPhysicalBytes") / (1024 * 1024 * 1024);
        long totalGb = (long) mem.get("totalPhysicalBytes") / (1024 * 1024 * 1024);
        return "OS: " + s.get("os")
                + "; CPU cores: " + cpu.get("availableProcessors")
                + "; system CPU load: " + cpu.get("systemCpuLoad")
                + "; RAM: " + usedGb + "/" + totalGb + " GB"
                + "; health: " + s.get("jarvisHealth");
    }
}
