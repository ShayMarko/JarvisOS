package com.jarvis.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jarvis.agent.AgentRegistry;
import com.jarvis.connectors.ConnectorRegistry;
import com.jarvis.task.TaskService;

class SystemMonitorTest {

    private final SystemMonitorService monitor =
            new SystemMonitorService(mock(TaskService.class), new AgentRegistry(), mock(ConnectorRegistry.class));

    @Test
    @SuppressWarnings("unchecked")
    void snapshotExposesCoreSections() {
        Map<String, Object> snap = monitor.snapshot();
        assertThat(snap).containsKeys("os", "cpu", "memory", "swap", "disk", "process", "jvm", "runtime", "jarvisHealth");

        Map<String, Object> cpu = (Map<String, Object>) snap.get("cpu");
        assertThat((int) cpu.get("availableProcessors")).isPositive();

        Map<String, Object> memory = (Map<String, Object>) snap.get("memory");
        assertThat((long) memory.get("totalPhysicalBytes")).isPositive();

        Map<String, Object> runtime = (Map<String, Object>) snap.get("runtime");
        assertThat(runtime).containsKeys("activeAgents", "runningTasks");
    }

    @Test
    void streamSkipsBroadcastWithNoSubscribers() {
        MonitorStreamService stream = new MonitorStreamService(monitor);
        assertThat(stream.subscriberCount()).isZero();
        // No subscribers: broadcast must be a harmless no-op.
        stream.broadcast();
        assertThat(stream.subscriberCount()).isZero();
    }
}
