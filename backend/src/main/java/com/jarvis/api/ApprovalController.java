package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.approval.ApprovalRequest;
import com.jarvis.approval.ApprovalResult;
import com.jarvis.approval.ApprovalService;

/** Approval Center endpoints (spec §11.2). */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approval;


    public record DecisionRequest(boolean remember) {}

    @GetMapping
    public List<ApprovalRequest> pending() {
        return approval.pending();
    }

    @GetMapping("/recent")
    public List<ApprovalRequest> recent(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return approval.recent(Math.min(limit, 500));
    }

    /** Approve a pending request; the deferred action runs and its result is returned. */
    @PostMapping("/{id}/approve")
    public ApprovalResult approve(@PathVariable String id, @RequestBody(required = false) DecisionRequest body) {
        return approval.approve(id, body != null && body.remember());
    }

    @PostMapping("/{id}/deny")
    public ApprovalRequest deny(@PathVariable String id, @RequestBody(required = false) DecisionRequest body) {
        return approval.deny(id, body != null && body.remember());
    }
}
