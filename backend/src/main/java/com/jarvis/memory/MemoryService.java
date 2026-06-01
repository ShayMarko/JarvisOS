package com.jarvis.memory;

import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jarvis.audit.AuditService;
import com.jarvis.error.Exceptions.NotFoundException;

/**
 * The Memory Manager (spec §10.1). The user controls everything: view, add,
 * edit, delete, search and export. Every mutation is audited.
 */
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryRepository repository;
    private final AuditService audit;


    @Transactional(readOnly = true)
    public List<Memory> list(String query) {
        List<Memory> all = repository.findAllByOrderByUpdatedAtDesc();
        if (query == null || query.isBlank()) {
            return all;
        }
        String needle = query.toLowerCase();
        return all.stream().filter(m -> contains(m, needle)).toList();
    }

    @Transactional(readOnly = true)
    public Memory get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No memory with id " + id));
    }

    @Transactional(readOnly = true)
    public List<Memory> exportAll() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional
    public Memory create(MemoryDraft draft) {
        Instant now = Instant.now();
        Memory m = new Memory();
        m.setId("mem_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        m.setCategory(draft.category());
        m.setTitle(draft.title());
        m.setContent(draft.content());
        m.setSource(draft.source() != null ? draft.source() : "manual");
        m.setConfidence(draft.confidence() != null ? draft.confidence() : 1.0);
        m.setVisibility(draft.visibility() != null ? draft.visibility() : Visibility.USER_VISIBLE);
        m.setSensitivity(draft.sensitivity() != null ? draft.sensitivity() : Sensitivity.NORMAL);
        m.setExpiresAt(draft.expiresAt());
        m.setEnabled(draft.enabled() == null || draft.enabled());
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        Memory saved = repository.save(m);
        audit.record("MEMORY", "memory:create", saved.getTitle(), "OK", "id=" + saved.getId());
        return saved;
    }

    @Transactional
    public Memory update(String id, MemoryDraft draft) {
        Memory m = get(id);
        if (draft.category() != null) m.setCategory(draft.category());
        if (draft.title() != null) m.setTitle(draft.title());
        if (draft.content() != null) m.setContent(draft.content());
        if (draft.source() != null) m.setSource(draft.source());
        if (draft.confidence() != null) m.setConfidence(draft.confidence());
        if (draft.visibility() != null) m.setVisibility(draft.visibility());
        if (draft.sensitivity() != null) m.setSensitivity(draft.sensitivity());
        if (draft.expiresAt() != null) m.setExpiresAt(draft.expiresAt());
        if (draft.enabled() != null) m.setEnabled(draft.enabled());
        m.setUpdatedAt(Instant.now());
        Memory saved = repository.save(m);
        audit.record("MEMORY", "memory:update", saved.getTitle(), "OK", "id=" + saved.getId());
        return saved;
    }

    @Transactional
    public void delete(String id) {
        Memory m = get(id);
        repository.delete(m);
        audit.record("MEMORY", "memory:delete", m.getTitle(), "OK", "id=" + id);
    }

    private boolean contains(Memory m, String needle) {
        return lower(m.getTitle()).contains(needle)
                || lower(m.getContent()).contains(needle)
                || lower(m.getCategory()).contains(needle)
                || lower(m.getSource()).contains(needle);
    }

    private String lower(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
