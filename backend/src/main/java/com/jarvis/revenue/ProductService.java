package com.jarvis.revenue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;

import com.jarvis.common.Ids;
import com.jarvis.explorer.FileSystemService;

import lombok.RequiredArgsConstructor;

/**
 * RevenueOS packaging: turn a built project folder into a SELLABLE artifact — a single .zip under
 * {@code Products/} — and record it as a revenue asset. The "money loop" primitive the agent lacked:
 * it can already build code + write sales copy, but couldn't package the result for sale.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String PRODUCTS = "Products";

    private final FileSystemService fs;
    private final RevenueService revenue;
    private final ProductRepository products;

    /**
     * Zip the Explorer folder {@code folder} (e.g. {@code Projects/spring-saas-starter}) into
     * {@code Products/<name>.zip} and log it as a RevenueOS asset. Returns a human-readable result with
     * the relative zip path and file count.
     */
    public String packageProduct(String folder, String name) {
        Path src = fs.resolveExisting(folder);   // guarded: must exist + be under the Jarvis root
        if (!Files.isDirectory(src)) {
            return "Error: '" + folder + "' is not a folder — point me at the project directory to package.";
        }
        String safe = (name == null || name.isBlank() ? src.getFileName().toString() : name)
                .replaceAll("[^a-zA-Z0-9-_ ]", "_").strip();
        if (safe.isBlank()) {
            safe = "product";
        }
        Path dir = fs.getRoot().resolve(PRODUCTS);
        Path zip = dir.resolve(safe + ".zip");
        int count = 0;
        try {
            Files.createDirectories(dir);
            List<Path> files;
            try (Stream<Path> walk = Files.walk(src)) {
                files = walk.filter(Files::isRegularFile).toList();
            }
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (Path p : files) {
                    zos.putNextEntry(new ZipEntry(src.relativize(p).toString().replace('\\', '/')));
                    Files.copy(p, zos);
                    zos.closeEntry();
                    count++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not package " + folder, e);
        }
        revenue.logAsset("Product package: " + safe + ".zip");
        recordBuilt(safe, typeFor(folder), folder);
        long kb = Math.max(1, sizeQuietly(zip) / 1024);
        return "📦 Packaged " + safe + " → " + PRODUCTS + "/" + safe + ".zip (" + count + " files, ~" + kb
                + " KB). Logged as a revenue asset. Sell it (e.g. Gumroad) and use revenue_log when it earns.";
    }

    /** Upsert a product as BUILT after it's packaged (idempotent by name). */
    public void recordBuilt(String name, String type, String folder) {
        Product p = findByName(name);
        if (p == null) {
            p = new Product(Ids.generate("prod"), name, type, folder);
        } else {
            p.setFolder(folder);
            p.setUpdatedAt(Instant.now());
        }
        products.save(p);
    }

    /** The whole portfolio, most-recently-updated first. */
    public List<Product> portfolio() {
        List<Product> all = products.findAllByOrderByUpdatedAtDesc();
        return all == null ? List.of() : all;
    }

    /**
     * Update a product's money-loop state: status, listing/deploy URLs, price, and recorded revenue.
     * A positive {@code addRevenue} also files a RevenueOS REVENUE entry (so the ROI dashboard reflects it).
     */
    public String update(String name, String status, String listingUrl, String deployUrl,
                         Double price, Double addRevenue) {
        Product p = findByName(name);
        if (p == null) {
            return "No product named '" + name + "' — package it first (it's tracked once packaged).";
        }
        if (notBlank(status)) {
            p.setStatus(status.toUpperCase(java.util.Locale.ROOT));
        }
        if (notBlank(listingUrl)) {
            p.setListingUrl(listingUrl);
            if ("BUILT".equals(p.getStatus())) {
                p.setStatus("LISTED");
            }
        }
        if (notBlank(deployUrl)) {
            p.setDeployUrl(deployUrl);
            if ("BUILT".equals(p.getStatus()) || "LISTED".equals(p.getStatus())) {
                p.setStatus("LIVE");
            }
        }
        if (price != null) {
            p.setPriceUsd(price);
        }
        if (addRevenue != null && addRevenue > 0) {
            p.setRevenueUsd(p.getRevenueUsd() + addRevenue);
            p.setStatus("EARNING");
            revenue.log(RevenueKind.REVENUE, addRevenue, "Product: " + name);
        }
        p.setUpdatedAt(Instant.now());
        products.save(p);
        return "Updated \"" + name + "\" → " + p.getStatus()
                + (p.getRevenueUsd() > 0 ? " ($" + p.getRevenueUsd() + " earned)" : "") + ".";
    }

    private Product findByName(String name) {
        Optional<Product> opt = products.findFirstByNameIgnoreCaseOrderByCreatedAtDesc(name);
        return opt == null ? null : opt.orElse(null);   // tolerate unstubbed test mocks
    }

    private static String typeFor(String folder) {
        String f = folder == null ? "" : folder.toLowerCase(java.util.Locale.ROOT);
        if (f.startsWith("sites/")) {
            return "site";
        }
        if (f.startsWith("books/")) {
            return "ebook";
        }
        if (f.startsWith("projects/")) {
            return "app";
        }
        return "product";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static long sizeQuietly(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }
}
