package com.jarvis.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.jarvis.config.JarvisSecurityProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class ApiTokenFilterTest {

    private ApiTokenFilter filter(String token) {
        JarvisSecurityProperties p = new JarvisSecurityProperties();
        p.setApiToken(token);
        return new ApiTokenFilter(p);
    }

    private HttpServletRequest req(String uri, String header) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getRequestURI()).thenReturn(uri);
        when(r.getHeader("X-Jarvis-Token")).thenReturn(header);
        return r;
    }

    @Test
    void openWhenNoTokenConfigured() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter("").doFilterInternal(req("/api/chat", null), mock(HttpServletResponse.class), chain);
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void rejectsApiCallWithoutTheToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        filter("secret").doFilterInternal(req("/api/chat", null), res, chain);
        verify(res).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void allowsApiCallWithTheCorrectToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter("secret").doFilterInternal(req("/api/chat", "secret"), mock(HttpServletResponse.class), chain);
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void ignoresNonApiPaths() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter("secret").doFilterInternal(req("/actuator/health", null), mock(HttpServletResponse.class), chain);
        verify(chain, times(1)).doFilter(any(), any());
    }
}
