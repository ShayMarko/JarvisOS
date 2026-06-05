package com.jarvis.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.brain.ChatResponse;
import com.jarvis.brain.Orchestrator;

import jakarta.validation.constraints.NotBlank;

/** Direct chat entry point to the Jarvis Brain (spec §6), blocking and streaming. */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final Orchestrator orchestrator;
    private final ObjectMapper mapper;
    // Bounded pool: each SSE stream holds a thread for up to the emitter timeout while the
    // agent loop makes blocking model calls, so an unbounded pool could exhaust threads.
    private final ExecutorService exec =
            Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));

    public record ChatRequest(@NotBlank String message, String sessionId) {}

    @PostMapping
    public ChatResponse chat(@jakarta.validation.Valid @RequestBody ChatRequest request) {
        return orchestrator.handle(request.message(),
                request.sessionId() == null ? "default" : request.sessionId());
    }

    /**
     * Streaming run: emits a {@code step} event for each step as it happens, then
     * a final {@code answer} event, then {@code done}. Consumed by the HUD via
     * EventSource so the agent's thinking shows live.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String message, @RequestParam(required = false) String sessionId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        exec.submit(() -> {
            try {
                ChatResponse resp = orchestrator.handle(message, sessionId == null ? "default" : sessionId, step -> {
                    try {
                        emitter.send(SseEmitter.event().name("step").data(mapper.writeValueAsString(step), MediaType.APPLICATION_JSON));
                    } catch (Exception ignored) {
                        // client disconnected; the run still completes server-side
                    }
                });
                emitter.send(SseEmitter.event().name("answer").data(mapper.writeValueAsString(resp), MediaType.APPLICATION_JSON));
                emitter.send(SseEmitter.event().name("done").data("{}", MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                log.warn("Chat stream failed: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data("{\"message\":\"" + safe(e.getMessage()) + "\"}", MediaType.APPLICATION_JSON));
                } catch (Exception ignored) { /* already gone */ }
                emitter.complete();
            }
        });
        return emitter;
    }

    private static String safe(String s) {
        return s == null ? "error" : s.replace("\"", "'").replace("\n", " ");
    }
}
