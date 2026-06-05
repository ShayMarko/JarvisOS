package com.jarvis.system;

import lombok.RequiredArgsConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.jarvis.agent.AgentRegistry;
import com.jarvis.common.Numbers;
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

    // Network throughput is a rate: we remember the last cumulative byte counts
    // and the time we read them, then divide the delta by the elapsed seconds.
    private long lastRxBytes = -1;
    private long lastTxBytes = -1;
    private long lastSampleNanos = 0;


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
        snapshot.put("network", network());
        snapshot.put("gpu", null);             // no GPU telemetry yet
        snapshot.put("localModelLoad", null);  // no local model yet (Phase 10)
        snapshot.put("jarvisHealth", "OK");
        return snapshot;
    }

    /**
     * Network throughput: cumulative bytes in/out across physical interfaces plus a
     * per-second rate computed against the previous sample. Real numbers from macOS
     * {@code netstat -ib}; returns null on other platforms or if the tool is missing.
     */
    private synchronized Map<String, Object> network() {
        long[] totals = readNetTotals();   // {rxBytes, txBytes} or null
        if (totals == null) {
            return null;
        }
        long rx = totals[0];
        long tx = totals[1];
        long nowNanos = System.nanoTime();

        double rxPerSec = 0;
        double txPerSec = 0;
        if (lastRxBytes >= 0 && lastSampleNanos > 0) {
            double seconds = (nowNanos - lastSampleNanos) / 1_000_000_000.0;
            if (seconds > 0.05) {
                rxPerSec = Math.max(0, (rx - lastRxBytes) / seconds);
                txPerSec = Math.max(0, (tx - lastTxBytes) / seconds);
            }
        }
        lastRxBytes = rx;
        lastTxBytes = tx;
        lastSampleNanos = nowNanos;

        Map<String, Object> net = new LinkedHashMap<>();
        net.put("rxBytes", rx);
        net.put("txBytes", tx);
        net.put("rxBytesPerSec", Math.round(rxPerSec));
        net.put("txBytesPerSec", Math.round(txPerSec));
        return net;
    }

    /**
     * Sum ibytes/obytes across the physical {@code en*} interfaces (Wi-Fi + Ethernet)
     * from {@code netstat -ib}. We anchor on the {@code <Link#N>} token because the
     * Address column is blank on the Link row, so header column indices don't line up.
     * The fields after the token are either {@code [MAC] Ipkts Ierrs Ibytes Opkts Oerrs
     * Obytes Coll} (when the interface has a hardware address) or the same without the
     * MAC — so we detect the MAC (it contains ':') and offset accordingly. Counting only
     * {@code en*} avoids double-counting VPN/tunnel traffic that also rides the wire.
     */
    private long[] readNetTotals() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return null;
        }
        try {
            Process p = new ProcessBuilder("netstat", "-ib").redirectErrorStream(true).start();
            long rx = 0;
            long tx = 0;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] f = line.trim().split("\\s+");
                    if (f.length < 2 || !f[0].startsWith("en")) {
                        continue;       // header, blanks, non-physical interfaces
                    }
                    int link = -1;
                    for (int i = 0; i < f.length; i++) {
                        if (f[i].startsWith("<Link")) {
                            link = i;
                            break;
                        }
                    }
                    if (link < 0) {
                        continue;       // not the Link row (IPv4/IPv6 alias rows)
                    }
                    // Ipkts starts right after the Link token, skipping a MAC address if present.
                    int start = (link + 1 < f.length && f[link + 1].contains(":")) ? link + 2 : link + 1;
                    int ibytes = start + 2;
                    int obytes = start + 5;
                    if (f.length <= obytes) {
                        continue;
                    }
                    try {
                        rx += Long.parseLong(f[ibytes]);
                        tx += Long.parseLong(f[obytes]);
                    } catch (NumberFormatException ignored) {
                        // unexpected layout for this interface — skip it
                    }
                }
            }
            p.waitFor();
            return new long[] {rx, tx};
        } catch (Exception e) {
            return null;
        }
    }

    private static double round(double value) {
        if (value < 0) {
            return -1; // metric not available on this platform yet
        }
        return Numbers.round3(value);
    }
}
