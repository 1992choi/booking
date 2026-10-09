package com.example.booking.core.logging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ServerHttpObservationFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "newPassword", "token", "refreshToken");
    private static final Pattern SENSITIVE_PATTERN = buildSensitivePattern();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // body 최대 10KB까지 캐싱 (그 이상은 로그에서 잘림)
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request, 10240);
        try {
            chain.doFilter(wrapped, response);
        } finally {
            log(wrapped, response.getStatus());
        }
    }

    private void log(ContentCachingRequestWrapper request, int status) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[REQUEST] ")
                .append(request.getMethod()).append(" ")
                .append(request.getRequestURI());

        String query = request.getQueryString();
        if (query != null) {
            sb.append("?").append(query);
        }

        sb.append(" -> ").append(status);

        String body = extractBody(request);
        if (!body.isBlank()) {
            sb.append("\n  body: ").append(mask(body));
        }

        putTraceContextOnMdc(request);
        try {
            if (status >= 400) {
                log.warn(sb.toString());
            } else {
                log.info(sb.toString());
            }
        } finally {
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }

    // 트레이싱 필터(ServerHttpObservationFilter)가 연 스팬 스코프는 체인이 복귀하는 시점(finally)엔 이미 닫혀 있어
    // Tracer.currentSpan()으로는 조회가 안 된다 — request 속성에 남아있는 Observation 컨텍스트에서
    // 직접 꺼내 MDC에 심는다. Loki 로그에는 mdc_traceId/mdc_spanId 필드로 노출된다.
    private void putTraceContextOnMdc(HttpServletRequest request) {
        Optional<ServerRequestObservationContext> observationContext =
                ServerHttpObservationFilter.findObservationContext(request);
        if (observationContext.isEmpty()) {
            return;
        }

        TracingObservationHandler.TracingContext tracingContext =
                observationContext.get().get(TracingObservationHandler.TracingContext.class);
        Span span = tracingContext != null ? tracingContext.getSpan() : null;
        if (span == null) {
            return;
        }

        MDC.put("traceId", span.context().traceId());
        MDC.put("spanId", span.context().spanId());
    }

    private String extractBody(ContentCachingRequestWrapper request) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return "";
        }
        byte[] buf = request.getContentAsByteArray();
        if (buf.length == 0) {
            return "";
        }

        return new String(buf, StandardCharsets.UTF_8);
    }

    private String mask(String json) {
        return SENSITIVE_PATTERN.matcher(json).replaceAll("\"$1\":\"***\"");
    }

    private static Pattern buildSensitivePattern() {
        String keys = String.join("|", SENSITIVE_KEYS);
        return Pattern.compile("\"(" + keys + ")\"\\s*:\\s*\"[^\"]*\"");
    }

}