package com.xuejiai.aaf.framework.intelligent.assistant;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 助理会话级授权状态，TTL 随会话自然失效。 */
@Service
@RequiredArgsConstructor
public class AssistantSessionTrustService {

    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final String TOOL_TRUST_PREFIX = "session_tool_trust:";
    private static final String FULL_DELEGATION_PREFIX = "session_delegation:";

    private final StringRedisTemplate redisTemplate;

    public void trustTools(String sessionId, Long userId, List<String> toolNames) {
        if (sessionId == null || userId == null || toolNames == null) {
            return;
        }
        for (String toolName : toolNames) {
            if (toolName == null || toolName.isBlank()) {
                continue;
            }
            redisTemplate
                    .opsForValue()
                    .set(toolTrustKey(sessionId, toolName), String.valueOf(userId), SESSION_TTL);
        }
    }

    public void grantFullDelegation(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return;
        }
        redisTemplate
                .opsForValue()
                .set(FULL_DELEGATION_PREFIX + sessionId, String.valueOf(userId), SESSION_TTL);
    }

    public void revokeFullDelegation(String sessionId) {
        redisTemplate.delete(FULL_DELEGATION_PREFIX + sessionId);
    }

    public boolean isToolTrusted(String sessionId, Long userId, String toolName) {
        if (sessionId == null || userId == null || toolName == null) {
            return false;
        }
        return String.valueOf(userId).equals(redisTemplate.opsForValue().get(toolTrustKey(sessionId, toolName)));
    }

    public boolean isFullDelegated(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return false;
        }
        return String.valueOf(userId).equals(redisTemplate.opsForValue().get(FULL_DELEGATION_PREFIX + sessionId));
    }

    private String toolTrustKey(String sessionId, String toolName) {
        return TOOL_TRUST_PREFIX + sessionId + ":" + toolName.trim();
    }
}
