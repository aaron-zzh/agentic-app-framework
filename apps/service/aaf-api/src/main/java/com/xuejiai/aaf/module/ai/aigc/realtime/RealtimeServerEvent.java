package com.xuejiai.aaf.module.ai.aigc.realtime;

import java.util.Map;

/** v1 标准 JSON 服务端事件。 */
public record RealtimeServerEvent(String type, Map<String, Object> data) {}
