package com.xuejiai.aaf.autodev.agent;

import java.time.LocalDateTime;

/** 会话列表响应。 */
public record AutodevSessionVO(
        Long id, String sessionId, String agentRole, String status, LocalDateTime createTime) {}
