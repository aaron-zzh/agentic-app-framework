package com.xuejiai.aaf.module.ai.aigc.event.api;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        // payload 来自 JSON 反序列化，值可能为 null；Map.copyOf 会拒绝 null 值，故用可容忍 null 的只读拷贝
        payload =
                payload == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
