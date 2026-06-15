-- Risk level (LOW/MEDIUM/HIGH/CRITICAL) for notifications surfaced from approvals,
-- so the Notification Center can render a per-level colour badge. Null for non-approval notifications.
ALTER TABLE notification ADD COLUMN risk TEXT;
