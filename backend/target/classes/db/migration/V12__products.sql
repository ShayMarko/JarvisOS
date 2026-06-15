-- Product portfolio: the money-loop tracker (build → list → deploy → earn) behind RevenueOS.
CREATE TABLE product (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    type        TEXT,
    status      TEXT NOT NULL,            -- BUILT | LISTED | LIVE | EARNING
    folder      TEXT,
    listing_url TEXT,
    deploy_url  TEXT,
    price_usd   DOUBLE,
    revenue_usd DOUBLE DEFAULT 0,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);
CREATE INDEX idx_product_updated ON product (updated_at);
