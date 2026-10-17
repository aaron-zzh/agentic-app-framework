package com.xuejiai.aaf.module.elder.guard.service;

import java.util.Locale;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.device.api.DeviceInboundHandler;
import com.xuejiai.aaf.module.device.api.DeviceMessageApi;
import com.xuejiai.aaf.module.elder.scenario.ElderDeviceScenario;

/** 养老场景的设备用药结果闭环。通用设备层只维护消息状态。 */
@Component
@Order(100)
public class ElderMedicationInboundHandler implements DeviceInboundHandler {

    private final DeviceMessageApi deviceMessageApi;
    private final MedicationReminderService reminderService;

    public ElderMedicationInboundHandler(
            DeviceMessageApi deviceMessageApi, MedicationReminderService reminderService) {
        this.deviceMessageApi = deviceMessageApi;
        this.reminderService = reminderService;
    }

    @Override
    public boolean supports(InboundType type) {
        return type == InboundType.ACK || type == InboundType.RESULT;
    }

    @Override
    public boolean supportsScenario(String scenarioKey) {
        return ElderDeviceScenario.KEY.equals(scenarioKey);
    }

    @Override
    @Transactional
    public void handle(DeviceInboundMessage inbound) {
        if (inbound.type() != InboundType.RESULT) {
            return;
        }
        var commandId =
                inbound.commandId() == null ? text(inbound, "commandId") : inbound.commandId();
        var commandMessageId = text(inbound, "commandMessageId");
        var message =
                commandId != null
                        ? deviceMessageApi.findByCommandId(commandId).orElse(null)
                        : commandMessageId == null
                                ? null
                                : deviceMessageApi.findByMessageId(commandMessageId).orElse(null);
        if (message == null || !message.deviceId().equals(inbound.deviceId())) {
            return;
        }
        reminderService.recordDeviceResult(message, outcome(inbound), inbound.deviceOccurredAt());
    }

    private static String outcome(DeviceInboundMessage inbound) {
        var value = text(inbound, "result");
        if (value == null) {
            value = text(inbound, "status");
        }
        if (value == null) {
            value = inbound.resultCode();
        }
        return value == null || value.isBlank() ? "SUCCEEDED" : value.toUpperCase(Locale.ROOT);
    }

    private static String text(DeviceInboundMessage message, String field) {
        var value = message.payload().get(field);
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
