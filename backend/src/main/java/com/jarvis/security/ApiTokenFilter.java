package com.jarvis.security;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jarvis.config.JarvisSecurityProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Optional API-token gate for {@code /api/**}. Dormant by default (no token configured → open, as the
 * server binds to loopback). Once {@code jarvis.security.api-token} is set — e.g. when Jarvis is reached
 * from a phone/off-box — every API request must present it, or gets a 401. Actuator/health (not under
 * {@code /api/}) is unaffected.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)   // after the trace-id filter
@RequiredArgsConstructor
public class ApiTokenFilter extends OncePerRequestFilter {

    private final JarvisSecurityProperties security;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = security.getApiToken();
        String path = request.getRequestURI();
        if (token == null || token.isBlank() || path == null || !path.startsWith("/api/") || provided(request).equals(token)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Missing or invalid API token.\"}");
    }

    private static String provided(HttpServletRequest req) {
        String h = req.getHeader("X-Jarvis-Token");
        if (h != null && !h.isBlank()) {
            return h.trim();
        }
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        String q = req.getParameter("token");
        return q == null ? "" : q.trim();
    }
}
