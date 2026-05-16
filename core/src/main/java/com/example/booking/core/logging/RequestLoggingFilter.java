package com.example.booking.core.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

        if (status >= 400) {
            log.warn(sb.toString());
        } else {
            log.info(sb.toString());
        }
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