package com.xuejiai.aaf.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 开发环境模拟 Token 过滤器。
 *
 * <p>请求头 Authorization: Bearer test1 时，直接以 userId=1 身份通过认证。 格式：{mockSecret}{userId}，默认
 * mockSecret="test"。
 *
 * <p>⚠️ 生产环境必须关闭。
 */
public class MockTokenFilter extends OncePerRequestFilter {

    private final String mockSecret;

    public MockTokenFilter(String mockSecret) {
        this.mockSecret = mockSecret;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer " + mockSecret)) {
            String userIdStr = header.substring(("Bearer " + mockSecret).length());
            try {
                Long userId = Long.valueOf(userIdStr);
                var auth =
                        new UsernamePasswordAuthenticationToken(
                                userId.toString(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (NumberFormatException ignored) {
                // 格式不对，跳过，交给后续 JWT 验证
            }
        }
        chain.doFilter(request, response);
    }
}
