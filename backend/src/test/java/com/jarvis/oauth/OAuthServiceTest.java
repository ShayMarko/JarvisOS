package com.jarvis.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.oauth.OAuthService.TokenBundle;
import com.jarvis.secrets.SecretsVaultService;

class OAuthServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private OAuthService service() {
        OAuthProperties props = new OAuthProperties();
        OAuthProperties.Provider google = new OAuthProperties.Provider();
        google.setAuthUrl("https://accounts.google.com/o/oauth2/v2/auth");
        google.setTokenUrl("https://oauth2.googleapis.com/token");
        google.setClientId("client-123");
        google.setScopes(List.of("https://www.googleapis.com/auth/drive.readonly"));
        props.getProviders().put("google", google);
        return new OAuthService(props, mock(SecretsVaultService.class), mapper);
    }

    @Test
    void buildsConsentUrlWithOfflineAccess() {
        String url = service().authorizeUrl("google", "state-x");
        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("client_id=client-123")
                .contains("response_type=code")
                .contains("access_type=offline")
                .contains("state=state-x")
                .contains("redirect_uri=")
                .contains("scope=");
        // redirect_uri is URL-encoded; decoding it must yield our callback path.
        assertThat(java.net.URLDecoder.decode(url, java.nio.charset.StandardCharsets.UTF_8))
                .contains("redirect_uri=http://localhost:8088/api/oauth/google/callback");
    }

    @Test
    void parsesTokensAndKeepsRefreshOnRefreshGrant() {
        OAuthService svc = service();
        TokenBundle first = svc.parseTokenResponse(
                "{\"access_token\":\"AT1\",\"refresh_token\":\"RT1\",\"expires_in\":3600}", null);
        assertThat(first.accessToken()).isEqualTo("AT1");
        assertThat(first.refreshToken()).isEqualTo("RT1");
        assertThat(first.isExpired(Instant.now().getEpochSecond())).isFalse();

        // A refresh response without a new refresh_token keeps the old one.
        TokenBundle refreshed = svc.parseTokenResponse("{\"access_token\":\"AT2\",\"expires_in\":3600}", "RT1");
        assertThat(refreshed.accessToken()).isEqualTo("AT2");
        assertThat(refreshed.refreshToken()).isEqualTo("RT1");
    }

    @Test
    void detectsExpiry() {
        TokenBundle expired = new TokenBundle("AT", "RT", Instant.now().getEpochSecond() - 10);
        assertThat(expired.isExpired(Instant.now().getEpochSecond())).isTrue();
    }
}
