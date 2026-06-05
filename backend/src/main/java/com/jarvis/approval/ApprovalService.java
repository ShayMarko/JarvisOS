package com.jarvis.approval;

import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.jarvis.common.Ids;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.jarvis.audit.AuditService;
import com.jarvis.error.Exceptions.ConflictException;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.security.RiskLevel;

/**
 * The Approval Center (spec §11.2) — human-in-the-loop gate for sensitive
 * actions. A caller {@link #submit} s an action with its risk and a preview; if
 * not auto-decided by a remembered rule it is parked as PENDING and the deferred
 * action is held in memory until the user approves (it then runs) or denies.
 *
 * <p>Note: the deferred action is in-memory, so a restart drops pending actions
 * (their audit rows remain). Durable pending actions arrive with the Durable
 * Task Engine (Phase 8).
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRepository repository;
    private final AuditService audit;

    /** Deferred actions awaiting a decision, keyed by request id. */
    private final Map<String, Callable<Object>> pendingActions = new ConcurrentHashMap<>();
    /** Remembered decisions keyed by action type (spec "Remember this decision"). */
    private final Map<String, ApprovalStatus> remembered = new ConcurrentHashMap<>();

    private final com.jarvis.notification.NotificationService notifications;


    public ApprovalResult submit(String actionType, String title, String description,
                                 RiskLevel risk, String preview, Callable<Object> action) {
        ApprovalRequest req = new ApprovalRequest(
                Ids.generate("ap"),
                actionType, title, description, risk, preview);

        ApprovalStatus remembered = this.remembered.get(actionType);
        if (remembered == ApprovalStatus.APPROVED) {
            return runApproved(req, action, true);
        }
        if (remembered == ApprovalStatus.DENIED) {
            req.setStatus(ApprovalStatus.DENIED);
            req.setDecidedAt(Instant.now());
            req.setResultSummary("Auto-denied by a remembered decision.");
            repository.save(req);
            audit.record("APPROVAL", actionType, title, "DENIED", "auto; id=" + req.getId());
            return new ApprovalResult(req, null);
        }

        repository.save(req);
        pendingActions.put(req.getId(), action);
        audit.record("APPROVAL", actionType, title, "PENDING", "risk=" + risk + "; id=" + req.getId());
        notifications.notify("warning", "Approval needed", title + " (risk " + risk + ")", "approval", req.getId());
        return new ApprovalResult(req, null);
    }

    public ApprovalResult approve(String id, boolean remember) {
        ApprovalRequest req = require(id);
        if (req.getStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException("Approval " + id + " is already " + req.getStatus());
        }
        Callable<Object> action = pendingActions.remove(id);
        if (remember) {
            remembered.put(req.getActionType(), ApprovalStatus.APPROVED);
        }
        return runApproved(req, action, false);
    }

    public ApprovalRequest deny(String id, boolean remember) {
        ApprovalRequest req = require(id);
        if (req.getStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException("Approval " + id + " is already " + req.getStatus());
        }
        pendingActions.remove(id);
        if (remember) {
            remembered.put(req.getActionType(), ApprovalStatus.DENIED);
        }
        req.setStatus(ApprovalStatus.DENIED);
        req.setDecidedAt(Instant.now());
        audit.record("APPROVAL", req.getActionType(), req.getTitle(), "DENIED", "id=" + id);
        return repository.save(req);
    }

    public List<ApprovalRequest> pending() {
        return repository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING);
    }

    public List<ApprovalRequest> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    private ApprovalResult runApproved(ApprovalRequest req, Callable<Object> action, boolean auto) {
        Object result = null;
        String summary;
        try {
            result = action != null ? action.call() : null;
            summary = "ok";
        } catch (Exception e) {
            log.warn("Approved action {} failed", req.getId(), e);
            summary = "error: " + e.getMessage();
        }
        req.setStatus(ApprovalStatus.APPROVED);
        req.setDecidedAt(Instant.now());
        req.setResultSummary(summary);
        repository.save(req);
        audit.record("APPROVAL", req.getActionType(), req.getTitle(), "APPROVED",
                (auto ? "auto; " : "") + "id=" + req.getId());
        return new ApprovalResult(req, result);
    }

    private ApprovalRequest require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No approval request " + id));
    }
}
