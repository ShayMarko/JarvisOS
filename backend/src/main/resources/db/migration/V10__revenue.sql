-- RevenueOS ledger: income, savings, hours, assets, experiments — powers the ROI dashboard.
CREATE TABLE revenue_entry (
    id          TEXT PRIMARY KEY,
    kind        TEXT NOT NULL,            -- REVENUE | SAVED | HOURS | ASSET | EXPERIMENT
    amount      DOUBLE NOT NULL,
    note        TEXT,
    occurred_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_revenue_occurred ON revenue_entry (occurred_at);
