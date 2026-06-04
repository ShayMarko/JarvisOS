package com.jarvis.revenue;

/** What a RevenueOS ledger entry represents (drives the ROI dashboard). */
public enum RevenueKind {
    /** Direct income (USD). */
    REVENUE,
    /** Money saved / cost avoided (USD). */
    SAVED,
    /** Hours saved (value = hours × hourly rate). */
    HOURS,
    /** A sellable asset created (template, ebook, app, landing page). amount = count (usually 1). */
    ASSET,
    /** An active revenue experiment being tested. amount = count (usually 1). */
    EXPERIMENT
}
