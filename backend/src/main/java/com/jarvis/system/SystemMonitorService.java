package com.jarvis.system;

import lombok.RequiredArgsConstructor;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.jarvis.agent.AgentRegistry;
import com.jarvis.connectors.ConnectorRegistry;
import com.jarvis.task.TaskService;
import com.sun.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;

/**
 * The System Monitor (spec §13). Reports CPU, memory, swap, disk, JVM and
 * process metrics available from the JVM without native hooks. Per-process,
 * GPU and local-model load are surfaced as placeholders until later phases /
 * a sidecar can provide them; agent and task counts are wired in Phase 6.
 */
@Service
@RequiredArgsConstructor
public class SystemMonitorService {

    private final TaskService tasks;
    private final AgentRegistry agents;
    private final ConnectorRegistry connectors;


    public Map<String, Object> snapshot() {
        OperatingSystemMXBean os =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Runtime runtime = Runtime.getRuntime();

        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("availableProcessors", os.getAvailableProcessors());
        cpu.put("systemLoadAverage", round(os.getSystemLoadAverage()));
        cpu.put("processCpuLoad", round(os.getProcessCpuLoad()));
        cpu.put("systemCpuLoad", round(os.getCpuLoad()));

        long totalPhysical = os.getTotalMemorySize();
        long freePhysical = os.getFreeMemorySize();
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("totalPhysicalBytes", totalPhysical);
        memory.put("freePhysicalBytes", freePhysical);
        memory.put("usedPhysicalBytes", totalPhysical - freePhysical);
        memory.put("jvmMaxBytes", runtime.maxMemory());
        memory.put("jvmTotalBytes", runtime.totalMemory());
        memory.put("jvmUsedBytes", runtime.totalMemory() - runtime.freeMemory());

        long totalSwap = os.getTotalSwapSpaceSize();
        Map<String, Object> swap = new LinkedHashMap<>();
        swap.put("totalBytes", totalSwap);
        swap.put("freeBytes", os.getFreeSwapSpaceSize());
        swap.put("usedBytes", totalSwap - os.getFreeSwapSpaceSize());

        File rootDisk = new File(".").getAbsoluteFile();
        Map<String, Object> disk = new LinkedHashMap<>();
        disk.put("totalBytes", rootDisk.getTotalSpace());
        disk.put("freeBytes", rootDisk.getFreeSpace());
        disk.put("usableBytes", rootDisk.getUsableSpace());

        Map<String, Object> process = new LinkedHashMap<>();
        process.put("committedVirtualBytes", os.getCommittedVirtualMemorySize());
        process.put("threads", ManagementFactory.getThreadMXBean().getThreadCount());
        if (os instanceof UnixOperatingSystemMXBean unix) {
            process.put("openFileDescriptors", unix.getOpenFileDescriptorCount());
            process.put("maxFileDescriptors", unix.getMaxFileDescriptorCount());
        }

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("uptimeMillis", ManagementFactory.getRuntimeMXBean().getUptime());

        long running = tasks.activeCount();
        Map<String, Object> runtimeStatus = new LinkedHashMap<>();
        runtimeStatus.put("activeAgents", running);          // an agent runs per in-flight task
        runtimeStatus.put("runningTasks", running);
        runtimeStatus.put("registeredAgents", agents.all().size());
        runtimeStatus.put("connectorHealth", connectors.healthSummary());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("os", System.getProperty("os.name"));
        snapshot.put("cpu", cpu);
        snapshot.put("memory", memory);
        snapshot.put("swap", swap);
        snapshot.put("disk", disk);
        snapshot.put("process", process);
        snapshot.put("jvm", jvm);
        snapshot.put("runtime", runtimeStatus);
        snapshot.put("gpu", null);             // no GPU telemetry yet
        snapshot.put("localModelLoad", null);  // no local model yet (Phase 10)
        snapshot.put("jarvisHealth", "OK");
        return snapshot;
    }

    private static double round(double value) {
        if (value < 0) {
            return -1; // metric not available on this platform yet
        }
        return Math.round(value * 1000.0) / 1000.0;
    }
}
