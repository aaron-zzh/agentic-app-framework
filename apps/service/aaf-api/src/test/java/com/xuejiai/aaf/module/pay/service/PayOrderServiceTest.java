package com.xuejiai.aaf.module.pay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.module.pay.domain.PayOrder;
import com.xuejiai.aaf.module.pay.repository.PayOrderRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 支付订单服务单元测试。 */
class PayOrderServiceTest extends BaseMockitoUnitTest {

    @Mock
    private PayOrderRepository orderRepository;

    @InjectMocks
    private PayOrderService orderService;

    @Test
    void createOrder_应创建待支付订单() {
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var order = orderService.createOrder(1L, "test-biz-001", 100L, "wechat");

        assertThat(order).isNotNull();
        assertThat(order.getUserId()).isEqualTo(1L);
        assertThat(order.getAmount()).isEqualTo(100L);
        verify(orderRepository).save(any(PayOrder.class));
    }

    @Test
    void getOrder_存在时应返回() {
        var order = new PayOrder();
        order.setId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        var result = orderService.getOrder(1L);

        assertThat(result).isPresent();
    }
}
