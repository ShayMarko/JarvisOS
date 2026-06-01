package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.audit.AuditLogEntry;
import com.jarvis.audit.AuditService;
import com.jarvis.system.SystemMonitorService;

/** System monitor + audit log endpoints (spec §13, §10.x). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {

    private final SystemMonitorService monitor;
    private final AuditService audit;


    @GetMapping("/status")
    public Map<String, Object> status() {
        return monitor.snapshot();
    }

    @GetMapping("/audit")
    public List<AuditLogEntry> audit(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return audit.recent(Math.min(limit, 500));
    }
}
