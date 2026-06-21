package com.xuejiai.aaf.module.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.enums.billing.SubscriptionOperationEnum;
import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.engine.credit.UpgradeSettlement;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.domain.SubscriptionRecord;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRecordRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.brokerage.service.BrokerageService;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.service.PayOrderService;
import com.xuejiai.aaf.module.pay.vo.BizOrderVO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** AAF-099 SubscriptionService 单元测试：覆盖 cancel/downgrade/cancelPending/upgrade 路径。 */
class SubscriptionServiceTest extends BaseMockitoUnitTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionRecordRepository recordRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private BizOrderService bizOrderService;
    @Mock private PayOrderService payOrderService;
    @Mock private EntitlementService entitlementService;
    @Mock private CreditService creditService;
    @Mock private BrokerageService brokerageService;
    @Mock private UserRepository userRepository;

    @InjectMocks private SubscriptionService subscriptionService;

    private Subscription activeSub;
    private SubscriptionPlan proPlan;
    private SubscriptionPlan teamPlan;
    private SubscriptionPlan freePlan;

    @BeforeEach
    void setUp() {
        // PRO 月付 29 元，月度 200 积分
        proPlan = new SubscriptionPlan();
        proPlan.setId(20L);
        proPlan.setCode("PRO");
        proPlan.setName("PRO");
        proPlan.setPrice(2900L); // 29 元 = 2900 分
        proPlan.setDurationDays(30);
        proPlan.setMonthlyCredits(200L);

        // TEAM 月付 99 元，月度 400 积分
        teamPlan = new SubscriptionPlan();
        teamPlan.setId(30L);
        teamPlan.setCode("TEAM");
        teamPlan.setName("TEAM");
        teamPlan.setPrice(9900L);
        teamPlan.setDurationDays(30);
        teamPlan.setMonthlyCredits(400L);

        freePlan = new SubscriptionPlan();
        freePlan.setId(10L);
        freePlan.setCode("FREE");
        freePlan.setName("免费版");
        freePlan.setPrice(0L);
        freePlan.setDurationDays(0);
        freePlan.setMonthlyCredits(0L);

        // 用户当前订阅 PRO 月付，已过 5 天，剩 25 天
        activeSub = new Subscription();
        activeSub.setId(500L);
        activeSub.setUserId(100L);
        activeSub.setPlanId(proPlan.getId());
        activeSub.setStartAt(LocalDateTime.now().minusDays(5));
        activeSub.setEndAt(LocalDateTime.now().plusDays(25));
        activeSub.setStatus(SubscriptionStatusEnum.ACTIVE.getCode());
        activeSub.setAutoRenew(true);
        activeSub.setPendingYearly(false);

        lenient()
                .when(subscriptionRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            Subscription s = inv.getArgument(0);
                            if (s.getId() == null) {
                                s.setId(System.nanoTime());
                            }
                            return s;
                        });
        lenient()
                .when(recordRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            SubscriptionRecord r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(System.nanoTime());
                            }
                            return r;
                        });
    }

    // ========== F1 取消订阅 ==========

    @Test
    @DisplayName(
            "Given 用户有生效订阅 When cancel Then 设置 cancelled_at + auto_renew=false 且 status 仍 ACTIVE")
    void cancel_setsCancelledAtAndKeepsActive() {
        when(subscriptionRepository.findByUserIdAndStatus(
                        100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.of(activeSub));

        var result = subscriptionService.cancel(100L);

        assertThat(result.getCancelledAt()).isNotNull();
        assertThat(result.getAutoRenew()).isFalse();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatusEnum.ACTIVE.getCode());
    }

    @Test
    @DisplayName("Given 用户已取消订阅 When 再次 cancel Then 幂等不重写")
    void cancel_idempotent() {
        var alreadyCancelled = LocalDateTime.now().minusDays(1);
        activeSub.setCancelledAt(alreadyCancelled);
        when(subscriptionRepository.findByUserIdAndStatus(
                        100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.of(activeSub));

        var result = subscriptionService.cancel(100L);

        assertThat(result.getCancelledAt()).isEqualTo(alreadyCancelled);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given 无生效订阅 When cancel Then 抛 NOT_FOUND")
    void cancel_noActiveSub_throws() {
        when(subscriptionRepository.findByUserIdAndStatus(
                        100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.cancel(100L))
                .isInstanceOf(BusinessException.class);
    }

    // ========== F1b 降级排队 ==========

    @Test
    @DisplayName("Given 用户 PRO 月付 When 降级到 FREE Then 设置 pendingPlanId 不动 status")
    void downgrade_setsPending() {
        when(subscriptionRepository.findByUserIdAndStatus(
                        100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.of(activeSub));
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));

        var result = subscriptionService.downgrade(100L, "FREE", false);

        assertThat(result.getPendingPlanId()).isEqualTo(freePlan.getId());
        assertThat(result.getPendingYearly()).isFalse();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatusEnum.ACTIVE.getCode());
    }

    @Test
    @DisplayName("Given 用户 PRO 月付 When 调降级接口请求升级到 TEAM Then 拒绝")
    void downgrade_rejectsUpgradeRequest() {
        when(subscriptionRepository.findByUserIdAndStatus(
                        100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.of(activeSub));
        when(planRepository.findByCode("TEAM")).thenReturn(Optional.of(teamPlan));
        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));

        assertThatThrownBy(() -> subscriptionService.downgrade(100L, "TEAM", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("升级");
    }

    @Test
    @DisplayName("Given 已设 pending When cancelPending Then 清除 pendingPlanId/pendingYearly")
    void cancelPending_clears() {
        activeSub.setPendingPlanId(freePlan.getId());
        activeSub.setPendingYearly(false);
        when(subscriptionRepository.findByUserIdAndStatus(
                        100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.of(activeSub));

        var result = subscriptionService.cancelPending(100L);

        assertThat(result.getPendingPlanId()).isNull();
        assertThat(result.getPendingYearly()).isFalse();
    }

    // ========== F2 升级（差价 + 三笔流水） ==========

    @Test
    @DisplayName("Given 用户 PRO 月付剩 25 天 When 升级到 TEAM 月付 Then 按时间比例补差价 + 创建支付订单")
    void upgrade_calculatesProrate() {
        when(subscriptionRepository.findByUserIdAndStatus(
                        100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.of(activeSub));
        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
        when(planRepository.findByCode("TEAM")).thenReturn(Optional.of(teamPlan));

        // 旧实付 2900，按 25/30 抵扣 ≈ 2417；新价 9900；差价 ≈ 7483
        // 创建 BizOrder/PayOrder mock
        var bizOrderVO = mock(BizOrderVO.class);
        when(bizOrderVO.id()).thenReturn(701L);
        when(bizOrderVO.orderNo()).thenReturn("BIZUPGRADE001");
        when(bizOrderService.create(eq(100L), any())).thenReturn(bizOrderVO);
        var payOrderVO = mock(PayOrderVO.class);
        when(payOrderVO.id()).thenReturn(801L);
        when(payOrderService.create(any())).thenReturn(payOrderVO);
        when(payOrderService.isSuccess(801L)).thenReturn(false);

        PayOrderVO result = subscriptionService.upgrade(100L, "TEAM", "MOCK", false);

        assertThat(result).isNotNull();
        // 验证 SubscriptionRecord 被保存为 UPGRADE 操作
        ArgumentCaptor<SubscriptionRecord> recordCaptor =
                ArgumentCaptor.forClass(SubscriptionRecord.class);
        verify(recordRepository).save(recordCaptor.capture());
        var savedRecord = recordCaptor.getValue();
        assertThat(savedRecord.getOperation())
                .isEqualTo(SubscriptionOperationEnum.UPGRADE.getCode());
        assertThat(savedRecord.getPlanId()).isEqualTo(teamPlan.getId());
        // 应付差价 > 0 且 < 新套餐全价（按时间比例抵扣）
        assertThat(savedRecord.getPayPrice()).isPositive().isLessThan(teamPlan.getPrice());
        assertThat(savedRecord.getYearly()).isFalse();
        assertThat(savedRecord.getPayStatus()).isEqualTo("UNPAID");

        // 创建了 BizOrder + PayOrder
        verify(bizOrderService).create(eq(100L), any());
        verify(payOrderService).create(any());
        verify(bizOrderService).bindPayOrder(701L, 801L);
    }

    @Test
    @DisplayName("Given 旧月度 200 已用 100 升级到 TEAM 月度 400 When 支付成功 Then 三笔积分流水写入，最终 balance=300")
    void upgrade_writesThreeCreditTransactions() {
        // 设置：用户当前 PRO 月付，sub 还在
        when(subscriptionRepository.findByUserIdAndStatus(
                        100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.of(activeSub));
        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
        when(planRepository.findByCode("TEAM")).thenReturn(Optional.of(teamPlan));

        // 创建支付订单 + mock 同步成功
        var bizOrderVO = mock(BizOrderVO.class);
        when(bizOrderVO.id()).thenReturn(701L);
        when(bizOrderVO.orderNo()).thenReturn("BIZUPGRADE001");
        when(bizOrderService.create(eq(100L), any())).thenReturn(bizOrderVO);
        var payOrderVO = mock(PayOrderVO.class);
        when(payOrderVO.id()).thenReturn(801L);
        when(payOrderService.create(any())).thenReturn(payOrderVO);
        when(payOrderService.isSuccess(801L)).thenReturn(true);

        // mock onPaySuccess 路径需要的查询
        var bizOrder = new com.xuejiai.aaf.module.pay.domain.BizOrder();
        bizOrder.setId(701L);
        bizOrder.setUserId(100L);
        bizOrder.setOrderType(
                com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum.SUBSCRIPTION.getCode());
        when(bizOrderService.findByPayOrderId(801L)).thenReturn(bizOrder);

        // 模拟 record 在 onPaySuccess 中被检索：将 UPGRADE 流水标记 UNPAID 后被找到
        var record = new SubscriptionRecord();
        record.setId(601L);
        record.setUserId(100L);
        record.setPlanId(teamPlan.getId());
        record.setOperation(SubscriptionOperationEnum.UPGRADE.getCode());
        record.setPayOrderId(801L);
        record.setPayPrice(7000L);
        record.setPayStatus("UNPAID");
        record.setYearly(false);
        when(recordRepository.findByPayOrderIdAndPayStatus(801L, "UNPAID"))
                .thenReturn(java.util.List.of(record));
        when(planRepository.findById(teamPlan.getId())).thenReturn(Optional.of(teamPlan));

        // mock creditService.settleSubscriptionUpgrade 返回的结算结果
        when(creditService.settleSubscriptionUpgrade(eq(100L), eq(400L), anyLong(), any()))
                .thenReturn(new UpgradeSettlement(11L, 12L, 13L, 100L));

        // 调用：触发 upgrade → onPaySuccess → activateUpgrade
        subscriptionService.upgrade(100L, "TEAM", "MOCK", false);

        // 验证三笔流水入口被调用：newAmount=400, expireAt 非空
        ArgumentCaptor<Long> amountCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> subIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(creditService, times(1))
                .settleSubscriptionUpgrade(
                        eq(100L), amountCaptor.capture(), subIdCaptor.capture(), any());
        assertThat(amountCaptor.getValue()).isEqualTo(400L); // 新月度 400
        assertThat(subIdCaptor.getValue()).isNotNull();

        // 验证旧订阅被标记 CANCELLED + cancelled_at
        assertThat(activeSub.getStatus()).isEqualTo(SubscriptionStatusEnum.CANCELLED.getCode());
        assertThat(activeSub.getCancelledAt()).isNotNull();

        // 验证权益被重新实例化
        verify(entitlementService).instantiateQuotas(100L, teamPlan.getId());
    }

    @Test
    @DisplayName("Given 用户 PRO 月付 When 调升级接口请求降级到 FREE Then 拒绝")
    void upgrade_rejectsDowngradeRequest() {
        when(subscriptionRepository.findByUserIdAndStatus(
                        100L, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.of(activeSub));
        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));

        assertThatThrownBy(() -> subscriptionService.upgrade(100L, "FREE", "MOCK", false))
                .isInstanceOf(BusinessException.class);
    }
}
