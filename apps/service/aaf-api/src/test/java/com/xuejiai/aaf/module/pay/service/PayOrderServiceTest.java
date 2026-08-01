package com.xuejiai.aaf.module.pay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;
import com.xuejiai.aaf.framework.engine.settlement.SettlementEngine;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.pay.domain.PayOrder;
import com.xuejiai.aaf.module.pay.repository.PayOrderRepository;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PayOrderServiceTest {

    @Mock private PayOrderRepository payOrderRepository;
    @Mock private SettlementEngine settlementEngine;
    @Mock private BrokerageUserRepository brokerageUserRepository;
    @Mock private UserRepository userRepository;
    @Mock private BizOrderService bizOrderService;
    @Mock private OperatorContext operatorContext;

    private PayOrderService payOrderService;

    @BeforeEach
    void setUp() {
        payOrderService =
                new PayOrderService(
                        payOrderRepository,
                        settlementEngine,
                        Optional.empty(),
                        brokerageUserRepository,
                        userRepository,
                        bizOrderService,
                        operatorContext);
    }

    @Test
    @DisplayName("Given 支付单已成功 When 收到重复微信事件 Then 原子迁移未命中且不返回副作用触发 ID")
    void should_not_trigger_side_effect_again_when_wx_event_replayed() {
        // M3：重复/并发回调下 UPDATE ... WHERE status = WAITING 命中 0 行
        when(payOrderRepository.transitionStatus(
                        eq("M-1"),
                        eq(PayOrderStatusEnum.WAITING.getCode()),
                        eq(PayOrderStatusEnum.SUCCESS.getCode()),
                        eq("WX-1"),
                        any()))
                .thenReturn(0);

        var result = payOrderService.handleWxNotify("M-1", "WX-1");

        assertThat(result).isNull();
        verify(payOrderRepository, never()).findByMerchantOrderNo("M-1");
    }

    @Test
    @DisplayName("Given 支付单待支付 When 首个回调到达 Then 抢到原子状态迁移并返回支付单 ID")
    void should_return_pay_order_id_when_transition_won() {
        var order = new PayOrder();
        order.setId(10L);
        order.setMerchantOrderNo("M-2");
        order.setStatus(PayOrderStatusEnum.SUCCESS.getCode());
        when(payOrderRepository.transitionStatus(
                        eq("M-2"),
                        eq(PayOrderStatusEnum.WAITING.getCode()),
                        eq(PayOrderStatusEnum.SUCCESS.getCode()),
                        eq("ALI-1"),
                        any()))
                .thenReturn(1);
        when(payOrderRepository.findByMerchantOrderNo("M-2")).thenReturn(Optional.of(order));

        var result = payOrderService.handleAlipayNotify("M-2", "ALI-1");

        assertThat(result).isEqualTo(10L);
    }
}
