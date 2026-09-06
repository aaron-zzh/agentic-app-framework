package com.xuejiai.aaf.framework.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SSE Cookie Filter——原生 EventSource 无法设置 Authorization 时，将 HttpOnly Cookie 注入标准 Bearer header。
 *
 * <p>fetch 流请求直接使用 Authorization；本过滤器不读取 query JWT，且不覆盖已有认证信息。
 *
 * @author AaronZZH
 */
@Component
public class SseTokenFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // fetch 流请求优先使用标准 Authorization；原生 EventSource 可使用 HttpOnly Cookie。
        String token = extractFromCookie(request, "aaf-token");
        if (StringUtils.hasText(token)) {
            if (request.getHeader("Authorization") == null) {
                request = new BearerTokenRequestWrapper(request, token);
            }
            String origin = request.getHeader("Origin");
            if (StringUtils.hasText(origin)
                    && !response.containsHeader("Access-Control-Allow-Origin")) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
            }
        }
        chain.doFilter(request, response);
    }

    private String extractFromCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    /** 包装请求，注入 Bearer Token 到 Authorization header。 */
    private static class BearerTokenRequestWrapper extends HttpServletRequestWrapper {

        private final String bearerToken;

        BearerTokenRequestWrapper(HttpServletRequest request, String token) {
            super(request);
            this.bearerToken = "Bearer " + token;
        }

        @Override
        public String getHeader(String name) {
            if ("Authorization".equalsIgnoreCase(name)) {
                return bearerToken;
            }
            return super.getHeader(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            if ("Authorization".equalsIgnoreCase(name)) {
                return java.util.Collections.enumeration(java.util.List.of(bearerToken));
            }
            return super.getHeaders(name);
        }
    }
}
