package com.jarvis.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.seo.JarvisSeoPerformanceProperties;
import com.jarvis.seo.SeoPerformanceService;

import lombok.RequiredArgsConstructor;

/** SEO performance loop: see its config, or run one review on demand (headless trigger / try-before-arming). */
@RestController
@RequestMapping("/api/seo")
@RequiredArgsConstructor
public class SeoPerformanceController {

    private final SeoPerformanceService seo;
    private final JarvisSeoPerformanceProperties props;

    @GetMapping
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", props.isEnabled());
        out.put("site", props.getSite());
        out.put("period", props.getPeriod());
        out.put("cron", props.getCron());
        return out;
    }

    @PostMapping("/review")
    public Map<String, Object> review() {
        return Map.of("result", seo.review());
    }
}
