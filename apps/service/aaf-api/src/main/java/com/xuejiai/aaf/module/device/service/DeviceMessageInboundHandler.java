package com.xuejiai.aaf.module.device.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.device.DeviceMessageStatusEnum;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.device.api.DeviceInboundHandler;
import com.xuejiai.aaf.module.device.domain.DeviceMessage;
import com.xuejiai.aaf.module.device.repository.DeviceMessageRepository;

/** 设备 ACK 与执行结果处理器。重复 messageId/commandId 更新为幂等操作。 */
@Component
@Order(0)
public class DeviceMessageInboundHandler implements DeviceInboundHandler {

    private static final Set<String> TERMINAL =
            Set.of(
                    DeviceMessageStatusEnum.SUCCEEDED.getCode(),
                    DeviceMessageStatusEnum.FAILED.getCode(),
                    DeviceMessageStatusEnum.EXPIRED.getCode(),
                    DeviceMessageStatusEnum.CANCELLED.getCode());

    private final DeviceMessageRepository messageRepository;
    private final DeviceMessageStateService stateService;

    public DeviceMessageInboundHandler(
            DeviceMessageRepository messageRepository, DeviceMessageStateService stateService) {
        this.messageRepository = messageRepository;
        this.stateService = stateService;
    }

    @Override
    public boolean supports(InboundType type) {
        return type == InboundType.ACK || type == InboundType.RESULT;
    }

    @Override
    @Transactional
    public void handle(DeviceInboundMessage inbound) {
        var commandId =
                inbound.commandId() == null ? text(inbound, "commandId") : inbound.commandId();
        var commandMessageId = text(inbound, "commandMessageId");
        if (commandId == null && commandMessageId == null) {
            return;
        }
        var message =
                commandId != null
                        ? messageRepository.findLockedByCommandId(commandId).orElse(null)
                        : messageRepository.findLockedByMessageId(commandMessageId).orElse(null);
        if (message == null || !message.getDeviceId().equals(inbound.deviceId())) {
            return;
        }
        if (inbound.type() == InboundType.ACK) {
            markAccepted(message);
            return;
        }
        markResult(message, inbound);
    }

    private void markAccepted(DeviceMessage message) {
        if (TERMINAL.contains(message.getStatus())
                || DeviceMessageStatusEnum.ACCEPTED.getCode().equals(message.getStatus())) {
            return;
        }
        stateService.transition(message, DeviceMessageStatusEnum.ACCEPTED);
        message.setAcceptedAt(LocalDateTime.now());
        messageRepository.save(message);
    }

    private void markResult(DeviceMessage message, DeviceInboundMessage inbound) {
        var outcome = outcome(inbound);
        var target =
                switch (outcome) {
                    case "FAILED", "ERROR" -> DeviceMessageStatusEnum.FAILED;
                    case "CANCELLED" -> DeviceMessageStatusEnum.CANCELLED;
                    default -> DeviceMessageStatusEnum.SUCCEEDED;
                };
        if (TERMINAL.contains(message.getStatus())) {
            if (!stateService.transition(message, target)) {
                return;
            }
        }
        var now = LocalDateTime.now();
        if (!DeviceMessageStatusEnum.ACCEPTED.getCode().equals(message.getStatus())) {
            stateService.transition(message, DeviceMessageStatusEnum.ACCEPTED);
            message.setAcceptedAt(now);
        }
        stateService.transition(message, target);
        message.setCompletedAt(now);
        message.setResult(JsonUtils.toJsonString(inbound.payload()));
        if (target == DeviceMessageStatusEnum.FAILED) {
            message.setFailureReason(inbound.resultCode());
        }
        messageRepository.save(message);
    }

    private static String outcome(DeviceInboundMessage inbound) {
        var value = text(inbound, "result");
        if (value == null || value.isBlank()) {
            value = text(inbound, "status");
        }
        if (value == null || value.isBlank()) {
            value = inbound.resultCode();
        }
        return value == null || value.isBlank() ? "SUCCEEDED" : value.toUpperCase(Locale.ROOT);
    }

    private static String text(DeviceInboundMessage message, String field) {
        var value = message.payload().get(field);
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
