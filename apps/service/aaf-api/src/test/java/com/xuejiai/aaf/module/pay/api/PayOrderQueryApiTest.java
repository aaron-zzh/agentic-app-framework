package com.xuejiai.aaf.module.pay.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;

/** live checkout 只接受状态 WAITING 且有明确未来过期时间的支付单。 */
class PayOrderQueryApiTest {

    private static final Long PAY_ORDER_ID = 801L;
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 10, 7, 0);

    @Test
    @DisplayName("Given WAITING 且未来过期 When 判断 live Then 返回 true")
    void isLive_acceptsExplicitFutureExpiry() {
        var api = api(PayOrderStatusEnum.WAITING.getCode(), now.plusMinutes(10));

        assertThat(api.isLive(PAY_ORDER_ID, now)).isTrue();
    }

    @Test
    @DisplayName("Given WAITING 但过期时间为空 When 判断 live Then 返回 false")
    void isLive_rejectsNullExpiry() {
        var api = api(PayOrderStatusEnum.WAITING.getCode(), null);

        assertThat(api.isLive(PAY_ORDER_ID, now)).isFalse();
    }

    @Test
    @DisplayName("Given WAITING 但已过期 When 判断 live Then 返回 false")
    void isLive_rejectsExpiredOrder() {
        var api = api(PayOrderStatusEnum.WAITING.getCode(), now.minusSeconds(1));

        assertThat(api.isLive(PAY_ORDER_ID, now)).isFalse();
    }

    @Test
    @DisplayName("Given 支付单已成功 When 判断 live Then 返回 false")
    void isLive_rejectsSuccessfulOrder() {
        var api = api(PayOrderStatusEnum.SUCCESS.getCode(), now.plusMinutes(10));

        assertThat(api.isLive(PAY_ORDER_ID, now)).isFalse();
    }

    private PayOrderQueryApi api(Integer status, LocalDateTime expireTime) {
        return payOrderId ->
                Optional.of(
                        new PayOrderQueryApi.PayOrderSnapshot(
                                payOrderId, 2900L, status, expireTime, null));
    }
}
