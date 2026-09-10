package com.xuejiai.aaf.module.pay.api;

import java.time.LocalDateTime;
import java.util.Optional;

import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;

/** 支付单跨模块只读查询契约。 */
public interface PayOrderQueryApi {

    Optional<PayOrderSnapshot> findSnapshot(Long payOrderId);

    default boolean isLive(Long payOrderId, LocalDateTime now) {
        return findSnapshot(payOrderId)
                .filter(order -> PayOrderStatusEnum.WAITING.getCode().equals(order.status()))
                .filter(order -> order.expireTime() != null && order.expireTime().isAfter(now))
                .isPresent();
    }

    record PayOrderSnapshot(
            Long id,
            Long amount,
            Integer status,
            LocalDateTime expireTime,
            LocalDateTime successTime) {}
}
