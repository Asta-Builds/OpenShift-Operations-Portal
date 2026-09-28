-- V10: Scheduled report delivery and alerts. Every email the portal sends, or would have sent, is logged with its
-- outcome, so a schedule or alert that could not be delivered is visible instead of silently skipped.

ALTER TABLE reports ADD COLUMN report_format VARCHAR(10) DEFAULT 'PDF' NOT NULL;  -- CSV | PDF attachment

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    kind VARCHAR(30) NOT NULL,                 -- SCHEDULED_REPORT | LICENSE_BREACH | CAPACITY_RUNWAY
    report_id UUID REFERENCES reports(id) ON DELETE SET NULL,
    subject VARCHAR(255) NOT NULL,
    recipients TEXT NOT NULL,                  -- comma-separated addresses
    status VARCHAR(20) NOT NULL,               -- QUEUED | SENT | NOT_SENT (no SMTP server) | FAILED
    detail TEXT,                               -- why it was not sent, or the error
    dedupe_key VARCHAR(120) UNIQUE,            -- alerts: one per kind and day
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP
);
CREATE INDEX idx_notifications_created ON notifications(created_at DESC);
CREATE INDEX idx_notifications_report ON notifications(report_id, created_at DESC);
