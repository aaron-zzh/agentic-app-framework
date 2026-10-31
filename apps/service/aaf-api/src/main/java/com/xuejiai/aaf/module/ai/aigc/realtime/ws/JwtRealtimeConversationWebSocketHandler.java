package com.xuejiai.aaf.module.ai.aigc.realtime.ws;

import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import com.xuejiai.aaf.config.JwtHandshakeInterceptor;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeCaller;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeConversationSessionFactory;

/** JWT 专用的浏览器 Omni Realtime v1 入口。 */
@Component
public class JwtRealtimeConversationWebSocketHandler
        extends AbstractRealtimeConversationWebSocketHandler {

    private static final String DEVICE_KEY_HEADER = "X-Device-Key";
    private static final String DEVICE_CREDENTIAL_HEADER = "X-Device-Credential";

    public JwtRealtimeConversationWebSocketHandler(
            RealtimeConversationSessionFactory sessionFactory) {
        super(sessionFactory, Endpoint.WEB);
    }

    @Override
    protected RealtimeCaller resolveCaller(WebSocketSession session) {
        var userId = session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
        if (!(userId instanceof Long value)) {
            throw new IllegalArgumentException("浏览器实时会话缺少可信用户身份");
        }
        return new RealtimeCaller.Web(value);
    }

    @Override
    protected void validateHandshake(WebSocketSession session) {
        var queryNames = queryNames(session);
        if (!queryNames.equals(Set.of("token"))) {
            throw new IllegalArgumentException("浏览器入口只允许 token query 参数");
        }
        var headers = session.getHandshakeHeaders();
        if (headers.getFirst(DEVICE_KEY_HEADER) != null
                || headers.getFirst(DEVICE_CREDENTIAL_HEADER) != null
                || headers.getFirst(HttpHeaders.AUTHORIZATION) != null) {
            throw new IllegalArgumentException("浏览器入口只接受 token query 参数");
        }
    }
}
