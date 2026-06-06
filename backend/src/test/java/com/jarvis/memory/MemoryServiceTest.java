package com.jarvis.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jarvis.audit.AuditService;
import com.jarvis.error.Exceptions.NotFoundException;

class MemoryServiceTest {

    private MemoryRepository repo;
    private MemoryService service;

    @BeforeEach
    void setUp() {
        repo = mock(MemoryRepository.class);
        service = new MemoryService(repo, mock(AuditService.class));
        when(repo.save(any(Memory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createAppliesDefaults() {
        Memory m = service.create(new MemoryDraft("preference", "Tables", "Likes tables",
                null, null, null, null, null, null));

        assertThat(m.getId()).startsWith("mem_");
        assertThat(m.getConfidence()).isEqualTo(1.0);
        assertThat(m.getVisibility()).isEqualTo(Visibility.USER_VISIBLE);
        assertThat(m.getSensitivity()).isEqualTo(Sensitivity.NORMAL);
        assertThat(m.getSource()).isEqualTo("manual");
        assertThat(m.isEnabled()).isTrue();
        assertThat(m.getCreatedAt()).isNotNull();
        assertThat(m.getUpdatedAt()).isNotNull();
    }

    @Test
    void createRespectsProvidedValues() {
        Memory m = service.create(new MemoryDraft("secret", "Token", "abc",
                "chat", 0.5, Visibility.INTERNAL, Sensitivity.SENSITIVE, null, false));

        assertThat(m.getConfidence()).isEqualTo(0.5);
        assertThat(m.getSensitivity()).isEqualTo(Sensitivity.SENSITIVE);
        assertThat(m.isEnabled()).isFalse();
    }

    @Test
    void getMissingThrows() {
        when(repo.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("nope")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatePatchesOnlyProvidedFields() {
        Memory existing = new Memory();
        existing.setId("mem_1");
        existing.setCategory("fact");
        existing.setTitle("Old");
        existing.setContent("Old content");
        existing.setConfidence(0.9);
        existing.setEnabled(true);
        when(repo.findById("mem_1")).thenReturn(Optional.of(existing));

        Memory updated = service.update("mem_1", new MemoryDraft(
                null, "New title", null, null, null, null, null, null, false));

        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getContent()).isEqualTo("Old content"); // unchanged
        assertThat(updated.getConfidence()).isEqualTo(0.9);         // unchanged
        assertThat(updated.isEnabled()).isFalse();                   // patched
    }

    @Test
    void searchFiltersByNeedle() {
        Memory a = mem("mem_a", "Coffee", "likes espresso", "preference");
        Memory b = mem("mem_b", "Car", "drives a Tesla", "fact");
        when(repo.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(a, b));

        assertThat(service.list("espresso")).containsExactly(a);
        assertThat(service.list("")).containsExactly(a, b);
    }

    @Test
    void expiredMemoryIsNotActive() {
        Memory m = new Memory();
        m.setEnabled(true);
        m.setExpiresAt(Instant.now().minusSeconds(60));
        assertThat(m.isActive()).isFalse();
    }

    @Test
    void consolidateRemovesExactDuplicatesKeepingNewest() {
        Memory newest = mem("mem_new", "Likes tables", "always render tables", "preference");
        Memory dupe = mem("mem_old", "Likes tables", "always render tables", "preference");
        Memory distinct = mem("mem_x", "Likes charts", "render charts", "preference");
        // repository returns newest-first (findAllByOrderByUpdatedAtDesc)
        when(repo.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(newest, dupe, distinct));

        int removed = service.consolidate();

        assertThat(removed).isEqualTo(1);
        org.mockito.Mockito.verify(repo).delete(dupe);      // the older duplicate is dropped
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.never()).delete(newest);
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.never()).delete(distinct);
    }

    @Test
    void consolidateNoDuplicatesRemovesNothing() {
        when(repo.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(
                mem("a", "One", "x", "fact"), mem("b", "Two", "y", "fact")));
        assertThat(service.consolidate()).isZero();
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.never()).delete(any(Memory.class));
    }

    private Memory mem(String id, String title, String content, String category) {
        Memory m = new Memory();
        m.setId(id);
        m.setTitle(title);
        m.setContent(content);
        m.setCategory(category);
        return m;
    }
}
