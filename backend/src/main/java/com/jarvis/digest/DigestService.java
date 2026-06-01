package com.jarvis.digest;

import java.util.List;

import org.springframework.stereotype.Service;

import com.jarvis.approval.ApprovalRequest;
import com.jarvis.approval.ApprovalService;
import com.jarvis.audit.AuditLogEntry;
import com.jarvis.audit.AuditService;
import com.jarvis.connectors.ConnectorRegistry;
import com.jarvis.task.Task;
import com.jarvis.task.TaskService;

import lombok.RequiredArgsConstructor;

/**
 * "Jarvis Today" — a morning briefing that aggregates what matters right now:
 * today's calendar, anything waiting on the user's approval, active/recent tasks,
 * and the latest activity. Reuses existing services so it stays a thin read-model.
 */
@Service
@RequiredArgsConstructor
public class DigestService {

    private static final int RECENT_AUDIT = 5;
    private static final int RECENT_TASKS = 5;

    private final ApprovalService approvals;
    private final TaskService tasks;
    private final AuditService audit;
    private final ConnectorRegistry connectors;

    /** A human/agent-readable briefing. */
    public String build() {
        StringBuilder sb = new StringBuilder("🗓️  Jarvis Today\n");

        sb.append("\n📅 Calendar\n").append(indent(calendar()));

        List<ApprovalRequest> pending = approvals.pending();
        sb.append("\n\n⏳ Pending approvals: ").append(pending.size());
        for (ApprovalRequest a : pending) {
            sb.append("\n  • [").append(a.getRiskLevel()).append("] ").append(a.getTitle());
        }

        sb.append("\n\n⚙️  Tasks: ").append(tasks.activeCount()).append(" active");
        List<Task> recentTasks = tasks.recent(RECENT_TASKS);
        for (Task t : recentTasks) {
            sb.append("\n  • ").append(t.getStatus()).append(" — ").append(oneLine(t.getRequest()));
        }

        sb.append("\n\n🔎 Recent activity");
        List<AuditLogEntry> recent = audit.recent(RECENT_AUDIT);
        if (recent.isEmpty()) {
            sb.append("\n  (nothing logged yet)");
        }
        for (AuditLogEntry e : recent) {
            String label = e.getCommand() != null && !e.getCommand().isBlank() ? e.getCommand() : e.getInputType();
            sb.append("\n  • ").append(e.getStatus()).append(' ').append(label);
        }
        return sb.toString();
    }

    private String calendar() {
        try {
            String out = connectors.invoke("calendar", "today_events", "{}");
            return out == null || out.isBlank() ? "(no events today)" : out;
        } catch (Exception e) {
            return "(calendar not connected — add the google-calendar-token secret to enable)";
        }
    }

    private static String indent(String text) {
        return text.lines().map(l -> "  " + l).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static String oneLine(String s) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").strip();
        return flat.length() > 80 ? flat.substring(0, 80) + "…" : flat;
    }
}
