package com.xuejiai.aaf.module.device.mqtt;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.device.DeviceMessageStatusEnum;
import com.xuejiai.aaf.module.device.domain.DeviceMessage;
import com.xuejiai.aaf.module.device.repository.DeviceMessageRepository;
import com.xuejiai.aaf.module.device.service.DeviceMessageStateService;

/** MQTT outbox 短事务状态服务，网络发布不在数据库事务中执行。 */
@Service
public class MqttOutboxStateService {

    private static final Collection<String> RETRYABLE =
            List.of(
                    DeviceMessageStatusEnum.QUEUED.getCode(),
                    DeviceMessageStatusEnum.DELIVERY_UNKNOWN.getCode());
    private static final Collection<String> EXPIRABLE =
            List.of(
                    DeviceMessageStatusEnum.QUEUED.getCode(),
                    DeviceMessageStatusEnum.DISPATCHED.getCode(),
                    DeviceMessageStatusEnum.ACCEPTED.getCode(),
                    DeviceMessageStatusEnum.DELIVERY_UNKNOWN.getCode());

    private final DeviceMessageRepository messageRepository;
    private final DeviceMessageStateService stateService;

    public MqttOutboxStateService(
            DeviceMessageRepository messageRepository, DeviceMessageStateService stateService) {
        this.messageRepository = messageRepository;
        this.stateService = stateService;
    }

    @Transactional(readOnly = true)
    public List<DeviceMessage> pending() {
        return messageRepository.findTop100ByStatusInOrderByIssuedAtAsc(RETRYABLE);
    }

    @Transactional
    public void expireDue() {
        var expired =
                messageRepository.findTop100ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
                        EXPIRABLE, LocalDateTime.now());
        expired.forEach(this::markExpiredEntity);
    }

    @Transactional
    public void markAckTimeouts(int timeoutSeconds) {
        var cutoff = LocalDateTime.now().minusSeconds(timeoutSeconds);
        var timedOut =
                messageRepository.findTop100ByStatusAndDispatchedAtBeforeOrderByDispatchedAtAsc(
                        DeviceMessageStatusEnum.DISPATCHED.getCode(), cutoff);
        for (var message : timedOut) {
            if (message.getExpiresAt().isAfter(LocalDateTime.now())) {
                stateService.transition(message, DeviceMessageStatusEnum.DELIVERY_UNKNOWN);
                message.setFailureReason("设备未在确认窗口内返回 ACK");
            }
        }
    }

    @Transactional
    public void markDispatched(Long id) {
        var message = messageRepository.findById(id).orElse(null);
        if (message == null || !RETRYABLE.contains(message.getStatus())) {
            return;
        }
        stateService.transition(message, DeviceMessageStatusEnum.DISPATCHED);
        message.setDispatchedAt(LocalDateTime.now());
        message.setFailureReason(null);
    }

    @Transactional
    public void markDeliveryUnknown(Long id, String reason) {
        var message = messageRepository.findById(id).orElse(null);
        if (message == null || !RETRYABLE.contains(message.getStatus())) {
            return;
        }
        stateService.transition(message, DeviceMessageStatusEnum.DELIVERY_UNKNOWN);
        message.setFailureReason(truncate(reason, 500));
    }

    @Transactional
    public void markExpired(Long id) {
        messageRepository.findById(id).ifPresent(this::markExpiredEntity);
    }

    private void markExpiredEntity(DeviceMessage message) {
        if (!EXPIRABLE.contains(message.getStatus())) {
            return;
        }
        stateService.transition(message, DeviceMessageStatusEnum.EXPIRED);
        message.setFailureReason("消息已超过有效期，不再下发");
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
