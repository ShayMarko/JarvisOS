package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.revenue.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * Records a product's money-loop progress — set its status, listing/deploy URLs, price, and add revenue
 * (which also files a RevenueOS entry). Lets the agent close the loop after it lists/deploys/sells.
 */
@Component
@RequiredArgsConstructor
public class TrackProductTool implements Tool {

    private final ProductService products;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("track_product",
                "Update a product's status as it moves through the money loop. Provide 'name' and any of: "
                + "'status' (BUILT/LISTED/LIVE/EARNING), 'listing_url', 'deploy_url', 'price' (USD), 'revenue' "
                + "(USD earned to add — also files it to the ROI ledger). Use after listing/deploying/selling.",
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"status\":{\"type\":\"string\"},"
                + "\"listing_url\":{\"type\":\"string\"},\"deploy_url\":{\"type\":\"string\"},"
                + "\"price\":{\"type\":\"number\"},\"revenue\":{\"type\":\"number\"}},\"required\":[\"name\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String argumentsJson) {
        JsonNode a = ToolArgs.root(mapper, argumentsJson);
        String name = a.path("name").asText("");
        if (name.isBlank()) {
            return "Provide the product 'name' to track.";
        }
        Double price = a.has("price") && a.path("price").isNumber() ? a.path("price").asDouble() : null;
        Double revenue = a.has("revenue") && a.path("revenue").isNumber() ? a.path("revenue").asDouble() : null;
        try {
            return products.update(name, a.path("status").asText(null),
                    a.path("listing_url").asText(null), a.path("deploy_url").asText(null), price, revenue);
        } catch (RuntimeException e) {
            return "Couldn't update the product: " + e.getMessage();
        }
    }
}
