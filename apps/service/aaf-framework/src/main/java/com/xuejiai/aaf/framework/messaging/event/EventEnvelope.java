package com.xuejiai.aaf.framework.messaging.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局事件 SSE 的唯一 wire 信封。
 *
 * @param id 事件 id，即 SSE 游标
 * @param type 事件类型，{域}.{资源}.{动作}
 * @param refs 关联键值，值为正整数 ID
 * @param payload 状态摘要，不放权威业务数据
 */
public record EventEnvelope(
        Long id, String type, Map<String, Long> refs, Map<String, Object> payload) {

    public EventEnvelope {
        // payload 来自 JSON 反序列化，值可能为 null；Map.copyOf 会拒绝 null 值，故用容忍 null 的只读拷贝
        refs = refs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(refs));
        payload =
                payload == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    static EventEnvelope from(SysEvent event) {
        return new EventEnvelope(
                event.getId(), event.getEventType(), event.getRefs(), event.getPayload());
    }
}
