-- Actionable notifications: link a notification to a target (e.g. an ApprovalRequest id)
-- so the client can render inline Approve/Decline buttons in the bell.
ALTER TABLE notification ADD COLUMN action_id VARCHAR(64);
