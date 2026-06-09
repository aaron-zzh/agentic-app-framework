package com.xuejiai.aaf.framework.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SSE Token Filter——将 URL query param {@code ?token=xxx} 注入为 Authorization header。
 *
 * <p>浏览器原生 {@code EventSource} 不支持自定义 header，通过 query param 传递 JWT 是标准变通方案。
 * 仅在请求头中没有 Authorization 时生效，不覆盖已有认证信息。
 *
 * @author Kiro
 */
@Component
public class SseTokenFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = request.getParameter("token");
        if (StringUtils.hasText(token) && request.getHeader("Authorization") == null) {
            request = new BearerTokenRequestWrapper(request, token);
        }
        chain.doFilter(request, response);
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
