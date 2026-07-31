package com.xuejiai.aaf.module.pay.service;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("Given 支付单已成功 When 收到重复微信事件 Then 不返回副作用触发 ID 且不重复保存")
    void should_not_trigger_side_effect_again_when_wx_event_replayed() {
        var order = new PayOrder();
        order.setId(10L);
        order.setMerchantOrderNo("M-1");
        order.setStatus(PayOrderStatusEnum.SUCCESS.getCode());
        when(payOrderRepository.findByMerchantOrderNo("M-1")).thenReturn(Optional.of(order));

        var result = payOrderService.handleWxNotify("M-1", "WX-1");

        assertThat(result).isNull();
        verify(payOrderRepository, never()).save(order);
    }
}
