package com.jarvis.revenue;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * A sellable product in Jarvis's portfolio — the money-loop tracker tying together build → list → deploy →
 * earn. status: BUILT (packaged) → LISTED (on a store) → LIVE (deployed/published) → EARNING (made money).
 */
@Entity
@Table(name = "product")
@Getter
public class Product {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    /** boilerplate | app | api | site | ebook | notion | other. */
    @Setter
    private String type;

    @Setter
    private String status;

    /** Explorer folder it was built in (e.g. Projects/<name>). */
    @Setter
    private String folder;

    @Setter
    @Column(name = "listing_url")
    private String listingUrl;

    @Setter
    @Column(name = "deploy_url")
    private String deployUrl;

    @Setter
    @Column(name = "price_usd")
    private Double priceUsd;

    @Setter
    @Column(name = "revenue_usd")
    private double revenueUsd;

    @Column(name = "created_at")
    private Instant createdAt;

    @Setter
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Product() {
        // for JPA
    }

    public Product(String id, String name, String type, String folder) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.folder = folder;
        this.status = "BUILT";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
