package com.xuejiai.aaf.module.ai.aigc.event.api;

import java.util.Map;

/** AIGC Activity SSE 唯一 wire envelope。事件类型只存在于 type 字段。 */
public record AigcActivityEnvelope(
        Long id,
        String type,
        Long projectId,
        Long executionRunId,
        Long taskId,
        Long mediaVersionId,
        Long objectVersionId,
        Long reviewId,
        Long workId,
        Long publicationId,
        Long conversationId,
        Map<String, Object> payload) {

    public AigcActivityEnvelope {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
