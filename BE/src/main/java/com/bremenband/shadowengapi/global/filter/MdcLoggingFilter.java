package com.bremenband.shadowengapi.global.filter;

import java.io.IOException;
import java.util.UUID;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static com.bremenband.shadowengapi.global.filter.MdcKey.*;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            setMdc(request);
            log.info("[{}] HTTP Request Incoming: {} {} (IP: {})",
                    MDC.get(REQUEST_ID.name()),
                    request.getMethod(),
                    request.getRequestURI(),
                    MDC.get(REQUEST_IP.name()));

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private void setMdc(HttpServletRequest request) {
        MDC.put(REQUEST_ID.name(), UUID.randomUUID().toString().substring(0, 8));
        MDC.put(REQUEST_METHOD.name(), request.getMethod()); // GET, POST...
        MDC.put(REQUEST_URI.name(), request.getRequestURI()); // /api/cctv...
        MDC.put(REQUEST_IP.name(), getClientIp(request)); // 접속 IP
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/swagger-ui")       // 스웨거 화면 관련 파일들
                || path.startsWith("/v3/api-docs")      // 스웨거 설정 데이터
                || path.startsWith("/favicon.ico")      // 웹사이트 아이콘
                || path.matches(".*\\.(css|js|png|jpg|jpeg|gif)$"); // 기타 정적 파일 확장자
    }
}
