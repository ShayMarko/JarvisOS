package com.jarvis.api;

import java.util.Locale;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.revenue.NotionTemplateBuilder;
import com.jarvis.revenue.NotionTemplateBuilder.BuildResult;
import com.jarvis.revenue.RevenueKind;
import com.jarvis.revenue.RevenueService;
import com.jarvis.revenue.TemplateSpecGenerator;
import com.jarvis.revenue.TemplateSpecGenerator.Result;

import lombok.RequiredArgsConstructor;

/** RevenueOS endpoints — the money-loop pipeline: generate sellable assets + the ROI dashboard. */
@RestController
@RequestMapping("/api/revenue")
@RequiredArgsConstructor
public class RevenueController {

    private final TemplateSpecGenerator generator;
    private final NotionTemplateBuilder builder;
    private final RevenueService revenue;

    /** The ROI dashboard — does Jarvis out-earn its cost this month? */
    @GetMapping("/roi")
    public ResponseEntity<?> roi() {
        return ResponseEntity.ok(revenue.roi());
    }

    public record LogRequest(String kind, Double amount, String note) {}

    /** Log a ledger entry (REVENUE/SAVED/HOURS/ASSET/EXPERIMENT) → returns the updated ROI. */
    @PostMapping("/log")
    public ResponseEntity<?> log(@RequestBody LogRequest req) {
        if (req.kind() == null || req.kind().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide 'kind' (REVENUE/SAVED/HOURS/ASSET/EXPERIMENT)."));
        }
        RevenueKind kind;
        try {
            kind = RevenueKind.valueOf(req.kind().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown kind '" + req.kind() + "'."));
        }
        revenue.log(kind, req.amount() == null ? 1 : req.amount(), req.note());
        return ResponseEntity.ok(revenue.roi());
    }

    public record GenerateRequest(String idea, String language, String targetCustomer,
                                  String complexity, String parentPageId) {}

    @PostMapping("/notion/templates/generate")
    public ResponseEntity<?> generate(@RequestBody GenerateRequest req) {
        if (req.idea() == null || req.idea().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide an 'idea'."));
        }
        if (!builder.available()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Add a 'notion-token' to the Secrets Vault first (the Notion integration token)."));
        }
        if (req.parentPageId() == null || req.parentPageId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Provide 'parentPageId' — a Notion page the integration can access; the template is built under it."));
        }
        Result gen = generator.generate(req.idea(), req.language(), req.targetCustomer(), req.complexity());
        BuildResult built = builder.build(gen.spec(), req.parentPageId());
        return ResponseEntity.ok(Map.of(
                "title", gen.spec().title(),
                "aiCallsUsed", gen.aiCallsUsed(),
                "notionRootPageId", built.notionRootPageId(),
                "createdPages", built.createdPages(),
                "createdDatabases", built.createdDatabases(),
                "manualSteps", built.manualSteps(),
                "salesCopyPath", built.salesCopyPath()));
    }
}
