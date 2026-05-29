package com.xuejiai.aaf.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xuejiai.aaf.module.pay.service.PayOrderService;

/** 支付核心流程集成测试。 */
@SpringBootTest
@ActiveProfiles("test")
class PayFlowIT {

    @Autowired
    private PayOrderService payOrderService;

    @Test
    void 创建订单_支付成功_权益到账() {
        // 创建订单
        var order = payOrderService.createOrder(1L, "IT-biz-001", 9900L, "mock");
        assertThat(order).isNotNull();
        assertThat(order.getAmount()).isEqualTo(9900L);

        // 模拟支付回调
        payOrderService.handlePaySuccess(order.getId(), "mock-tx-001");

        // 验证订单状态
        var updated = payOrderService.getOrder(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("paid");
    }
}
