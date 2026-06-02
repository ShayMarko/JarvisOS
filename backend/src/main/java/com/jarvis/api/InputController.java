package com.jarvis.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.command.CommandEngine;
import com.jarvis.command.CommandResult;

import lombok.RequiredArgsConstructor;

/**
 * The single cognitive door (spec §4 "Input Router"). All user input — slash
 * command or free-form request — enters here; {@link CommandEngine} routes it
 * server-side (deterministic command vs Brain) and this endpoint streams the
 * outcome: live {@code step} events for an AI request, then a final {@code result}.
 *
 * <p>The REST resource endpoints (/api/files, /api/memory, …) stay separate —
 * they're data plumbing for the UI, not the thinking path.
 */
@RestController
@RequestMapping("/api/input")
@RequiredArgsConstructor
public class InputController {

    private static final Logger log = LoggerFactory.getLogger(InputController.class);

    private final CommandEngine engine;
    private final ObjectMapper mapper;
    // Bounded so a burst of streams can't spawn unlimited threads (each stream holds one).
    private final ExecutorService exec = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()));

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String input, @RequestParam(required = false) String sessionId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        String session = sessionId == null || sessionId.isBlank() ? "default" : sessionId;
        exec.submit(() -> {
            try {
                CommandResult result = engine.execute(input, session, step -> {
                    try {
                        emitter.send(SseEmitter.event().name("step")
                                .data(mapper.writeValueAsString(step), MediaType.APPLICATION_JSON));
                    } catch (Exception ignored) {
                        // client disconnected; the run still completes server-side
                    }
                });
                emitter.send(SseEmitter.event().name("result")
                        .data(mapper.writeValueAsString(result), MediaType.APPLICATION_JSON));
                emitter.send(SseEmitter.event().name("done").data("{}", MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                log.warn("Input stream failed: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("{\"message\":\"" + safe(e.getMessage()) + "\"}", MediaType.APPLICATION_JSON));
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
