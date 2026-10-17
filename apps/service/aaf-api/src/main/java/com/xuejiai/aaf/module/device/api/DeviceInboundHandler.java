package com.xuejiai.aaf.module.device.api;

import java.time.Instant;
import java.util.Map;

/**
 * MQTT 入站消息处理扩展点。
 *
 * <p>分发器按 {@link #supports(InboundType)} 与 {@link #supportsScenario(String)} 同时匹配后才调用； 场景包的处理器必须覆盖
 * {@code supportsScenario}，只处理自己场景的设备，通用处理器保持默认即可。
 */
public interface DeviceInboundHandler {

    boolean supports(InboundType type);

    /** 是否处理指定场景的设备消息；默认处理全部场景。 */
    default boolean supportsScenario(String scenarioKey) {
        return true;
    }

    void handle(DeviceInboundMessage message);

    enum InboundType {
        PRESENCE,
        STATUS,
        ALERT,
        ACK,
        RESULT,
        CAPABILITIES
    }

    record DeviceInboundMessage(
            Long deviceId,
            String deviceKey,
            String scenarioKey,
            InboundType type,
            String messageId,
            String eventId,
            String commandId,
            String correlationId,
            String bootId,
            long statusSequence,
            Instant deviceOccurredAt,
            String resultCode,
            Map<String, Object> payload) {}
}
