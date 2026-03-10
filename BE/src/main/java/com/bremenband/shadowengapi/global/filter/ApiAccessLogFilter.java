package com.bremenband.shadowengapi.global.filter;

import com.bremenband.shadowengapi.global.jwt.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS");

    private static final int MAX_BODY_LENGTH = 1_000;

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, MAX_BODY_LENGTH);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logAccess(wrappedRequest, wrappedResponse, duration);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logAccess(ContentCachingRequestWrapper request,
                           ContentCachingResponseWrapper response,
                           long duration) {

        String method    = request.getMethod();
        String uri       = request.getRequestURI();
        String query     = request.getQueryString();
        String fullUri   = query != null ? uri + "?" + query : uri;
        int    status    = response.getStatus();
        String clientIp  = resolveClientIp(request);
        String user      = resolveUser(request);
        String userAgent = request.getHeader("User-Agent");
        String body      = resolveRequestBody(request, uri);

        String message = String.format(
                "METHOD=%-6s | URI=%-50s | STATUS=%d | DURATION=%4dms | IP=%-15s | USER=%-10s | AGENT=%s | BODY=%s",
                method, fullUri, status, duration, clientIp, user,
                userAgent != null ? userAgent : "-",
                body
        );

        if (status >= 500) {
            ACCESS_LOG.error(message);
        } else if (status >= 400) {
            ACCESS_LOG.warn(message);
        } else {
            ACCESS_LOG.info(message);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    // SecurityContext 클리어 시점에 무관하게 JWT 토큰에서 직접 사용자를 추출한다
    private String resolveUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtProvider.isValid(token)) {
                return String.valueOf(jwtProvider.getUserId(token));
            }
        }
        return "anonymous";
    }

    // 인증 경로는 비밀번호 노출 방지를 위해 바디를 마스킹한다
    private String resolveRequestBody(ContentCachingRequestWrapper request, String uri) {
        if (isSensitivePath(uri)) {
            return "[MASKED]";
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.contains("application/json")) {
            return "-";
        }
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return "-";
        }
        String body = new String(content, 0, Math.min(content.length, MAX_BODY_LENGTH), StandardCharsets.UTF_8);
        return content.length > MAX_BODY_LENGTH ? body + "...[truncated]" : body;
    }

    private boolean isSensitivePath(String uri) {
        return uri.contains("/auth/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/h2-console")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/actuator")
                || uri.matches(".+\\.(css|js|gif|png|jpg|jpeg|ico|woff|woff2|ttf|svg|map)$");
    }
}
