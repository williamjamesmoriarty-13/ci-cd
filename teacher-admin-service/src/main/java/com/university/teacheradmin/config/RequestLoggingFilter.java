package com.university.teacheradmin.config;

import com.university.teacheradmin.logging.StructuredLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_ATTRIBUTE = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final StructuredLogger logger = new StructuredLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
            // On ignore les endpoints techniques d'actuator dans les logs métier
            if (!request.getRequestURI().startsWith("/actuator")) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("method", request.getMethod());
                fields.put("path", request.getRequestURI());
                fields.put("status_code", response.getStatus());
                fields.put("latency_ms", Math.round(latencyMs * 100.0) / 100.0);
                fields.put("trace_id", traceId);
                logger.info("inbound_request", fields);
            }
        }
    }
}
