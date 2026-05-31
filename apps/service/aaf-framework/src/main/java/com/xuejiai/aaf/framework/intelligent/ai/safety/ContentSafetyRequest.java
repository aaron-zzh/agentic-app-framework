package com.xuejiai.aaf.framework.intelligent.ai.safety;

import java.util.Map;

/** 生成式内容安全审查请求。 */
public record ContentSafetyRequest(
        String toolName,
        String category,
        String sessionId,
        Long userId,
        String prompt,
        Map<String, Object> metadata) {}
