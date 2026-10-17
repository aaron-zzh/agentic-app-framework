package com.xuejiai.aaf.module.device.api;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备下行消息业务入口。调用方只能描述语义指令，不能传入 MQTT 主题或 QoS。
 *
 * <p>场景包通过本接口下发、查询和收尾自己的消息，不直接访问设备消息的实体、仓储与状态机。
 */
public interface DeviceMessageApi {

    DeviceMessageRef enqueue(DeviceMessageCommand command);

    Optional<DeviceMessageView> findByCommandId(String commandId);

    Optional<DeviceMessageView> findByMessageId(String messageId);

    /** 按关联 ID 前缀查找处于指定状态的消息（最多 100 条，按签发时间升序，加行锁，需在事务内调用）。 */
    List<DeviceMessageView> listByCorrelationPrefix(
            String correlationPrefix, Collection<String> statuses);

    /** 查找指定能力下已过期、处于指定状态且尚未写入结果的消息（最多 100 条，按过期时间升序，加行锁，需在事务内调用）。 */
    List<DeviceMessageView> listExpiredWithoutResult(
            String capability,
            String correlationPrefix,
            LocalDateTime expiredBefore,
            Collection<String> statuses);

    /** 将消息置为已撤销并写入结果；非法状态流转抛出业务异常。 */
    void markCancelled(Long messageId, String resultJson);

    /** 将消息置为已过期；{@code failureReason} 与 {@code resultJson} 为 null 时不写入。 */
    void markExpired(Long messageId, String failureReason, String resultJson);

    record DeviceMessageCommand(
            Long deviceId,
            String scenarioKey,
            Long subjectId,
            String commandId,
            String direction,
            String capability,
            String action,
            Map<String, Object> arguments,
            LocalDateTime expiresAt,
            String correlationId,
            String traceId) {}

    record DeviceMessageRef(
            Long id, String commandId, String messageId, String status, LocalDateTime expiresAt) {}

    /** 消息只读视图。{@code payload} 与 {@code result} 为 JSON 文本。 */
    record DeviceMessageView(
            Long id,
            Long deviceId,
            String commandId,
            String messageId,
            String capability,
            String correlationId,
            String traceId,
            String status,
            String payload,
            LocalDateTime acceptedAt,
            String result,
            String failureReason) {}
}
