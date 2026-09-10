package com.xuejiai.aaf.module.pay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.common.enums.pay.BizOrderStatusEnum;
import com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.module.pay.domain.BizOrder;
import com.xuejiai.aaf.module.pay.domain.PayNotifyTask;
import com.xuejiai.aaf.module.pay.handler.PaySuccessHandler;
import com.xuejiai.aaf.module.pay.repository.PayNotifyTaskRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** COMPENSATION_PENDING 是可靠支付通知终态，不得再次触发履约。 */
class PayNotifyServiceTest extends BaseMockitoUnitTest {

    @Mock private BizOrderService bizOrderService;
    @Mock private PayNotifyTaskRepository taskRepository;
    @Mock private PaySuccessHandler subscriptionHandler;
    @Mock private MessageService messageService;

    private PayNotifyService payNotifyService;

    @BeforeEach
    void setUp() {
        when(subscriptionHandler.bizOrderType())
                .thenReturn(BizOrderTypeEnum.SUBSCRIPTION.getCode());
        payNotifyService =
                new PayNotifyService(
                        bizOrderService,
                        taskRepository,
                        List.of(subscriptionHandler),
                        messageService);
    }

    @Test
    @DisplayName("Given 业务订单已进入人工补偿 When 执行通知任务 Then 标记成功且不重复履约")
    void executeTask_treatsCompensationPendingAsTerminal() {
        var task = new PayNotifyTask();
        task.setId(1L);
        task.setPayOrderId(801L);
        task.setBizOrderType(BizOrderTypeEnum.SUBSCRIPTION.getCode());
        var bizOrder = new BizOrder();
        bizOrder.setStatus(BizOrderStatusEnum.COMPENSATION_PENDING.getCode());
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(bizOrderService.findByPayOrderId(801L)).thenReturn(bizOrder);

        payNotifyService.executeTask(task);

        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getResponse()).contains("已处理");
        assertThat(task.getNotifyTimes()).isEqualTo(1);
        verify(subscriptionHandler, never()).onPaySuccess(801L);
        verify(taskRepository).save(task);
        verify(messageService, never()).send(org.mockito.ArgumentMatchers.any());
    }
}
