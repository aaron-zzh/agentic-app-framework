package com.xuejiai.aaf.module.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.common.enums.billing.SubscriptionOperationEnum;
import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.common.enums.pay.BizOrderStatusEnum;
import com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum;
import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.domain.SubscriptionRecord;
import com.xuejiai.aaf.module.billing.domain.SubscriptionSku;
import com.xuejiai.aaf.module.billing.repository.MembershipCheckoutGuardRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRecordRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionSkuRepository;
import com.xuejiai.aaf.module.brokerage.service.BrokerageService;
import com.xuejiai.aaf.module.pay.api.PayOrderQueryApi;
import com.xuejiai.aaf.module.pay.domain.BizOrder;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.service.PayOrderService;
import com.xuejiai.aaf.module.pay.vo.BizOrderVO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;
import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** AAF-099 Plan + SKU 订阅、支付快照和补偿路径单元测试。 */
class SubscriptionServiceTest extends BaseMockitoUnitTest {

    private static final Long USER_ID = 100L;

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionRecordRepository recordRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionSkuRepository skuRepository;
    @Mock private MembershipCheckoutGuardRepository checkoutGuardRepository;
    @Mock private BizOrderService bizOrderService;
    @Mock private PayOrderService payOrderService;
    @Mock private PayOrderQueryApi payOrderQueryApi;
    @Mock private EntitlementService entitlementService;
    @Mock private CreditService creditService;
    @Mock private UserRepository userRepository;
    @Mock private BrokerageService brokerageService;

    private SubscriptionService subscriptionService;

    private SubscriptionPlan proPlan;
    private SubscriptionPlan teamPlan;
    private SubscriptionSku proSku;
    private SubscriptionSku teamSku;
    private Subscription activePro;

    @BeforeEach
    void setUp() {
        subscriptionService =
                new SubscriptionService(
                        subscriptionRepository,
                        recordRepository,
                        planRepository,
                        skuRepository,
                        checkoutGuardRepository,
                        bizOrderService,
                        payOrderService,
                        payOrderQueryApi,
                        entitlementService,
                        creditService,
                        userRepository,
                        brokerageService);
        proPlan = plan(20L, "PRO", "专业版", 20, 200L);
        teamPlan = plan(30L, "TEAM", "团队版", 30, 400L);
        proSku = sku(201L, proPlan.getId(), "PRO_MONTH", "MONTH", 1, 2900L);
        teamSku = sku(301L, teamPlan.getId(), "TEAM_MONTH", "MONTH", 1, 9900L);
        activePro = subscription(500L, proPlan.getId(), proSku.getId());

        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mock(User.class)));
        lenient()
                .when(subscriptionRepository.save(any()))
                .thenAnswer(
                        invocation -> {
                            var subscription = invocation.<Subscription>getArgument(0);
                            if (subscription.getId() == null) subscription.setId(900L);
                            return subscription;
                        });
        lenient()
                .when(recordRepository.save(any()))
                .thenAnswer(
                        invocation -> {
                            var record = invocation.<SubscriptionRecord>getArgument(0);
                            if (record.getId() == null) record.setId(600L);
                            return record;
                        });
    }

    @Test
    @DisplayName("Given 生效订阅 When 取消 Then 仅记录取消时间并保持 ACTIVE")
    void cancel_recordsIntentAndKeepsSubscriptionActive() {
        lockActive(activePro);

        var result = subscriptionService.cancel(USER_ID);

        assertThat(result.getCancelledAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatusEnum.ACTIVE.getCode());
        verify(checkoutGuardRepository).ensureGuard(USER_ID);
        verify(checkoutGuardRepository).lockGuard(USER_ID);
        verify(subscriptionRepository).save(activePro);
    }

    @Test
    @DisplayName("Given 已取消订阅 When 再次取消 Then 不重写取消事实")
    void cancel_isIdempotent() {
        var cancelledAt = LocalDateTime.now().minusDays(1);
        activePro.setCancelledAt(cancelledAt);
        lockActive(activePro);

        var result = subscriptionService.cancel(USER_ID);

        assertThat(result.getCancelledAt()).isEqualTo(cancelledAt);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given TEAM 订阅 When 申请降级到 PRO SKU Then 仅保存 pendingSkuId")
    void downgrade_storesPendingSkuIdentity() {
        var activeTeam = subscription(501L, teamPlan.getId(), teamSku.getId());
        lockActive(activeTeam);
        when(planRepository.findById(teamPlan.getId())).thenReturn(Optional.of(teamPlan));
        when(skuRepository.findByCode(proSku.getCode())).thenReturn(Optional.of(proSku));
        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));

        var result = subscriptionService.downgrade(USER_ID, proSku.getCode());

        assertThat(result.getPendingSkuId()).isEqualTo(proSku.getId());
        assertThat(result.getPlanId()).isEqualTo(teamPlan.getId());
        assertThat(result.getSkuId()).isEqualTo(teamSku.getId());
        verify(subscriptionRepository).save(activeTeam);
    }

    @Test
    @DisplayName("Given 已预约降级 When 撤销 Then 清空 pendingSkuId")
    void cancelPending_clearsPendingSku() {
        activePro.setPendingSkuId(101L);
        lockActive(activePro);

        var result = subscriptionService.cancelPending(USER_ID);

        assertThat(result.getPendingSkuId()).isNull();
        verify(subscriptionRepository).save(activePro);
    }

    @Test
    @DisplayName("Given PRO 剩余价值段 When 升级 TEAM Then 保存 SKU、世代和逐段价值快照")
    void subscribe_upgradePersistsCheckoutContext() {
        lockActive(activePro);
        when(recordRepository.findLiveByUserIdForUpdate(USER_ID)).thenReturn(List.of());
        when(skuRepository.findByCode(teamSku.getCode())).thenReturn(Optional.of(teamSku));
        when(skuRepository.findById(proSku.getId())).thenReturn(Optional.of(proSku));
        when(planRepository.findById(teamPlan.getId())).thenReturn(Optional.of(teamPlan));
        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));

        var segment = new SubscriptionRecord();
        segment.setId(601L);
        segment.setSkuPriceSnapshot(2900L);
        segment.setServiceStartAt(LocalDateTime.now().minusDays(5));
        segment.setServiceEndAt(LocalDateTime.now().plusDays(25));
        when(recordRepository.findAvailableValueSegmentsForUpdate(
                        eq(USER_ID), eq(activePro.getId()), any(LocalDateTime.class)))
                .thenReturn(List.of(segment));
        when(bizOrderService.create(eq(USER_ID), any()))
                .thenReturn(
                        new BizOrderVO(
                                701L,
                                "BIZ-701",
                                USER_ID,
                                BizOrderTypeEnum.SUBSCRIPTION.getCode(),
                                "升级",
                                7483L,
                                null,
                                BizOrderStatusEnum.PENDING.getCode(),
                                LocalDateTime.now()));
        var payOrder = payOrder(801L, 7483L, PayOrderStatusEnum.WAITING.getCode());
        when(payOrderService.create(any())).thenReturn(payOrder);
        when(payOrderQueryApi.findSnapshot(payOrder.id())).thenReturn(Optional.empty());

        var result = subscriptionService.subscribe(USER_ID, teamSku.getCode(), "MOCK");

        assertThat(result).isSameAs(payOrder);
        var captor = ArgumentCaptor.forClass(SubscriptionRecord.class);
        verify(recordRepository).save(captor.capture());
        var record = captor.getValue();
        assertThat(record.getOperation()).isEqualTo(SubscriptionOperationEnum.UPGRADE.getCode());
        assertThat(record.getPlanId()).isEqualTo(teamPlan.getId());
        assertThat(record.getSkuId()).isEqualTo(teamSku.getId());
        assertThat(record.getSkuPriceSnapshot()).isEqualTo(teamSku.getPrice());
        assertThat(record.getCheckoutGenerationId()).isEqualTo(activePro.getId());
        assertThat(record.getCheckoutCalculatedAt()).isNotNull();
        assertThat(record.getCheckoutValueSnapshot()).contains("\"recordId\":601");
        assertThat(record.getPayPrice()).isPositive().isLessThan(teamSku.getPrice());
        assertThat(record.getPayStatus()).isEqualTo("UNPAID");
        assertThat(record.getFulfillmentStatus()).isEqualTo("PENDING");
        verify(bizOrderService).bindPayOrder(701L, 801L);
    }

    @Test
    @DisplayName("Given 用户已有 live checkout When 再次下单 Then 返回既有支付单冲突且不创建新订单")
    void subscribe_rejectsConcurrentLiveCheckout() {
        lockActive(activePro);
        var pending = new SubscriptionRecord();
        pending.setPayOrderId(801L);
        when(recordRepository.findLiveByUserIdForUpdate(USER_ID)).thenReturn(List.of(pending));
        when(payOrderQueryApi.isLive(eq(801L), any(LocalDateTime.class))).thenReturn(true);

        assertThatThrownBy(() -> subscriptionService.subscribe(USER_ID, teamSku.getCode(), "MOCK"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payOrderId=801");

        verify(payOrderService, never()).create(any());
    }

    @Test
    @DisplayName("Given 支付成功时 ACTIVE 世代已变化 When 回调 Then 进入人工补偿且不履约")
    void paySuccess_marksCompensationWhenGenerationChanged() {
        var current = subscription(501L, proPlan.getId(), proSku.getId());
        lockActive(current);
        var record = pendingRecord(601L, SubscriptionOperationEnum.UPGRADE, teamPlan, teamSku, 500L, 7483L);
        var bizOrder = bizOrder(701L, 801L);
        when(bizOrderService.findByPayOrderId(801L)).thenReturn(bizOrder);
        when(recordRepository.findByPayOrderId(801L)).thenReturn(Optional.of(record));
        when(recordRepository.findByIdForUpdate(record.getId())).thenReturn(Optional.of(record));
        when(payOrderQueryApi.findSnapshot(801L))
                .thenReturn(Optional.of(successSnapshot(801L, record.getPayPrice())));
        when(planRepository.findById(teamPlan.getId())).thenReturn(Optional.of(teamPlan));
        when(skuRepository.findById(teamSku.getId())).thenReturn(Optional.of(teamSku));

        subscriptionService.onPaySuccess(801L);

        assertThat(record.getPayStatus()).isEqualTo("PAID");
        assertThat(record.getFulfillmentStatus()).isEqualTo("COMPENSATION_PENDING");
        assertThat(record.getExceptionCode()).isEqualTo("OPERATION_STATE_CHANGED");
        verify(bizOrderService).markCompensationPending(701L);
        verify(entitlementService, never()).instantiateQuotas(any(), any());
    }

    @Test
    @DisplayName("Given SKU 当前价格已变化 When 续费回调 Then 使用下单价格快照完成履约")
    void paySuccess_usesRecordSkuPriceSnapshot() {
        lockActive(activePro);
        var originalEndAt = activePro.getEndAt();
        proSku.setPrice(3900L);
        var record = pendingRecord(602L, SubscriptionOperationEnum.RENEW, proPlan, proSku, activePro.getId(), 2900L);
        record.setSkuPriceSnapshot(2900L);
        var bizOrder = bizOrder(702L, 802L);
        when(bizOrderService.findByPayOrderId(802L)).thenReturn(bizOrder);
        when(recordRepository.findByPayOrderId(802L)).thenReturn(Optional.of(record));
        when(recordRepository.findByIdForUpdate(record.getId())).thenReturn(Optional.of(record));
        when(payOrderQueryApi.findSnapshot(802L))
                .thenReturn(Optional.of(successSnapshot(802L, 2900L)));
        when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
        when(skuRepository.findById(proSku.getId())).thenReturn(Optional.of(proSku));

        subscriptionService.onPaySuccess(802L);

        assertThat(record.getFulfillmentStatus()).isEqualTo("FULFILLED");
        assertThat(record.getServiceGenerationId()).isEqualTo(activePro.getId());
        assertThat(activePro.getEndAt()).isEqualTo(originalEndAt.plusMonths(1));
        verify(bizOrderService).markPaid(702L);
        verify(bizOrderService, never()).markCompensationPending(any());
    }

    private void lockActive(Subscription subscription) {
        when(subscriptionRepository.findByUserIdAndStatusForUpdate(
                        USER_ID, SubscriptionStatusEnum.ACTIVE.getCode()))
                .thenReturn(Optional.ofNullable(subscription));
    }

    private SubscriptionPlan plan(
            Long id, String code, String name, int sort, long monthlyCredits) {
        var plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setCode(code);
        plan.setName(name);
        plan.setStatus("ENABLED");
        plan.setSort(sort);
        plan.setMonthlyCredits(monthlyCredits);
        return plan;
    }

    private SubscriptionSku sku(
            Long id,
            Long planId,
            String code,
            String billingCycle,
            int cycleMonths,
            long price) {
        var sku = new SubscriptionSku();
        sku.setId(id);
        sku.setPlanId(planId);
        sku.setCode(code);
        sku.setBillingCycle(billingCycle);
        sku.setCycleMonths(cycleMonths);
        sku.setPrice(price);
        sku.setMarketPrice(price);
        sku.setStatus("ENABLED");
        sku.setSort(10);
        return sku;
    }

    private Subscription subscription(Long id, Long planId, Long skuId) {
        var subscription = new Subscription();
        subscription.setId(id);
        subscription.setUserId(USER_ID);
        subscription.setPlanId(planId);
        subscription.setSkuId(skuId);
        subscription.setStartAt(LocalDateTime.now().minusDays(5));
        subscription.setEndAt(LocalDateTime.now().plusDays(25));
        subscription.setStatus(SubscriptionStatusEnum.ACTIVE.getCode());
        return subscription;
    }

    private SubscriptionRecord pendingRecord(
            Long id,
            SubscriptionOperationEnum operation,
            SubscriptionPlan plan,
            SubscriptionSku sku,
            Long checkoutGenerationId,
            long payPrice) {
        var record = new SubscriptionRecord();
        record.setId(id);
        record.setUserId(USER_ID);
        record.setPlanId(plan.getId());
        record.setSkuId(sku.getId());
        record.setOperation(operation.getCode());
        record.setPayOrderId(id + 200L);
        record.setPayPrice(payPrice);
        record.setSkuPriceSnapshot(payPrice);
        record.setPayStatus("UNPAID");
        record.setFulfillmentStatus("PENDING");
        record.setCheckoutGenerationId(checkoutGenerationId);
        record.setCheckoutCalculatedAt(LocalDateTime.now().minusMinutes(1));
        return record;
    }

    private BizOrder bizOrder(Long id, Long payOrderId) {
        var order = new BizOrder();
        order.setId(id);
        order.setUserId(USER_ID);
        order.setOrderType(BizOrderTypeEnum.SUBSCRIPTION.getCode());
        order.setPayOrderId(payOrderId);
        order.setStatus(BizOrderStatusEnum.PENDING.getCode());
        return order;
    }

    private PayOrderVO payOrder(Long id, long amount, int status) {
        return new PayOrderVO(
                id,
                "PAY-" + id,
                "会员订阅",
                amount,
                status,
                "MOCK",
                null,
                USER_ID,
                LocalDateTime.now().plusMinutes(15),
                null,
                0L,
                LocalDateTime.now(),
                null,
                BizOrderTypeEnum.SUBSCRIPTION.getCode());
    }

    private PayOrderQueryApi.PayOrderSnapshot successSnapshot(Long id, long amount) {
        return new PayOrderQueryApi.PayOrderSnapshot(
                id,
                amount,
                PayOrderStatusEnum.SUCCESS.getCode(),
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now());
    }
}
