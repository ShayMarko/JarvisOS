package com.jarvis.ai.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.jarvis.ai.ToolSpec;
import com.jarvis.revenue.Product;
import com.jarvis.revenue.ProductService;

import lombok.RequiredArgsConstructor;

/** Lists Jarvis's product portfolio (build → list → deploy → earn) — works over Discord/voice/chat. */
@Component
@RequiredArgsConstructor
public class ProductPortfolioTool implements Tool {

    private final ProductService products;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("product_portfolio",
                "List the product portfolio: each product's status (BUILT/LISTED/LIVE/EARNING), listing + "
                + "deploy URLs, price and revenue. Use when asked 'what products do I have', 'product status', "
                + "'what have I shipped', 'how much have my products earned'.",
                "{\"type\":\"object\",\"properties\":{}}");
    }

    @Override
    public String execute(String argumentsJson) {
        List<Product> all = products.portfolio();
        if (all.isEmpty()) {
            return "No products yet — build and package one (e.g. ask the Product Builder or App Factory).";
        }
        double earned = all.stream().mapToDouble(Product::getRevenueUsd).sum();
        StringBuilder sb = new StringBuilder("🛍️ Portfolio (" + all.size() + " products, $"
                + String.format(java.util.Locale.ROOT, "%.2f", earned) + " earned):\n");
        for (Product p : all) {
            sb.append("• ").append(p.getName()).append(" [").append(p.getType()).append("] — ")
              .append(p.getStatus());
            if (p.getRevenueUsd() > 0) {
                sb.append(" · $").append(String.format(java.util.Locale.ROOT, "%.2f", p.getRevenueUsd()));
            }
            if (p.getListingUrl() != null) {
                sb.append(" · listed: ").append(p.getListingUrl());
            }
            if (p.getDeployUrl() != null) {
                sb.append(" · live: ").append(p.getDeployUrl());
            }
            sb.append('\n');
        }
        return sb.toString().strip();
    }
}
