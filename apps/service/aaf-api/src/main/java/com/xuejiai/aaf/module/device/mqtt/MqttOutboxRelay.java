package com.xuejiai.aaf.module.device.mqtt;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.task.DistributedLock;
import com.xuejiai.aaf.module.device.api.DeviceMessagePublisher;

import lombok.extern.slf4j.Slf4j;

/** DeviceMessage outbox 的 MQTT 中继。PUBACK 只推进 DISPATCHED。 */
@Slf4j
@Component
public class MqttOutboxRelay {

    private final MqttConnectionConfig config;
    private final MqttOutboxStateService stateService;
    private final DeviceMessagePublisher publisher;

    public MqttOutboxRelay(
            MqttConnectionConfig config,
            MqttOutboxStateService stateService,
            DeviceMessagePublisher publisher) {
        this.config = config;
        this.stateService = stateService;
        this.publisher = publisher;
    }

    @OrgIgnore
    @Scheduled(fixedDelay = 1_000, initialDelay = 5_000)
    @DistributedLock(key = "device-mqtt-outbox-relay", ttlSeconds = 120)
    public void relay() {
        stateService.expireDue();
        stateService.markAckTimeouts(config.ackTimeoutSeconds());
        if (!config.enabled()) {
            return;
        }
        for (var message : stateService.pending()) {
            if (!message.getExpiresAt().isAfter(LocalDateTime.now())) {
                stateService.markExpired(message.getId());
                continue;
            }
            try {
                publisher.publish(message);
                stateService.markDispatched(message.getId());
            } catch (RuntimeException exception) {
                stateService.markDeliveryUnknown(message.getId(), exception.getMessage());
                log.warn("MQTT outbox 发布失败，保留至有效期内重试: id={}", message.getId());
            }
        }
    }
}
