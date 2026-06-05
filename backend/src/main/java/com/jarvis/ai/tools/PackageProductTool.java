package com.jarvis.ai.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.ai.ToolSpec;
import com.jarvis.revenue.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * Packages a built project into a sellable .zip and logs it as a revenue asset — the final step of the
 * "money loop" (build → docs → sales copy → PACKAGE → sell). Deterministic, no AI cost.
 */
@Component
@RequiredArgsConstructor
public class PackageProductTool implements Tool {

    private final ProductService products;
    private final ObjectMapper mapper;

    @Override
    public ToolSpec spec() {
        return new ToolSpec("package_product",
                "Zip a built project folder into a sellable .zip under Products/ and log it as a revenue asset. "
                + "Use after building a boilerplate/starter or app you intend to sell. Provide 'folder' (the "
                + "Explorer path, e.g. Projects/spring-saas-starter) and optional 'name' for the zip.",
                "{\"type\":\"object\",\"properties\":{\"folder\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"}},"
                + "\"required\":[\"folder\"]}");
    }

    @Override
    public boolean mutates() {
        return true;
    }

    @Override
    public String execute(String argumentsJson) {
        String folder = ToolArgs.firstStr(mapper, argumentsJson, "folder", "path", "project", "dir");
        String name = ToolArgs.firstStr(mapper, argumentsJson, "name", "title");
        if (folder.isBlank()) {
            return "Provide the 'folder' to package (the project directory under the Explorer).";
        }
        try {
            return products.packageProduct(folder, name);
        } catch (RuntimeException e) {
            return "Couldn't package the product: " + e.getMessage();
        }
    }
}
