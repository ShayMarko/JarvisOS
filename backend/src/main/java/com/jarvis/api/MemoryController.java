package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.memory.Memory;
import com.jarvis.memory.MemoryDraft;
import com.jarvis.memory.MemoryService;
import com.jarvis.memory.Sensitivity;
import com.jarvis.memory.Visibility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/** Memory Manager endpoints — full user control over stored memory (spec §10.1). */
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memory;


    public record CreateRequest(
            @NotBlank String category,
            @NotBlank String title,
            @NotBlank String content,
            String source,
            @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
            Visibility visibility,
            Sensitivity sensitivity,
            Instant expiresAt,
            Boolean enabled) {

        MemoryDraft toDraft() {
            return new MemoryDraft(category, title, content, source, confidence,
                    visibility, sensitivity, expiresAt, enabled);
        }
    }

    public record UpdateRequest(
            String category,
            String title,
            String content,
            String source,
            @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
            Visibility visibility,
            Sensitivity sensitivity,
            Instant expiresAt,
            Boolean enabled) {

        MemoryDraft toDraft() {
            return new MemoryDraft(category, title, content, source, confidence,
                    visibility, sensitivity, expiresAt, enabled);
        }
    }

    @GetMapping
    public List<Memory> list(@RequestParam(name = "q", defaultValue = "") String query) {
        return memory.list(query);
    }

    @GetMapping("/export")
    public List<Memory> export() {
        return memory.exportAll();
    }

    @GetMapping("/{id}")
    public Memory get(@PathVariable String id) {
        return memory.get(id);
    }

    @PostMapping
    public ResponseEntity<Memory> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memory.create(request.toDraft()));
    }

    @PutMapping("/{id}")
    public Memory update(@PathVariable String id, @Valid @RequestBody UpdateRequest request) {
        return memory.update(id, request.toDraft());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        memory.delete(id);
        return ResponseEntity.noContent().build();
    }
}
