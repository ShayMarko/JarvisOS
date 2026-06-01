package com.jarvis.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jarvis.audit.AuditService;
import com.jarvis.security.RiskLevel;

class ApprovalServiceTest {

    private ApprovalRepository repo;
    private ApprovalService service;

    @BeforeEach
    void setUp() {
        repo = mock(ApprovalRepository.class);
        service = new ApprovalService(repo, mock(AuditService.class),
                mock(com.jarvis.notification.NotificationService.class));
        when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void submitParksAsPending() {
        ApprovalResult r = service.submit("terminal", "Run: echo hi", "why", RiskLevel.LOW,
                "echo hi", () -> "ran");
        assertThat(r.request().getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(r.result()).isNull();
    }

    @Test
    void approveRunsDeferredActionAndReturnsResult() {
        ApprovalResult submitted = service.submit("terminal", "Run", "why", RiskLevel.LOW,
                "echo hi", () -> "the-output");
        String id = submitted.request().getId();
        when(repo.findById(id)).thenReturn(Optional.of(submitted.request()));

        ApprovalResult decided = service.approve(id, false);
        assertThat(decided.request().getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.result()).isEqualTo("the-output");
    }

    @Test
    void denyMarksDeniedAndDropsAction() {
        ApprovalResult submitted = service.submit("terminal", "Run", "why", RiskLevel.HIGH,
                "rm -rf x", () -> "should-not-run");
        String id = submitted.request().getId();
        when(repo.findById(id)).thenReturn(Optional.of(submitted.request()));

        ApprovalRequest denied = service.deny(id, false);
        assertThat(denied.getStatus()).isEqualTo(ApprovalStatus.DENIED);
    }

    @Test
    void rememberedApproveAutoRunsFutureRequests() {
        ApprovalResult first = service.submit("terminal", "Run", "why", RiskLevel.LOW, "echo a", () -> "a");
        String id = first.request().getId();
        when(repo.findById(id)).thenReturn(Optional.of(first.request()));
        service.approve(id, true); // remember = true

        ApprovalResult second = service.submit("terminal", "Run again", "why", RiskLevel.LOW, "echo b", () -> "b");
        assertThat(second.request().getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(second.result()).isEqualTo("b");
    }
}
