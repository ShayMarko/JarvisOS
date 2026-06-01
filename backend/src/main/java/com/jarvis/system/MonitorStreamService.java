package com.jarvis.system;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes live System Monitor snapshots to subscribed clients over SSE
 * (spec §13). A single scheduled task samples the metrics and broadcasts to
 * all open emitters; it does nothing while there are no subscribers, so we
 * never sample for an audience of zero.
 */
@Service
public class MonitorStreamService {

    private static final Logger log = LoggerFactory.getLogger(MonitorStreamService.class);
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L; // 30 min; client auto-reconnects

    private final SystemMonitorService monitor;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public MonitorStreamService(SystemMonitorService monitor) {
        this.monitor = monitor;
    }

    /** Registers a new subscriber and immediately sends one snapshot. */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> { emitter.complete(); emitters.remove(emitter); });
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        send(emitter, monitor.snapshot()); // prime the stream so the client renders at once
        log.debug("Monitor subscriber added; {} active", emitters.size());
        return emitter;
    }

    public int subscriberCount() {
        return emitters.size();
    }

    @Scheduled(fixedRateString = "${jarvis.monitor.stream-interval-ms:2000}")
    void broadcast() {
        if (emitters.isEmpty()) {
            return; // no subscribers → no sampling
        }
        Map<String, Object> snapshot = monitor.snapshot();
        for (SseEmitter emitter : emitters) {
            send(emitter, snapshot);
        }
    }

    private void send(SseEmitter emitter, Map<String, Object> snapshot) {
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (IOException | IllegalStateException e) {
            // Client went away mid-send; drop it.
            emitters.remove(emitter);
        }
    }
}
