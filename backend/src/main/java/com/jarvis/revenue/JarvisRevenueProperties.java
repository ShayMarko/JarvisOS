package com.jarvis.revenue;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Binds {@code jarvis.revenue} — ROI-dashboard inputs (hourly value + fixed monthly subscription cost). */
@Getter
@Setter
@ConfigurationProperties(prefix = "jarvis.revenue")
public class JarvisRevenueProperties {

    /** Your hourly value — used to turn HOURS saved into money. */
    private double hourlyRate = 50;
    /** Fixed monthly operating cost (e.g. the AI subscription) added on top of measured per-call AI cost. */
    private double monthlyBaseCostUsd = 0;
}
