package com.xuejiai.aaf.module.device.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.device.ErrorCodeConstants.DEVICE_MESSAGE_STATUS_TRANSITION_INVALID;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.device.DeviceMessageStatusEnum;
import com.xuejiai.aaf.module.device.domain.DeviceMessage;

/** 设备消息状态机；同状态写入视为幂等。 */
@Service
public class DeviceMessageStateService {

    private static final Map<String, Set<String>> STATUS_TRANSITIONS =
            Map.of(
                    DeviceMessageStatusEnum.QUEUED.getCode(),
                    Set.of(
                            DeviceMessageStatusEnum.DISPATCHED.getCode(),
                            DeviceMessageStatusEnum.DELIVERY_UNKNOWN.getCode(),
                            DeviceMessageStatusEnum.EXPIRED.getCode(),
                            DeviceMessageStatusEnum.CANCELLED.getCode()),
                    DeviceMessageStatusEnum.DISPATCHED.getCode(),
                    Set.of(
                            DeviceMessageStatusEnum.ACCEPTED.getCode(),
                            DeviceMessageStatusEnum.DELIVERY_UNKNOWN.getCode(),
                            DeviceMessageStatusEnum.EXPIRED.getCode(),
                            DeviceMessageStatusEnum.CANCELLED.getCode()),
                    DeviceMessageStatusEnum.DELIVERY_UNKNOWN.getCode(),
                    Set.of(
                            DeviceMessageStatusEnum.DISPATCHED.getCode(),
                            DeviceMessageStatusEnum.ACCEPTED.getCode(),
                            DeviceMessageStatusEnum.EXPIRED.getCode(),
                            DeviceMessageStatusEnum.CANCELLED.getCode()),
                    DeviceMessageStatusEnum.ACCEPTED.getCode(),
                    Set.of(
                            DeviceMessageStatusEnum.SUCCEEDED.getCode(),
                            DeviceMessageStatusEnum.FAILED.getCode(),
                            DeviceMessageStatusEnum.EXPIRED.getCode(),
                            DeviceMessageStatusEnum.CANCELLED.getCode()));

    public boolean transition(DeviceMessage message, DeviceMessageStatusEnum target) {
        var current = message.getStatus();
        if (target.getCode().equals(current)) {
            return false;
        }
        if (!STATUS_TRANSITIONS.getOrDefault(current, Set.of()).contains(target.getCode())) {
            throw exception(DEVICE_MESSAGE_STATUS_TRANSITION_INVALID, current, target.getCode());
        }
        message.setStatus(target.getCode());
        return true;
    }
}
