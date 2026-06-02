package com.jarvis.oauth;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jarvis.error.Exceptions.ConflictException;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.secrets.SecretView;
import com.jarvis.secrets.SecretsVaultService;

import lombok.RequiredArgsConstructor;

/**
 * OAuth 2.0 authorization-code flow with refresh (spec §13 oauth). Builds the
 * consent URL, exchanges the code for tokens, persists them encrypted in the
 * Secrets Vault (entry {@code oauth:<provider>}), and hands out a valid access
 * token on demand — refreshing automatically when it's near expiry. Connectors
 * call {@link #accessToken(String)}; they never see the refresh token.
 */
@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final long EXPIRY_SKEW_SECONDS = 60;

    private final OAuthProperties props;
    private final SecretsVaultService vault;
    private final ObjectMapper mapper;
    private final RestClient http = RestClient.create();

    /** A stored token set. Persisted as JSON in the vault. */
    record TokenBundle(String accessToken, String refreshToken, long expiresAtEpoch) {
        boolean isExpired(long nowEpoch) {
            return expiresAtEpoch > 0 && nowEpoch >= expiresAtEpoch - EXPIRY_SKEW_SECONDS;
        }
    }

    public boolean isConnected(String provider) {
        return vault.has(vaultKey(provider));
    }

    /** Build the provider's consent URL the user opens to grant access. */
    public String authorizeUrl(String provider, String state) {
        OAuthProperties.Provider p = require(provider);
        return UriComponentsBuilder.fromUriString(p.getAuthUrl())
                .queryParam("client_id", p.getClientId())
                .queryParam("redirect_uri", redirectUri(provider, p))
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", p.getScopes()))
                .queryParam("access_type", "offline")   // ask Google for a refresh token
                .queryParam("prompt", "consent")
                .queryParam("state", state == null ? provider : state)
                .build().toUriString();
    }

    /** Exchange an authorization code for tokens and store them. */
    public void exchangeCode(String provider, String code) {
        OAuthProperties.Provider p = require(provider);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", p.getClientId());
        form.add("client_secret", p.getClientSecret());
        form.add("redirect_uri", redirectUri(provider, p));
        TokenBundle bundle = postForTokens(p, form, null);
        save(provider, bundle, p.getScopes());
        log.info("OAuth provider '{}' connected (refresh token {}).", provider,
                bundle.refreshToken() == null ? "absent" : "stored");
    }

    /** A valid access token for the provider, refreshing if expired. */
    public String accessToken(String provider) {
        TokenBundle bundle = read(provider);
        if (bundle == null) {
            throw new ConflictException("Not connected to '" + provider + "'. Authorize it first (see /oauth).");
        }
        if (!bundle.isExpired(Instant.now().getEpochSecond())) {
            return bundle.accessToken();
        }
        if (bundle.refreshToken() == null || bundle.refreshToken().isBlank()) {
            throw new ConflictException("'" + provider + "' access token expired and no refresh token — re-authorize via /oauth.");
        }
        OAuthProperties.Provider p = require(provider);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", bundle.refreshToken());
        form.add("client_id", p.getClientId());
        form.add("client_secret", p.getClientSecret());
        TokenBundle refreshed = postForTokens(p, form, bundle.refreshToken()); // keep old refresh token if a new one isn't returned
        save(provider, refreshed, p.getScopes());
        return refreshed.accessToken();
    }

    private TokenBundle postForTokens(OAuthProperties.Provider p, MultiValueMap<String, String> form, String fallbackRefresh) {
        String raw = http.post().uri(p.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form).retrieve().body(String.class);
        return parseTokenResponse(raw, fallbackRefresh);
    }

    /** Parse a token endpoint response — pure, unit-tested without network. */
    TokenBundle parseTokenResponse(String raw, String fallbackRefresh) {
        try {
            JsonNode root = mapper.readTree(raw);
            String access = root.path("access_token").asText("");
            String refresh = root.path("refresh_token").asText(fallbackRefresh == null ? "" : fallbackRefresh);
            long expiresIn = root.path("expires_in").asLong(3600);
            long expiresAt = Instant.now().getEpochSecond() + expiresIn;
            return new TokenBundle(access, refresh.isBlank() ? null : refresh, expiresAt);
        } catch (Exception e) {
            throw new ConflictException("Token endpoint returned an unexpected response.");
        }
    }

    private void save(String provider, TokenBundle bundle, List<String> scopes) {
        // Upsert: drop any existing bundle for this provider, then store the new one.
        String key = vaultKey(provider);
        vault.list().stream().filter(s -> s.name().equals(key)).forEach(s -> vault.revoke(s.id()));
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("accessToken", bundle.accessToken());
            node.put("refreshToken", bundle.refreshToken());
            node.put("expiresAtEpoch", bundle.expiresAtEpoch());
            vault.store(key, "oauth", mapper.writeValueAsString(node), scopes);
        } catch (Exception e) {
            throw new ConflictException("Could not store OAuth tokens.");
        }
    }

    private TokenBundle read(String provider) {
        String json = vault.revealByName(vaultKey(provider));
        if (json == null) {
            return null;
        }
        try {
            JsonNode n = mapper.readTree(json);
            String refresh = n.path("refreshToken").isNull() ? null : n.path("refreshToken").asText(null);
            return new TokenBundle(n.path("accessToken").asText(""), refresh, n.path("expiresAtEpoch").asLong(0));
        } catch (Exception e) {
            return null;
        }
    }

    private String redirectUri(String provider, OAuthProperties.Provider p) {
        if (p.getRedirectUri() != null && !p.getRedirectUri().isBlank()) {
            return p.getRedirectUri();
        }
        return props.getBaseUrl() + "/api/oauth/" + provider + "/callback";
    }

    private OAuthProperties.Provider require(String provider) {
        OAuthProperties.Provider p = props.getProviders().get(provider);
        if (p == null) {
            throw new NotFoundException("No OAuth provider '" + provider + "' configured (jarvis.oauth.providers).");
        }
        return p;
    }

    private static String vaultKey(String provider) {
        return "oauth:" + provider;
    }

    /** Provider names that have stored tokens (for /oauth + status). */
    public List<String> connectedProviders() {
        return vault.list().stream().map(SecretView::name)
                .filter(n -> n.startsWith("oauth:")).map(n -> n.substring("oauth:".length())).toList();
    }

    public java.util.Set<String> configuredProviders() {
        return props.getProviders().keySet();
    }
}
