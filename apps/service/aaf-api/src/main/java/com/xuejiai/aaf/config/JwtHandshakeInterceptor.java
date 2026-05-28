package com.xuejiai.aaf.config;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.xuejiai.aaf.framework.security.access.AccessContext;
import com.xuejiai.aaf.framework.security.access.AccessLayer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Layer 2 WebSocket 握手认证拦截器。
 *
 * <p>从 URL 参数 {@code ?token=xxx} 或 Header {@code Authorization: Bearer xxx} 提取 JWT， 校验通过后将 userId
 * 存入 WebSocket attributes。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";

    private final JwtDecoder jwtDecoder;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        var token = extractToken(request);
        if (token == null) {
            log.warn("WebSocket 握手拒绝：缺少 token");
            return false;
        }

        try {
            var jwt = jwtDecoder.decode(token);
            var userId = Long.valueOf(jwt.getSubject());
            attributes.put(ATTR_USER_ID, userId);
            AccessContext.markProcessed(AccessLayer.INTERCEPTOR);
            log.debug("WebSocket 握手认证通过: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.warn("WebSocket 握手拒绝：token 无效 - {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {}

    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            var token = servletRequest.getServletRequest().getParameter("token");
            if (token != null && !token.isBlank()) return token;
        }
        var authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
