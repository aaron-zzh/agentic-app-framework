package com.xuejiai.aaf.common.enums.pay;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 转账订单状态枚举，对应字典 pay_transfer_status。 */
@Getter
@AllArgsConstructor
public enum PayTransferStatusEnum {
    WAITING(0, "等待转账"),
    IN_PROGRESS(10, "转账进行中"),
    SUCCESS(20, "转账成功"),
    FAILURE(30, "转账失败");

    private final Integer code;
    private final String label;
}
