package com.ipos.security;

import com.ipos.config.IntegrationPuProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates {@code X-IPOS-Integration-Key} for {@code POST /api/integration-pu/inbound/**}.
 * Runs before authentication; inbound integration does not use a browser session.
 * Registered as a bean from {@link com.ipos.config.IntegrationPuConfig}.
 */
public class IntegrationPuInboundApiKeyFilter extends OncePerRequestFilter {

    public static final String INTEGRATION_KEY_HEADER = "X-IPOS-Integration-Key";

    private final IntegrationPuProperties properties;

    public IntegrationPuInboundApiKeyFilter(IntegrationPuProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (!request.getMethod().equalsIgnoreCase("POST") || !uri.contains("/api/integration-pu/inbound")) {
            filterChain.doFilter(request, response);
            return;
        }

        String configured = properties.getInboundApiKey();
        if (configured == null || configured.isBlank()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"IPOS-PU inbound integration is not configured (missing ipos.integration-pu.inbound-api-key).\"}");
            return;
        }

        String provided = request.getHeader(INTEGRATION_KEY_HEADER);
        if (provided == null || !configured.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or missing integration API key.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
