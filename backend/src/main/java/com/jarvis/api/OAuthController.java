package com.jarvis.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.oauth.OAuthService;

import lombok.RequiredArgsConstructor;

/**
 * OAuth endpoints (spec §13). The user hits {@code /authorize} to get the consent
 * URL, grants access, and the provider redirects back to {@code /callback} where
 * Jarvis exchanges the code for tokens (stored encrypted in the vault).
 */
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauth;

    @GetMapping("/{provider}/authorize")
    public Map<String, String> authorize(@PathVariable String provider) {
        return Map.of("url", oauth.authorizeUrl(provider, provider));
    }

    /** Provider redirect target: exchange the code, then show a simple done page. */
    @GetMapping("/{provider}/callback")
    public ResponseEntity<String> callback(@PathVariable String provider,
                                            @RequestParam(required = false) String code,
                                            @RequestParam(required = false) String error) {
        if (error != null || code == null) {
            return ResponseEntity.badRequest().body(page("Authorization was cancelled or failed.", false));
        }
        oauth.exchangeCode(provider, code);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(page(provider + " connected. You can close this tab and return to Jarvis.", true));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", oauth.configuredProviders());
        out.put("connected", oauth.connectedProviders());
        return out;
    }

    private String page(String message, boolean ok) {
        String color = ok ? "#3ad29f" : "#ff6b81";
        return "<!doctype html><html><head><meta charset='utf-8'><title>Jarvis OAuth</title></head>"
                + "<body style='font-family:system-ui;background:#05080f;color:#cfe2f2;display:grid;place-items:center;height:100vh;margin:0'>"
                + "<div style='text-align:center'><div style='font-size:34px;color:" + color + "'>"
                + (ok ? "✓" : "⚠") + "</div><p>" + message + "</p></div></body></html>";
    }
}
