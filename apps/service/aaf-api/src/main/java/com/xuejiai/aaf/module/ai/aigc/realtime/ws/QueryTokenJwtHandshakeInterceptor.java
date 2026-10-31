package com.xuejiai.aaf.module.ai.aigc.realtime.ws;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.xuejiai.aaf.config.JwtHandshakeInterceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 仅供浏览器 Omni Realtime v1 入口使用的 token query JWT 握手认证。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryTokenJwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) != null) {
            log.warn("Omni Realtime 浏览器握手拒绝：不接受 Authorization 头");
            return false;
        }

        var queryParams = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        if (!queryParams.keySet().equals(Set.of("token"))) {
            log.warn("Omni Realtime 浏览器握手拒绝：只接受 token query 参数");
            return false;
        }
        var token = queryParams.getFirst("token");
        if (token == null || token.isBlank()) {
            log.warn("Omni Realtime 浏览器握手拒绝：缺少 token");
            return false;
        }

        try {
            var jwt = jwtDecoder.decode(token);
            attributes.put(JwtHandshakeInterceptor.ATTR_USER_ID, Long.valueOf(jwt.getSubject()));
            return true;
        } catch (Exception exception) {
            log.warn(
                    "Omni Realtime 浏览器握手拒绝：token 无效，reason={}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {}
}
