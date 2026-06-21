/**
 * 会话管理服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/** 会话管理：多会话并行、会话状态机、会话恢复。 会话状态存储在 Redis 中，支持跨实例恢复。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManager {

    private static final String KEY_PREFIX = "session:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /** 活跃会话缓存 */
    private final Map<String, SessionState> activeSessions = new ConcurrentHashMap<>();

    /** 创建新会话 */
    public SessionState createSession(Long userId, String assistantId) {
        var session = new SessionState();
        session.setSessionId(java.util.UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setAssistantId(assistantId);
        session.setStatus(SessionStatus.ACTIVE);
        session.setCreatedAt(Instant.now());
        session.setLastActiveAt(Instant.now());
        persist(session);
        activeSessions.put(session.getSessionId(), session);
        return session;
    }

    /** 获取会话 */
    public Optional<SessionState> getSession(String sessionId) {
        var cached = activeSessions.get(sessionId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return restore(sessionId);
    }

    /** 更新会话状态 */
    public void updateStatus(String sessionId, SessionStatus status) {
        getSession(sessionId)
                .ifPresent(
                        s -> {
                            s.setStatus(status);
                            s.setLastActiveAt(Instant.now());
                            persist(s);
                        });
    }

    /** 关闭会话 */
    public void closeSession(String sessionId) {
        updateStatus(sessionId, SessionStatus.CLOSED);
        activeSessions.remove(sessionId);
    }

    private void persist(SessionState session) {
        try {
            var json = JsonUtils.toJsonString(session);
            redisTemplate.opsForValue().set(KEY_PREFIX + session.getSessionId(), json, SESSION_TTL);
        } catch (Exception e) {
            log.warn("会话持久化失败: {}", e.getMessage());
        }
    }

    private Optional<SessionState> restore(String sessionId) {
        try {
            var json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
            if (json != null) {
                var session = JsonUtils.parseObject(json, SessionState.class);
                activeSessions.put(sessionId, session);
                return Optional.of(session);
            }
        } catch (Exception e) {
            log.warn("会话恢复失败: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /** 会话状态 */
    @Getter
    @Setter
    public static class SessionState {
        private String sessionId;
        private Long userId;
        private String assistantId;
        private SessionStatus status;
        private Instant createdAt;
        private Instant lastActiveAt;
        private Map<String, Object> context = new java.util.HashMap<>();
    }

    /** 会话状态枚举 */
    public enum SessionStatus {
        ACTIVE,
        WAITING,
        PROCESSING,
        SUSPENDED,
        CLOSED
    }
}
