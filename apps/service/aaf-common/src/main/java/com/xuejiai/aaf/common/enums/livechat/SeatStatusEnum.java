package com.xuejiai.aaf.common.enums.livechat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 坐席状态枚举。 */
@Getter
@AllArgsConstructor
public enum SeatStatusEnum {
    ONLINE("online", "在线"),
    BUSY("busy", "忙碌"),
    OFFLINE("offline", "离线");

    private final String code;
    private final String label;
}
